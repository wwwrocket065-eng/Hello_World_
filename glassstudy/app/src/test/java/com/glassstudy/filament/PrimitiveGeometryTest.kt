package com.glassstudy.filament

import org.junit.Assert.*
import org.junit.Test

class PrimitiveGeometryTest {
    @Test fun everyShapeIsClosedAndOutwardAtEveryQualityAndAdaptivity() {
        for(id in PrimitiveGeometry.ids)for(q in listOf("low","medium","high","ultra"))for(a in listOf(0,50,100)) {
            val mesh=PrimitiveGeometry.mesh(id,q,a,.8f,1.6f)
            val counts=mutableMapOf<Pair<Int,Int>,Int>();val directions=mutableMapOf<Pair<Int,Int>,Int>()
            var volume=0.0
            for(i in mesh.indices.indices step 3) {
                val t=mesh.indices.slice(i..i+2)
                assertEquals(3,t.toSet().size)
                val p=t.map { v -> mesh.positions.slice(v*3..v*3+2).map { it.toDouble() } }
                val u=DoubleArray(3){p[1][it]-p[0][it]};val v=DoubleArray(3){p[2][it]-p[0][it]}
                val n=doubleArrayOf(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0])
                assertTrue("$id/$q/$a degenerate",n.sumOf { it*it }>1e-18)
                volume+=(0..2).sumOf { p[0][it]*n[it] }/6
                for(j in 0..2){val x=t[j];val y=t[(j+1)%3];val key=minOf(x,y) to maxOf(x,y)
                    counts[key]=(counts[key]?:0)+1;directions[key]=(directions[key]?:0)+if(x<y)1 else -1
                }
            }
            assertTrue("$id/$q/$a open edge",counts.values.all { it==2 })
            assertTrue("$id/$q/$a winding",directions.values.all { it==0 })
            assertTrue("$id/$q/$a inward",volume>0)
        }
    }
}
