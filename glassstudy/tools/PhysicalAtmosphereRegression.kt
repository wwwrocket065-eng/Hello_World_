package com.glassstudy.filament

import kotlin.math.PI
import kotlin.math.abs

fun main() {
    val vac=LightEnvironmentMode.VACUUM
    val atm=LightEnvironmentMode.ATMOSPHERE
    for(e in listOf(0.0,.25,PI/4,PI/2,3*PI/4,PI)) {
        check(PhysicalAtmosphereModel.solarTransmission(vac,e,.14)==1.0)
        check(PhysicalAtmosphereModel.skyIlluminance(vac,e,14000.0,.08)==0.0)
        val light=PhysicalAtmosphereModel.skyIlluminance(atm,e,14000.0,.08)
        check(light in 0.0..14001.0)
        val sh=PhysicalAtmosphereModel.skyRadianceSh(e,1.3,.3)
        check(sh.size==27 && sh.all { it.isFinite() })
        check(PhysicalAtmosphereModel.backgroundRgb(vac,e,.3,1.0).all { it==0f })
        check(PhysicalAtmosphereModel.backgroundRgb(atm,e,.3,0.0).all { it==0f })
    }
    check(PhysicalAtmosphereModel.skyIlluminance(atm,0.0,14000.0,.08)>0.0)
    check(PhysicalAtmosphereModel.skyIlluminance(atm,PI/2,14000.0,.08)>13000.0)
    check(PhysicalAtmosphereModel.solarTransmission(atm,0.0,.14)<.1)
    check(PhysicalAtmosphereModel.solarTransmission(atm,PI/2,.14)>.8)
    check(LightEnvironmentMode.from("vacuum")==vac)
    check(LightEnvironmentMode.from("atmosphere")==atm)
    check(abs(PhysicalAtmosphereModel.skyRadianceSh(PI/3,0.0,.3)[0]-
        PhysicalAtmosphereModel.skyRadianceSh(PI/3,PI,.3)[0])<.001f)
    println("PASS: no vacuum IBL, atmospheric sun-dependent skylight and SH")
}
