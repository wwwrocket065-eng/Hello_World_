package com.glassstudy.filament

import java.util.Locale

/** Bounded, thread-safe recorder. Wall submission time is NEVER called GPU time. */
class OpticalRenderMetrics(private val now:()->Long = System::nanoTime) {
    enum class Stage(val title:String) {
        PROFILE("Профиль"), BUFFERS("Буферы"), MASK("Силуэт"), PHOTONS("Каустика"),
        BASE("Пол/фон"), PATH("Лучи бокала"), DISPLAY("Вывод/FXAA")
    }
    data class Cost(val calls:Long=0,val wallNs:Long=0,val cpuNs:Long=0,val peakWallNs:Long=0) {
        fun plus(wall:Long,cpu:Long)=Cost(calls+1,wallNs+wall,cpuNs+cpu,maxOf(peakWallNs,wall))
    }
    data class View(
        val run:Long=0,val elapsedNs:Long=0,val complete:Boolean=false,val phase:String="ожидание",
        val width:Int=0,val height:Int=0,val spp:Int=0,val targetSpp:Int=0,
        val qualityStartNs:Long=0,val firstSampleNs:Long=0,
        val parameters:String="",val gpu:String="не определён",val bufferBytes:Long=0,
        val costs:Map<Stage,Cost> = emptyMap(),val gpuCosts:Map<Stage,Cost> = emptyMap()
    ) {
        fun pill():String = "$width"+"px · $spp spp · "+
            String.format(Locale("ru","RU"),"%.1f с",elapsedNs/1e9)+if(complete) " ✓" else " …"
    }
    data class Window(val atNs:Long,val run:Long,val phase:String,val width:Int,val spp:Int,
        val cpuPercent:Double?,val frameAvgMs:Double?,val frameP95Ms:Double?,
        val costs:Map<Stage,Cost>,val gpuSamples:List<GpuSample>,val events:String,
        val javaMb:Double?,val nativeMb:Double?,val thermalStatus:Int?)
    data class GpuSample(val stage:Stage,val run:Long,val issuedNs:Long,val durationNs:Long,
        val calls:Long=1,val peakNs:Long=durationNs)
    private var started=0L
    private var ended=0L
    private var run=0L
    private var qualityStarted=0L
    private var firstSample=0L
    private var view=View()
    private val runCosts=linkedMapOf<Stage,Cost>()
    private val windowCosts=linkedMapOf<Stage,Cost>()
    private val runGpuCosts=linkedMapOf<Stage,Cost>()
    private val windowGpuSamples=linkedMapOf<Triple<Long,Stage,Long>,GpuSample>()
    private var timerStatus="не инициализирован"
    private var skippedGpu=0L
    private val history=java.util.ArrayDeque<Window>()
    private val events=linkedSetOf<String>()
    private var lastPoll=0L
    private var lastCpuMs=0L
    private var intervalNs=1_000_000_000L
    private var retentionNs=180_000_000_000L

