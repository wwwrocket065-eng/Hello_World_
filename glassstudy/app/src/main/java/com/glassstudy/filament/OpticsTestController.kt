package com.glassstudy.filament

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import java.io.File
import java.util.concurrent.Executors

/** UI-thread suite coordinator. GL capture belongs to the renderer; file IO to one worker. */
class OpticsTestController(private val activity:Activity,private val surface:GlassOpticsSurface) {
    private val handler=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private val directory=File(activity.filesDir,"optics-test")
    private var saved:GlassOpticsSurface.State?=null
    private var cases=emptyList<Pair<String,GlassOpticsSurface.State>>()
    private var index=0
    private var expectedRun=0L
    private var started=0L
    private var timeoutMs=600000L
    private var generation=0
    private var finishing=false
    private var closed=false
    private var initialReport=""
    private var originalContour=FloatArray(0)
    private var smoothContour=FloatArray(0)
    private val reports=mutableListOf<String>()
    private val names=mutableListOf<String>()
    var running=false
        private set
    fun report():String=File(directory,"clipboard.txt").takeIf { it.isFile }?.readText() ?: "Тест ещё не выполнен."
    fun start(config:DiagnosticsModuleConfig,diagnostics:String,polygon:FloatArray?,smooth:FloatArray?) {
        if(running || closed)return
        if(surface.visibility!=android.view.View.VISIBLE)return toast("Включите оптический рендер")
        if(polygon==null || smooth==null)return toast("Нет профиля для теста")
        originalContour=polygon.copyOf();smoothContour=smooth.copyOf()
        val original=surface.beginTest() ?: return toast("Для теста включите оптический рендер")
        saved=original;running=true;generation++;index=0;reports.clear();names.clear();initialReport=diagnostics
        timeoutMs=config.testTimeoutSeconds*1000L
        val render=original.render.copy(optics=original.render.optics.copy(width=config.testWidth,samples=config.testSamples,segmentCullingEnabled=true))
        val base=original.copy(render=render)
        cases=listOf("Гладкая поверхность, отсечение ВКЛ" to base.copy(analyticSurface=true),
            "Гладкая поверхность, отсечение ВЫКЛ" to base.copy(analyticSurface=true,
                render=render.copy(optics=render.optics.copy(segmentCullingEnabled=false))),
            "Полигональная поверхность" to base.copy(analyticSurface=false),
            "Гладкая поверхность, другой угол камеры" to base.copy(analyticSurface=true,pitch=(base.pitch+Math.toRadians(config.testPitchOffsetDegrees.toDouble())).coerceIn(-1.3,1.3)))
        // Old files stay readable until a successful new result replaces them.
        next()
    }
    private fun next() {
        if(index>=cases.size) { finish("Завершён");return }
        val (name,state)=cases[index]
        expectedRun=surface.metrics.snapshot().run+1
        started=SystemClock.uptimeMillis()
        surface.testState(state,if(state.analyticSurface)smoothContour else originalContour)
        toast("Тест ${index+1}/${cases.size}: $name")
        handler.postDelayed(poll,250)
    }
    private val poll=object:Runnable {
        override fun run() {
            if(!running || finishing)return
            if(surface.failed!=null) { finish("Отказ GPU: ${surface.failed}");return }
            if(SystemClock.uptimeMillis()-started>timeoutMs) { finish("Прерван: таймаут прогона ${index+1}");return }
            val view=surface.metrics.snapshot()
            if(view.run==expectedRun && view.complete) {
                val token=generation
                val (name,state)=cases[index]
                val details="ПРОГОН ${index+1}: $name\n$state\n"+surface.metrics.report()
                surface.captureTestImage { result ->
                    if(token!=generation || !running) { result.getOrNull()?.bitmap?.recycle();return@captureTestImage }
                    result.fold(onSuccess={ capture ->
                        val bitmap=capture.bitmap
                        val number=index+1
                        worker.execute {
                            val written=runCatching {
                                directory.mkdirs()
                                val tmp=File(directory,"pending-$number.png")
                                tmp.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
                                val target=File(directory,"run-$token-$number.png")
                                check(tmp.renameTo(target));target.name
                            }
                            bitmap.recycle()
                            handler.post {
                                if(token!=generation || !running)return@post
                                written.fold(onSuccess={ file ->
                                    reports.add(details+"\n"+capture.rays);names.add(file);index++;next()
                                },onFailure={ finish("Ошибка сохранения снимка: ${it.message}") })
                            }
                        }
                    },onFailure={ finish("Ошибка снимка: ${it.message}") })
                }
            } else handler.postDelayed(this,250)
        }
    }
    fun cancel() { if(running)finish("Прерван: приложение приостановлено") }
    private fun finish(status:String) {
        if(!running || finishing)return
        finishing=true;generation++;handler.removeCallbacks(poll)
        if(index<cases.size)initialReport+="\nНезавершённый прогон ${index+1}: ${surface.metrics.snapshot()}\n"+surface.metrics.report()
        saved?.let { surface.endTest(it) };saved=null
        val text=initialReport+"\n\n▸ АВТОТЕСТ ОПТИКИ\n$status; ${reports.size}/${cases.size} прогонов\n"+
            "Одинаковые камера/свет в первых трёх прогонах; тестовое разрешение/SPP указаны ниже.\n"+
            "Снимки после GPU fence; GPU-загрузка не выводится из CPU/FPS.\n"+
            reports.joinToString("\n\n")
        fun compact(value:String):String=if(value.length<=24000)value else
            value.take(18000)+"\n[История сокращена для буфера; полный отчёт сохранён в приложении]\n"+value.takeLast(6000)
        val clipboardText=compact(initialReport)+"\n\n▸ АВТОТЕСТ ОПТИКИ\n$status; ${reports.size}/${cases.size} прогонов\n"+
            reports.joinToString("\n\n") { item ->
                val history=item.indexOf("ИСТОРИЯ:")
                val rays=item.indexOf("ПРОБА ЛУЧЕЙ")
                compact(if(history>=0)item.take(history) else item)+
                    if(rays>=0)"\n"+item.substring(rays) else ""
            }
        val files=names.toList()
        worker.execute {
            val outcome=runCatching {
                directory.mkdirs()
                if(files.isNotEmpty())makeContactSheet(files)
                else File(directory,"image.png").delete()
                val temp=File(directory,"report.tmp");temp.writeText(text)
                check(temp.renameTo(File(directory,"report.txt")))
                File(directory,"clipboard.txt").writeText(clipboardText)
                directory.listFiles()?.filter { it.name.startsWith("run-") && it.name !in files }?.forEach { it.delete() }
            }
            handler.post {
                running=false;finishing=false
                if(closed)return@post
                outcome.fold(onSuccess={
                    val clipboard=activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("GlassStudy: диагностика и тест",clipboardText)) }
                        .fold(onSuccess={ toast("$status. Диагностика и тест скопированы") },
                            onFailure={ toast("Отчёт сохранён. Повторите копирование из диагностики") })
                },onFailure={ toast("Не удалось сохранить тест: ${it.message}") })
            }
        }
    }
    private fun makeContactSheet(files:List<String>) {
        val first=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeFile(File(directory,files.first()).path,first)
        val w=first.outWidth;val h=first.outHeight
        check(w>0 && h>0)
        val label=48
        val image=Bitmap.createBitmap(w*2,(h+label)*((files.size+1)/2),Bitmap.Config.ARGB_8888)
        try {
            val canvas=Canvas(image);canvas.drawColor(Color.rgb(10,25,35))
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=24f }
            files.forEachIndexed { i,file ->
                val bitmap=BitmapFactory.decodeFile(File(directory,file).path) ?: error("Нет снимка $file")
                val x=(i%2)*w;val y=(i/2)*(h+label)
                canvas.drawText("Прогон ${i+1}",x+12f,y+32f,paint)
                canvas.drawBitmap(bitmap,x.toFloat(),(y+label).toFloat(),null);bitmap.recycle()
            }
            val tmp=File(directory,"image.tmp")
            tmp.outputStream().use { check(image.compress(Bitmap.CompressFormat.PNG,100,it)) }
            check(tmp.renameTo(File(directory,"image.png")))
        } finally { image.recycle() }
    }
    fun copyImage() {
        if(!File(directory,"image.png").isFile) { toast("Сначала завершите тест со снимком");return }
        val uri=android.net.Uri.parse("content://${activity.packageName}.optics-test/image.png")
        val clipboard=activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newUri(activity.contentResolver,"Рендеры теста GlassStudy",uri))
        toast("Картинка скопирована. Вставка зависит от клавиатуры и приложения")
    }
    fun close() { cancel();closed=true;worker.shutdown() }
    private fun toast(text:String) { Toast.makeText(activity,text,Toast.LENGTH_LONG).show() }
}
