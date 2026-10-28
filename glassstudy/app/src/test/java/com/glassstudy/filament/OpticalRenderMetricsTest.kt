package com.glassstudy.filament

import org.junit.Assert.*
import org.junit.Test

class OpticalRenderMetricsTest {
    @Test fun includesGlQueueDelayFromUserChange() {
        var time=1_000_000_000L
        val request=time
        time+=2_000_000_000
        val m=OpticalRenderMetrics { time }
        m.begin("камера",request)
        time+=3_000_000_000
        m.complete()
        assertEquals(5_000_000_000L,m.snapshot().elapsedNs)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectsExcessiveSampling() {
        OpticalRenderMetrics().configure(1,180)
    }
    @Test fun liveClockAdvancesWithoutAnotherDraw() {
        var time=1_000_000_000L
        val m=OpticalRenderMetrics { time }
        m.begin("лучи")
        time+=5_000_000_000
        assertEquals(5_000_000_000L,m.snapshot().elapsedNs)
        m.pause()
        time+=50_000_000_000
        assertEquals(5_000_000_000L,m.snapshot().elapsedNs)
    }
    @Test fun completionFreezesAndNewInputRestarts() {
        var time=1_000_000_000L
        val m=OpticalRenderMetrics { time }
        m.begin("камера")
        time+=350_000_000
        m.progress("лучи",1280,2467,1,128,true,"params","gpu",100)
        assertFalse(m.snapshot().complete)
        assertEquals(350_000_000L,m.snapshot().elapsedNs)
        time+=2_000_000_000
        m.refreshElapsed()
        assertEquals(2_350_000_000L,m.snapshot().elapsedNs)
        m.complete()
        time+=10_000_000_000
        m.refreshElapsed()
        assertTrue(m.snapshot().complete)
        assertEquals(2_350_000_000L,m.snapshot().elapsedNs)
        assertTrue(m.snapshot().pill().endsWith("✓"))
        m.begin("свет")
        assertFalse(m.snapshot().complete)
        assertEquals(0L,m.snapshot().elapsedNs)
        assertEquals(2L,m.snapshot().run)
    }
    @Test fun windowsKeepDynamicPeaksAndMulticoreCpu() {
        var time=1_000_000_000L
        val m=OpticalRenderMetrics { time }
        m.begin("начало");m.poll(100,null,null,true)
        m.record(OpticalRenderMetrics.Stage.PHOTONS,800_000_000,10_000_000)
        m.record(OpticalRenderMetrics.Stage.PATH,5_000_000,2_000_000)
        time+=1_000_000_000;m.poll(1600,16.7,32.0,true)
        m.record(OpticalRenderMetrics.Stage.PATH,2_000_000,1_000_000)
        time+=1_000_000_000;m.poll(1700,16.7,17.0,true)
        val rows=m.windows()
        assertEquals(150.0,rows[0].cpuPercent!!,0.001)
        assertEquals(10.0,rows[1].cpuPercent!!,0.001)
        assertEquals(800_000_000L,rows[0].costs[OpticalRenderMetrics.Stage.PHOTONS]!!.peakWallNs)
        assertNull(rows[1].costs[OpticalRenderMetrics.Stage.PHOTONS])
        assertTrue(m.report().contains("Каустика, 800,00 мс"))
    }
    @Test fun delayedGpuResultsKeepTheirOriginalRunAnd64Bits() {
        var time=1_000_000_000L
        val m=OpticalRenderMetrics { time }
        m.begin("первый");m.poll(0,null,null,true)
        val old=m.snapshot().run
        m.begin("второй")
        m.recordGpu(OpticalRenderMetrics.Stage.PHOTONS,old,time,5_000_000_000L)
        m.recordGpu(OpticalRenderMetrics.Stage.PATH,m.snapshot().run,time,2_000_000)
        m.recordGpu(OpticalRenderMetrics.Stage.PATH,m.snapshot().run,time,3_000_000)
        time+=1_000_000_000;m.poll(50,null,null,true)
        assertNull(m.snapshot().gpuCosts[OpticalRenderMetrics.Stage.PHOTONS])
        assertEquals(2L,m.snapshot().gpuCosts[OpticalRenderMetrics.Stage.PATH]!!.calls)
        val samples=m.windows().single().gpuSamples
        assertEquals(5_000_000_000L,samples.first().durationNs)
        assertEquals(old,samples.first().run)
        assertEquals(2L,samples.last().calls)
        assertEquals(3_000_000L,samples.last().peakNs)
    }
    @Test fun retentionAndPauseDoNotInventLongCpuSpikes() {
        var time=1_000_000_000L
        val m=OpticalRenderMetrics { time }
        m.configure(250,10);m.begin("камера");m.poll(0,null,null,true)
        for(i in 1..100) { time+=250_000_000;m.poll(i.toLong(),null,null,true) }
        assertTrue(m.windows().size<=41)
        val n=m.windows().size
        m.pause();time+=100_000_000_000;m.poll(5000,null,null,true)
        assertEquals(n,m.windows().size)
        time+=1_000_000_000;m.poll(5100,null,null,true)
        assertEquals(10.0,m.windows().last().cpuPercent!!,.001)
    }
}