    @Synchronized fun configure(intervalMs:Int,historySeconds:Int) {
        require(intervalMs in 250..5000 && historySeconds in 10..600)
        intervalNs=intervalMs.toLong()*1_000_000L
        retentionNs=historySeconds.toLong()*1_000_000_000L
    }
    @Synchronized fun begin(reason:String,requestedAtNs:Long=now()) {
        started=requestedAtNs.coerceAtMost(now());ended=0L;run++;qualityStarted=0L;firstSample=0L;runCosts.clear();runGpuCosts.clear()
        events.add(reason)
        view=view.copy(run=run,elapsedNs=0,complete=false,spp=0,phase=reason,
            qualityStartNs=0,firstSampleNs=0,costs=emptyMap(),gpuCosts=emptyMap())
    }
    @Synchronized fun stageStarted(stage:Stage) {
        if(ended>0L)return
        view=view.copy(phase=stage.title,elapsedNs=if(started>0)(now()-started).coerceAtLeast(0) else 0L)
    }
    @Synchronized fun event(reason:String) { events.add(reason) }
    @Synchronized fun record(stage:Stage,wallNs:Long,cpuNs:Long) {
        val wall=wallNs.coerceAtLeast(0);val cpu=cpuNs.coerceAtLeast(0)
        runCosts[stage]=(runCosts[stage] ?: Cost()).plus(wall,cpu)
        windowCosts[stage]=(windowCosts[stage] ?: Cost()).plus(wall,cpu)
    }
    @Synchronized fun progress(phase:String,width:Int,height:Int,spp:Int,target:Int,
        finalQuality:Boolean,parameters:String,gpu:String,bufferBytes:Long) {
        val time=now()
        if(finalQuality && qualityStarted==0L)qualityStarted=time
        if(finalQuality && spp>0 && firstSample==0L)firstSample=time
        view=View(run,if(started==0L)0L else ((if(ended>0)ended else time)-started).coerceAtLeast(0),
            ended>0,if(ended>0)"готово" else phase,width,height,spp,target,
            if(qualityStarted>0)(qualityStarted-started).coerceAtLeast(0) else 0L,
            if(firstSample>0)(firstSample-started).coerceAtLeast(0) else 0L,
            parameters,gpu,bufferBytes,runCosts.toMap(),runGpuCosts.toMap())
    }
    @Synchronized fun qualityStarted() { if(qualityStarted==0L)qualityStarted=now() }
    @Synchronized fun refreshElapsed() {
        if(started>0L && ended==0L)view=view.copy(elapsedNs=(now()-started).coerceAtLeast(0))
    }
    @Synchronized fun complete() {
        if(started==0L || ended>0L)return
        ended=now();events.add("GPU завершил рендер")
        view=view.copy(elapsedNs=(ended-started).coerceAtLeast(0),complete=true,phase="готово",costs=runCosts.toMap())
    }
    @Synchronized fun gpuStatus(status:String) { timerStatus=status }
    @Synchronized fun gpuSkipped(count:Long) { skippedGpu=count }
    @Synchronized fun recordGpu(stage:Stage,sampleRun:Long,issuedNs:Long,durationNs:Long) {
        val sample=GpuSample(stage,sampleRun,issuedNs,durationNs)
        val key=Triple(sampleRun,stage,issuedNs/intervalNs)
        val previous=windowGpuSamples[key]
        if(previous!=null)windowGpuSamples[key]=previous.copy(durationNs=previous.durationNs+durationNs,
            calls=previous.calls+1,peakNs=maxOf(previous.peakNs,durationNs))
        else if(windowGpuSamples.size<64)windowGpuSamples[key]=sample
        else events.add("GPU history: часть подробных групп пропущена")
        if(sampleRun==run) {
            runGpuCosts[stage]=(runGpuCosts[stage] ?: Cost()).plus(durationNs,0)
            view=view.copy(gpuCosts=runGpuCosts.toMap())
        }
    }
    @Synchronized fun snapshot():View {
        refreshElapsed()
        return view
    }
    /** Called by the already-running UI frame callback, not by an extra rendering loop. */
    @Synchronized fun isSampleDue():Boolean = lastPoll==0L || now()-lastPoll>=intervalNs
    @Synchronized fun poll(processCpuMs:Long,frameAvgMs:Double?,frameP95Ms:Double?,active:Boolean,
        javaMb:Double?=null,nativeMb:Double?=null,thermalStatus:Int?=null) {
        val time=now()
        if(lastPoll==0L) { lastPoll=time;lastCpuMs=processCpuMs;return }
        if(time-lastPoll<intervalNs)return
        val cpu=if(processCpuMs>=lastCpuMs)(processCpuMs-lastCpuMs)*100_000_000.0/(time-lastPoll) else null
        history.addLast(Window(time,view.run,if(active)view.phase else "Filament fallback",
            view.width,view.spp,cpu,frameAvgMs,frameP95Ms,windowCosts.toMap(),windowGpuSamples.values.toList(),events.joinToString(", "),javaMb,nativeMb,thermalStatus))
        windowCosts.clear();windowGpuSamples.clear();events.clear();lastPoll=time;lastCpuMs=processCpuMs
        while(history.isNotEmpty() && (time-history.first.atNs>retentionNs || history.size>600))history.removeFirst()
    }
    @Synchronized fun pause() {
        event("пауза приложения");lastPoll=0L;windowCosts.clear();windowGpuSamples.clear()
        started=0L;ended=0L
        view=view.copy(phase="пауза",complete=false)
    }
    @Synchronized fun windows():List<Window> = history.toList()

