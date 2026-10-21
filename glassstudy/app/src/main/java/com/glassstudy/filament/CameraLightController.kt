package com.glassstudy.filament

import android.view.MotionEvent
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.utils.GlassStudyModelViewer
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Single state/gesture owner for camera orbit, light direction and brightness. */
class CameraLightController(
    private val viewer:GlassStudyModelViewer,
    private val surface:SurfaceView,
    private val density:()->Float,
    private val showIndicator:(LightIndicatorView.Mode,Float)->Unit,
    private val hideIndicator:()->Unit
) {
    var config=CameraModuleConfig()
        private set
    private var zoomPower=.50
    private var invertHorizontal=true
    var lightMode=false
        private set
    var yaw=.56
        private set
    var pitch=.34
        private set
    var distance=5.5
        private set
    var lightAz=1.3
        private set
    var lightElevation=.9
        private set
    var brightness=1.0
        private set
    private var oldX=0f
    private var oldY=0f
    private var oldPinch=0f
    private val lightGesture=LightGestureController()
    // Input-only tap vs gesture classification; indicator visibility is independent
    // of pointer release and changes only on a new gesture or an intentional tap.
    private val lightTouch=LightTouchSession()
    private var initialLightConfigurationApplied=false

    fun configure(next:CameraModuleConfig,zoom:Double,inverted:Boolean) {
        require(zoom.isFinite() && zoom>0)
        config=next
        if(!initialLightConfigurationApplied) {
            lightAz=next.homeLightAzRad;lightElevation=next.homeLightElevationRad
            initialLightConfigurationApplied=true
        }
        lightGesture.lockThresholdDp=next.lightAxisLockThresholdDp
        lightTouch.tapSlopDp=next.lightTapSlopDp
        zoomPower=zoom
        invertHorizontal=inverted
        distance=distance.coerceIn(config.minDistance,config.maxDistance)
        pitch=pitch.coerceIn(config.minOrbitPitchRad,config.maxOrbitPitchRad)
        applyToFilament()
    }

    fun setLightMode(enabled:Boolean) {
        if(lightMode!=enabled) {
            lightMode=enabled
            resetGesture()
        }
    }

    fun setBrightnessPercent(value:Float) {
        if(!value.isFinite())return
        brightness=(value.toDouble()/100.0).coerceIn(0.0,2.0)
        applyToFilament()
    }

    fun resetHome() {
        yaw=config.homeYawRad;pitch=config.homePitchRad;distance=config.homeDistance
        lightMode=false
        resetGesture()
        applyToFilament()
    }

    fun resetGesture() {
        lightGesture.reset()
        lightTouch.cancel()
        oldPinch=0f
        hideIndicator()
    }

    fun onTouch(e:MotionEvent):Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lightGesture.reset()
                lightTouch.begin(e.x,e.y)
                oldX=e.getX(0);oldY=e.getY(0)
                oldPinch=0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                lightTouch.onMultiplePointers()
                lightGesture.reset()
                oldX=e.getX(0);oldY=e.getY(0)
                oldPinch=if(e.pointerCount>=2)pinch(e) else 0f
                if(lightMode && e.pointerCount>=2)
                    showIndicator(LightIndicatorView.Mode.BRIGHTNESS,(brightness*100).toFloat())
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Finish the active pinch without hiding its indicator.
                // Keep one-finger input locked out until ALL pointers lift.
                lightGesture.reset()
                oldPinch=0f
                val survivor=if(e.actionIndex==0 && e.pointerCount>1)1 else 0
                oldX=e.getX(survivor);oldY=e.getY(survivor)
            }
            MotionEvent.ACTION_UP -> {
                // A fast swipe might have no MOVE event: also check the final position.
                lightTouch.onMove(e.x,e.y,density())
                val wasTap=lightTouch.finish()
                lightGesture.reset()
                oldPinch=0f
                // One stationary tap dismisses, but never cycles the indicator.
                if(lightMode && wasTap)hideIndicator()
            }
            MotionEvent.ACTION_CANCEL -> {
                // A system cancellation is not a deliberate single tap.
                lightTouch.cancel()
                lightGesture.reset()
                oldPinch=0f
            }
            MotionEvent.ACTION_MOVE -> {
                val dp=density().coerceAtLeast(.1f)
                if(e.pointerCount>=2) {
                    lightTouch.onMultiplePointers()
                    lightGesture.reset()
                    val span=pinch(e)
                    if(oldPinch>0f && span>1f) {
                        if(lightMode) {
                            // Additive adjustment works even from absolute zero.
                            val next=(brightness+(span-oldPinch)/dp/config.brightnessPinchDivisorDp).coerceIn(0.0,2.0)
                            setBrightnessPercent((next*100).toFloat())
                            showIndicator(LightIndicatorView.Mode.BRIGHTNESS,(brightness*100).toFloat())
                        } else {
                            distance=(distance*(oldPinch/span).toDouble().pow(zoomPower))
                                .coerceIn(config.minDistance,config.maxDistance)
                        }
                    }
                    oldPinch=span
                } else {
                    val dx=(e.x-oldX).toDouble()/dp
                    val dy=(e.y-oldY).toDouble()/dp
                    if(lightMode) {
                        lightTouch.onMove(e.x,e.y,dp)
                        // Pinch-to-one-finger transitions do not switch axes
                        // until a completely new ACTION_DOWN gesture starts.
                        val motion=if(!lightTouch.hadMultiplePointers)
                            lightGesture.move(dx,dy) else null
                        if(motion!=null)when(motion.axis) {
                            LightGestureController.Axis.AZIMUTH -> {
                                val turn=2.0*Math.PI
                                lightAz=(lightAz+motion.deltaDp*config.lightYawSensitivity)%turn
                                if(lightAz<0)lightAz+=turn
                                showIndicator(LightIndicatorView.Mode.AZIMUTH,
                                    Math.toDegrees(lightAz).toFloat())
                            }
                            LightGestureController.Axis.ELEVATION -> {
                                lightElevation=(lightElevation+
                                    motion.deltaDp*config.lightPitchSensitivity)
                                    .coerceIn(0.0,Math.PI)
                                showIndicator(LightIndicatorView.Mode.ELEVATION,
                                    Math.toDegrees(lightElevation).toFloat())
                            }
                        }
                    } else {
                        yaw+=dx*(if(invertHorizontal)-config.yawSensitivity else config.yawSensitivity)
                        pitch=(pitch+dy*config.pitchSensitivity)
                            .coerceIn(config.minOrbitPitchRad,config.maxOrbitPitchRad)
                    }
                }
                oldX=e.getX(0);oldY=e.getY(0)
            }
        }
        return true
    }

    private fun pinch(e:MotionEvent):Float {
        val dx=e.getX(0)-e.getX(1)
        val dy=e.getY(0)-e.getY(1)
        return sqrt(dx*dx+dy*dy)
    }

    fun applyToFilament() {
        val cy=cos(pitch)
        val x=distance*cy*sin(yaw)
        val z=distance*cy*cos(yaw)
        val y=1.0+distance*sin(pitch)
        viewer.camera.lookAt(x,y,z,0.0,1.0,0.0,0.0,1.0,0.0)
        val aspect=max(.2,surface.width.toDouble()/max(1,surface.height))
        viewer.camera.setProjection(config.fieldOfView,aspect,config.nearClip,
            config.farClip,Camera.Fov.VERTICAL)
        val rays=LightDirectionMath.incoming(lightAz,lightElevation)
        val mgr=viewer.engine.lightManager
        val instance=mgr.getInstance(viewer.light)
        // Filament's light ray direction MUST oppose the actual sun source,
        // including the return hemisphere (elevation 90..180 degrees).
        mgr.setDirection(instance,rays.x,rays.y,rays.z)
        mgr.setIntensity(instance,(config.lightIntensity*brightness).toFloat())
    }
}
