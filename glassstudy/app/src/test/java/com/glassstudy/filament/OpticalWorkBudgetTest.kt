package com.glassstudy.filament

import org.junit.Assert.*
import org.junit.Test

class OpticalWorkBudgetTest {
    @Test fun limitsClampAndLowerSettingsReduceWork() {
        val b=OpticalWorkBudget()
        b.percent=100;val full=b.units(32768)
        b.percent=10
        assertTrue(b.units(32768)<full)
        assertEquals(90L,b.pauseMs(10))
        b.percent=0;assertEquals(10,b.percent)
        b.percent=200;assertEquals(100,b.percent)
        assertEquals(0L,b.pauseMs(100))
    }
    @Test fun stallsShrinkBatchesAndWorkNeverStopsCompletely() {
        val b=OpticalWorkBudget();val before=b.units(32768)
        b.observeCompletion(240)
        assertTrue(b.units(32768)<before)
        repeat(20) { b.observeCompletion(10000) }
        assertEquals(1,b.units(32768))
        assertTrue(b.pauseMs(Long.MAX_VALUE)<=2000)
    }
    @Test fun slowRowsCannotCollapsePhotonWorkOrSubdivideRows() {
        val mask=OpticalWorkBudget()
        val photons=OpticalWorkBudget()
        repeat(40) {
            assertTrue(mask.units(16384,1280)>=1280)
            // Reporting the nominal budget must not forget the raster row floor.
            mask.units(32768)
            mask.observeCompletion(200)
        }
        assertEquals(1280,mask.units(16384,1280))
        assertEquals(8192,photons.units(16384))
        // A one-row batch can recover after transient stalls instead of requiring
        // thousands of multiplicative increases from a fictitious one-pixel size.
        repeat(20) { mask.observeCompletion(1) }
        assertTrue(mask.units(16384,1280)>1280)
    }
    @Test fun phoneSchedulingFloorDoesNotTrapOnePhotonBatches() {
        val b=OpticalWorkBudget();b.percent=90
        repeat(30) { b.units(16384);b.observeCompletion(10000) }
        assertEquals(1,b.units(16384))
        repeat(60) { b.units(16384);b.observeCompletion(14) }
        assertTrue("Normal phone scheduling must recover throughput",b.units(16384)>=8192)
    }
    @Test fun oneDriverStallDoesNotCollapseThroughput() {
        val b=OpticalWorkBudget();val initial=b.units(16384)
        b.observeCompletion(10000)
        assertTrue(b.units(16384)>=initial/2)
    }
}