    private data class ReportData(val view:View,val rows:List<Window>,val interval:Long,
        val retention:Long,val status:String,val skipped:Long,val time:Long)
    fun report():String {
        val data=synchronized(this) { ReportData(snapshot(),history.toList(),intervalNs,retentionNs,
            timerStatus,skippedGpu,now()) }
        // Formatting a long copied report must not hold the GL recorder lock.
        val v=data.view;val rows=data.rows
        val intervalNs=data.interval;val retentionNs=data.retention
        val timerStatus=data.status;val skippedGpu=data.skipped
        fun ms(ns:Long)=String.format(Locale("ru","RU"),"%.2f",ns/1e6)
        fun num(value:Double?)=value?.let { String.format(Locale("ru","RU"),"%.1f",it) } ?: "—"
        return buildString {
            appendLine("▸ GPU-ОПТИКА · ДИНАМИКА")
            appendLine("Рендер #${v.run}: ${v.phase}; ${v.width}×${v.height}; ${v.spp}/${v.targetSpp} spp")
            appendLine("От последнего изменения: ${ms(v.elapsedNs)} мс${if(v.complete) " (завершение GPU подтверждено fence)" else " (в процессе)"}")
            appendLine("Отчёт — снимок на момент открытия; история продолжает собираться независимо от окна.")
            appendLine("Отсчёт включает очередь GL после изменения, стабилизацию, preview, каустику и накопление; пауза прерывает замер.")
            appendLine("Начало финального качества: ${if(v.qualityStartNs>0)ms(v.qualityStartNs) else "—"} мс; первый полный spp: ${if(v.firstSampleNs>0)ms(v.firstSampleNs) else "—"} мс от изменения (подача команд)")
            appendLine(v.parameters)
            appendLine("GPU: ${v.gpu}")
            appendLine("Текстуры оптики: ~${num(v.bufferBytes/1048576.0)} МиБ (расчёт полезных данных, не VRAM драйвера)")
            appendLine("GPU-таймер: $timerStatus; пропущено из-за заполненной очереди: $skippedGpu")
            appendLine("Загрузка GPU %: недоступна. Ни CPU-время, ни длительность GPU-этапа не являются процентом загрузки.")
            appendLine("GPU-ЭТАПЫ текущего рендера: полученные замеры · сумма / среднее / пик, мс")
            for((stage,cost) in v.gpuCosts)appendLine("${stage.title}: ${cost.calls} · ${ms(cost.wallNs)} / ${ms(cost.wallNs/cost.calls)} / ${ms(cost.peakWallNs)}")
            appendLine("Таймер измеряет диапазон команд текущего ES3-контекста, не скрытый Filament. На мобильном tile GPU результаты зависят от драйвера; измерения могут влиять на скорость.")
            appendLine("ЭТАПЫ текущего рендера: вызовы · подача wall / CPU потока / пик wall, мс")
            for((stage,cost) in v.costs)appendLine("${stage.title}: ${cost.calls} · ${ms(cost.wallNs)} / ${ms(cost.cpuNs)} / ${ms(cost.peakWallNs)}")
            appendLine("Wall включает ожидания драйвера; CPU потока — реально затраченное CPU-время. Это не длительность выполнения шейдера.")
            appendLine("ИСТОРИЯ: ${rows.size} интервалов; период ≥${intervalNs/1_000_000} мс; хранение до ${retentionNs/1_000_000_000} с / 600 интервалов")
            val peakCpu=rows.mapNotNull { it.cpuPercent }.maxOrNull()
            val peakStage=rows.flatMap { r -> r.costs.map { Triple(r,it.key,it.value) } }.maxByOrNull { it.third.peakWallNs }
            appendLine("Пик CPU процесса: ${num(peakCpu)}% одного ядра (все потоки, может быть >100%)")
            if(peakStage!=null)appendLine("Самый долгий вызов подачи: ${peakStage.second.title}, ${ms(peakStage.third.peakWallNs)} мс; #${peakStage.first.run}, ${peakStage.first.width}px, ${peakStage.first.spp} spp")
            appendLine("сек назад | # | этап | px/spp | CPU% | UI avg/P95 мс | Java/native МиБ · thermal | подача этапов wall/CPU/пик мс")
            val peakGpu=rows.flatMap { it.gpuSamples }.maxByOrNull { it.peakNs }
            if(peakGpu!=null)appendLine("Самый долгий полученный GPU-этап: ${peakGpu.stage.title}, ${ms(peakGpu.peakNs)} мс; рендер #${peakGpu.run}")
            val time=data.time
            for(r in rows) {
                append("${num((time-r.atNs)/1e9)} | ${r.run} | ${r.phase} | ${r.width}/${r.spp} | ${num(r.cpuPercent)} | ${num(r.frameAvgMs)}/${num(r.frameP95Ms)} | ${num(r.javaMb)}/${num(r.nativeMb)} · ${r.thermalStatus ?: "—"} | ")
                append(r.costs.entries.joinToString("; ") { (stage,cost) -> "${stage.title}=${ms(cost.wallNs)}/${ms(cost.cpuNs)}/${ms(cost.peakWallNs)}(${cost.calls})" })
                if(r.events.isNotEmpty())append(" · ${r.events}")
                appendLine()
                // Attribute delayed GPU results to the ORIGINAL render and
                // submission time, never to a newer camera/light state.
                for(sample in r.gpuSamples) {
                    appendLine("  GPU #${sample.run} ${sample.stage.title}, подано ${num((time-sample.issuedNs)/1e9)} с назад: n=${sample.calls}, сумма=${ms(sample.durationNs)}, пик=${ms(sample.peakNs)} мс")
                }
            }
            appendLine("GPU-записи показаны в интервале получения; исходный рендер и время подачи указаны отдельно. Сбои/смена частоты disjoint отбрасываются, очередь ограничена.")
            appendLine("Thermal — системная степень перегрева Android 0..6 (0=нет, 6=выключение), не температура датчика; Java/native — heap, не вся RAM процесса.")
            appendLine("История собирается при закрытой диагностике. UI avg/P95 — последние 180 интервалов Choreographer, не скорость трассировки. Незавершённый GL-вызов попадёт в историю после возврата.")
        }
    }
}
