package com.glassstudy.filament

import android.content.Context
import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/**
 * Independent ES3 dielectric renderer. All GL ownership stays on its GL thread.
 * Stochastic camera paths converge while stationary; photon flux lights the
 * actual floor material, rather than covering the checker with an overlay.
 * Unsupported drivers fail back to the still-running Filament surface.
 */
class GlassOpticsSurface(context:Context,private val onFailure:(String)->Unit):GLSurfaceView(context) {
    data class State(
        val yaw:Double,val pitch:Double,val distance:Double,val fov:Double,
        val azimuth:Double,val elevation:Double,val brightness:Double,
        val mode:LightEnvironmentMode,val floor:String,val scene:SceneModuleConfig,
        val render:RenderModuleConfig,val camera:CameraModuleConfig,val ior:Float,val antialias:Boolean
    )
    @Volatile var failed:String?=null
        private set
    @Volatile var sampleCount=0
        private set
    @Volatile var photonReady=false
        private set
    @Volatile var traceWidth=0
        private set
    @Volatile var traceHeight=0
        private set
    private val renderer=OpticalRenderer()
    private var lastState:State?=null
    private var lastContour:FloatArray?=null
    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8,8,8,8,0,0)
        setZOrderMediaOverlay(true)
        preserveEGLContextOnPause=true
        setRenderer(renderer)
        renderMode=RENDERMODE_WHEN_DIRTY
    }
    fun update(state:State) {
        if(state==lastState || failed!=null)return
        lastState=state
        queueEvent { renderer.accept(state) }
        requestRender()
    }
    fun setContour(points:FloatArray) {
        if(lastContour?.contentEquals(points)==true)return
        lastContour=points.copyOf()
        queueEvent { renderer.contour=points.copyOf();renderer.profileDirty=true;renderer.invalidatePhotons() }
        requestRender()
    }
    fun diagnostics():String = failed?.let { "GPU-оптика: отказ ($it); Filament fallback" }
        ?: "GPU-оптика ES3: ${traceWidth}×${traceHeight}, $sampleCount spp; " +
            "каустика: ${if(photonReady)"карта потока" else "ожидание света"}; шум/предел путей: эксперимент"

    private inner class OpticalRenderer:Renderer {
        var contour= floatArrayOf(0f,0f,.01f,.01f,.02f,0f)
        var profileDirty=true
        private var state:State?=null
        private var oldLight:List<Any>?=null
        private var pathProgram=0
        private var photonProgram=0
        private var displayProgram=0
        private var profile=0
        private var width=0
        private var height=0
        private var bufferWidth=0
        private var bufferHeight=0
        private var accum=IntArray(2)
        private var fbos=IntArray(2)
        private var photons=0
        private var photonFbo=0
        private var mapSize=0
        private var dirtyPhotons=true
        private var changeTime=0L
        private var motionTime=0L
        private var rowCursor=0
        private var shownIndex=0
        private var baseDirty=true
        private var havePreview=false
        private var initialized=false
        private var delayed=false

        fun invalidatePhotons() {
            dirtyPhotons=true;photonReady=false;sampleCount=0
            changeTime=SystemClock.uptimeMillis();motionTime=changeTime;rowCursor=0;baseDirty=true
        }
        fun accept(next:State) {
            val light=listOf(next.azimuth,next.elevation,next.brightness,next.mode,next.ior,
                next.scene,next.render,next.camera.lightIntensity,next.antialias)
            if(light!=oldLight) { oldLight=light;invalidatePhotons() }
            state=next;sampleCount=0;rowCursor=0;baseDirty=true;motionTime=SystemClock.uptimeMillis()
        }
        private fun source(name:String):String=context.assets.open("optics/$name")
            .bufferedReader().use { it.readText() }.replace("/*OPTICS_KERNEL*/",
                context.assets.open("optics/kernel.glsl").bufferedReader().use { it.readText() })
        private fun shader(type:Int,text:String):Int {
            val id=GL.glCreateShader(type)
            GL.glShaderSource(id,text);GL.glCompileShader(id)
            val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0) { GL.glGetShaderInfoLog(id) }
            return id
        }
        private fun program(vertex:String,fragment:String):Int {
            val v=shader(GL.GL_VERTEX_SHADER,source(vertex))
            val f=shader(GL.GL_FRAGMENT_SHADER,source(fragment))
            val p=GL.glCreateProgram();GL.glAttachShader(p,v);GL.glAttachShader(p,f);GL.glLinkProgram(p)
            GL.glDeleteShader(v);GL.glDeleteShader(f)
            val ok=IntArray(1);GL.glGetProgramiv(p,GL.GL_LINK_STATUS,ok,0)
            check(ok[0]!=0) { GL.glGetProgramInfoLog(p) };return p
        }
        private fun fail(error:Exception) {
            if(failed!=null)return
            failed=error.javaClass.simpleName+": "+(error.message ?: "GL error").take(180)
            Log.e("GlassStudyOptics","GPU optical pass disabled",error)
            post { onFailure(failed!!) }
        }
        override fun onSurfaceCreated(gl:GL10?,config:EGLConfig?) {
            try {
                // Probe actual RGBA16F attachments and GL drawing errors below.
                // EXT_float_blend is required for 32F, not half-float blending.
                pathProgram=program("quad.vert","path.frag")
                photonProgram=program("photon.vert","photon.frag")
                displayProgram=program("quad.vert","display.frag")
                val ids=IntArray(1);GL.glGenTextures(1,ids,0);profile=ids[0]
                GL.glGenTextures(2,accum,0);GL.glGenFramebuffers(2,fbos,0)
                GL.glGenTextures(1,ids,0);photons=ids[0]
                GL.glGenFramebuffers(1,ids,0);photonFbo=ids[0]
                val vao=IntArray(1);GL.glGenVertexArrays(1,vao,0);GL.glBindVertexArray(vao[0])
                initialized=true;bufferWidth=0;mapSize=0;profileDirty=true;havePreview=false;baseDirty=true
                invalidatePhotons()
            } catch(e:Exception) { fail(e) }
        }
        override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int) {
            width=w;height=h;sampleCount=0;rowCursor=0;baseDirty=true;motionTime=SystemClock.uptimeMillis()
        }
        private fun f(p:Int,name:String,value:Float)=GL.glUniform1f(GL.glGetUniformLocation(p,name),value)
        private fun i(p:Int,name:String,value:Int)=GL.glUniform1i(GL.glGetUniformLocation(p,name),value)
        private fun v(p:Int,name:String,x:Float,y:Float,z:Float)=GL.glUniform3f(GL.glGetUniformLocation(p,name),x,y,z)
        private fun texture(id:Int,unit:Int,p:Int,name:String) {
            GL.glActiveTexture(GL.GL_TEXTURE0+unit);GL.glBindTexture(GL.GL_TEXTURE_2D,id);i(p,name,unit)
        }
        private fun allocate(texture:Int,fbo:Int,w:Int,h:Int) {
            GL.glBindTexture(GL.GL_TEXTURE_2D,texture)
            GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA16F,w,h,0,GL.GL_RGBA,GL.GL_HALF_FLOAT,null)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbo)
            GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,texture,0)
            check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE) { "RGBA16F FBO incomplete" }
            GL.glClearColor(0f,0f,0f,0f);GL.glClear(GL.GL_COLOR_BUFFER_BIT)
        }
        private fun receiverExtent(s:State):Float=max(s.render.optics.photonExtent,
            s.scene.floorWidth*.5f+s.render.optics.photonMargin)
        private fun common(p:Int,s:State) {
            texture(profile,0,p,"uProfile");i(p,"uProfileCount",contour.size/2)
            var radius=0f;var low=Float.POSITIVE_INFINITY;var high=Float.NEGATIVE_INFINITY
            for(j in contour.indices step 2) {
                low=min(low,contour[j]);high=max(high,contour[j]);radius=max(radius,contour[j+1])
            }
            v(p,"uBoundsMin",-radius-.001f,low-.001f,-radius-.001f)
            v(p,"uBoundsMax",radius+.001f,high+.001f,radius+.001f)
            f(p,"uIor",s.ior);i(p,"uBounces",s.render.optics.bounces)
            val sun=LightDirectionMath.source(s.azimuth,s.elevation)
            v(p,"uSun",sun.x,sun.y,sun.z)
            f(p,"uPhotonExtent",receiverExtent(s))
            i(p,"uSmoothSurface",if(s.antialias)1 else 0)
        }
        override fun onDrawFrame(gl:GL10?) {
            val s=state ?: return
            if(!initialized || failed!=null || width<1 || height<1)return
            try {
                val c=s.render.optics
                if(profileDirty) {
                    GL.glBindTexture(GL.GL_TEXTURE_2D,profile)
                    val data=ByteBuffer.allocateDirect(contour.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                    data.put(contour).position(0)
                    GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RG32F,contour.size/2,1,0,GL.GL_RG,GL.GL_FLOAT,data)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST)
                    profileDirty=false;sampleCount=0;rowCursor=0;baseDirty=true
                }
                val moving=SystemClock.uptimeMillis()-motionTime<c.settleMs
                val w=min(if(moving)c.movingWidth else c.width,width)
                val h=(w*height.toFloat()/width).roundToInt().coerceAtLeast(1)
                if(bufferWidth!=w || bufferHeight!=h) {
                    val oldTextures=accum;val oldFbos=fbos
                    val oldWidth=bufferWidth;val oldHeight=bufferHeight
                    accum=IntArray(2);fbos=IntArray(2)
                    GL.glGenTextures(2,accum,0);GL.glGenFramebuffers(2,fbos,0)
                    for(j in 0..1) {
                        allocate(accum[j],fbos[j],w,h)
                        if(oldWidth>0 && oldHeight>0) {
                            // Seed the high-resolution buffers with the live preview;
                            // unfinished tiles must not turn into black stripes.
                            GL.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER,oldFbos[shownIndex])
                            GL.glBindFramebuffer(GL.GL_DRAW_FRAMEBUFFER,fbos[j])
                            GL.glBlitFramebuffer(0,0,oldWidth,oldHeight,0,0,w,h,
                                GL.GL_COLOR_BUFFER_BIT,GL.GL_LINEAR)
                        }
                    }
                    GL.glDeleteTextures(2,oldTextures,0);GL.glDeleteFramebuffers(2,oldFbos,0)
                    bufferWidth=w;bufferHeight=h;sampleCount=0;rowCursor=0;shownIndex=0;traceWidth=w;traceHeight=h;baseDirty=true
                }
                // Cover every visible floor point plus a filter margin. Keep
                // world texel/photon density as the receiver grows beyond 4 units.
                val receiverScale=receiverExtent(s)/c.photonExtent
                val desiredMap=(c.photonMap*receiverScale).roundToInt().coerceIn(64,512)
                val launchSide=(c.photonSide*receiverScale).roundToInt().coerceIn(128,1536)
                if(mapSize!=desiredMap) {
                    allocate(photons,photonFbo,desiredMap,desiredMap)
                    mapSize=desiredMap;invalidatePhotons()
                }
                val elapsed=SystemClock.uptimeMillis()-changeTime
                if(dirtyPhotons && elapsed>=c.settleMs) {
                    GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,photonFbo)
                    GL.glViewport(0,0,mapSize,mapSize)
                    GL.glClearColor(0f,0f,0f,0f);GL.glClear(GL.GL_COLOR_BUFFER_BIT)
                    if(s.elevation>.05 && s.elevation<Math.PI-.05 && s.brightness>0) {
                        GL.glUseProgram(photonProgram);common(photonProgram,s)
                        i(photonProgram,"uPhotonSide",launchSide);i(photonProgram,"uPhotonMapSize",mapSize)
                        GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_ONE,GL.GL_ONE)
                        GL.glDrawArrays(GL.GL_POINTS,0,launchSide*launchSide)
                        GL.glDisable(GL.GL_BLEND)
                        photonReady=true
                    }
                    dirtyPhotons=false;sampleCount=0;rowCursor=0;baseDirty=true
                }
                if(sampleCount<c.samples || baseDirty) {
                    val p=pathProgram
                    val region=OpticalScreenRegion.project(contour,s.yaw,s.pitch,s.distance,s.fov,width.toDouble()/height,w,h)
                    val rows=if(moving)region.height else min(region.height-rowCursor,max(1,c.pixelBudget/max(1,region.width)))
                    GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbos[sampleCount%2]);GL.glViewport(0,0,w,h)
                    GL.glUseProgram(p);common(p,s)
                    texture(accum[1-sampleCount%2],1,p,"uPrevious");texture(photons,2,p,"uPhotons")
                    GL.glUniform2f(GL.glGetUniformLocation(p,"uResolution"),w.toFloat(),h.toFloat())
                    val cy=cos(s.pitch)
                    v(p,"uEye",(s.distance*cy*sin(s.yaw)).toFloat(),(1+s.distance*sin(s.pitch)).toFloat(),
                        (s.distance*cy*cos(s.yaw)).toFloat())
                    f(p,"uTanFov",tan(Math.toRadians(s.fov)*.5).toFloat());f(p,"uAspect",width.toFloat()/height)
                    val direct=LightingResponseCurve.intensity(s.brightness,s.camera.directBrightnessExponent)*
                        PhysicalAtmosphereModel.solarTransmission(s.mode,s.elevation,s.render.atmosphereExtinction.toDouble())*
                        s.camera.lightIntensity/100000.0
                    val sky=LightingResponseCurve.intensity(s.brightness,s.render.globalBrightnessExponent.toDouble())*
                        PhysicalAtmosphereModel.skyIlluminance(s.mode,s.elevation,s.render.atmosphereSkyIlluminance.toDouble(),
                            s.render.atmosphereHorizonFraction.toDouble())/100000.0
                    f(p,"uMaxSampleRadiance",c.maxSampleRadiance)
                    f(p,"uDirect",direct.toFloat());f(p,"uSky",sky.toFloat());f(p,"uHaze",s.render.atmosphereHaze)
                    val tint=PhysicalAtmosphereModel.sunlightRgb(s.mode,s.elevation,s.render.atmosphereExtinction.toDouble())
                    v(p,"uSunColor",tint[0],tint[1],tint[2])
                    val bg=PhysicalAtmosphereModel.backgroundRgb(s.mode,s.elevation,s.render.atmosphereHaze.toDouble(),s.brightness)
                    v(p,"uBackdrop",bg[0],bg[1],bg[2])
                    f(p,"uFloorWidth",s.scene.floorWidth);f(p,"uTile",s.scene.tileWidth)
                    f(p,"uDark",s.scene.darkLevel.toFloat());f(p,"uLight",s.scene.lightLevel.toFloat())
                    f(p,"uWhite",s.scene.whiteLevel.toFloat())
                    i(p,"uFloorMode",when(s.floor) { "checker"->1;"white"->2;else->0 })
                    i(p,"uPhotonReady",if(photonReady)1 else 0);i(p,"uFrame",sampleCount)
                    if(baseDirty) {
                        i(p,"uBaseOnly",1);i(p,"uPreserveGlass",if(havePreview)1 else 0)
                        GL.glUniform4f(GL.glGetUniformLocation(p,"uGlassRegion"),region.x.toFloat(),region.y.toFloat(),
                            (region.x+region.width).toFloat(),(region.y+region.height).toFloat())
                        // Neither base draw may sample an attached accumulation texture.
                        texture(photons,1,p,"uPrevious")
                        for(j in 0..1) {
                            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbos[j])
                            GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                        }
                        texture(accum[1-sampleCount%2],1,p,"uPrevious")
                        i(p,"uBaseOnly",0);baseDirty=false
                        GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbos[sampleCount%2])
                    }
                    if(region.width>0 && region.height>0) {
                    GL.glEnable(GL.GL_SCISSOR_TEST);GL.glScissor(region.x,region.y+rowCursor,region.width,rows)
                    GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                    GL.glDisable(GL.GL_SCISSOR_TEST)
                    shownIndex=sampleCount%2;rowCursor+=rows
                    if(rowCursor>=region.height) { sampleCount++;rowCursor=0 }
                    } else { sampleCount=c.samples;shownIndex=0 }
                    havePreview=true
                }
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0);GL.glViewport(0,0,width,height)
                GL.glUseProgram(displayProgram)
                texture(accum[shownIndex],0,displayProgram,"uImage")
                GL.glUniform2f(GL.glGetUniformLocation(displayProgram,"uScreen"),width.toFloat(),height.toFloat())
                f(displayProgram,"uExposure",c.exposure)
                i(displayProgram,"uFxaa",if(s.antialias)1 else 0)
                GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                val error=GL.glGetError();check(error==GL.GL_NO_ERROR) { "GL error 0x${error.toString(16)}" }
                if(sampleCount<c.samples)requestRender()
                else if((dirtyPhotons || moving) && !delayed) {
                    delayed=true
                    postDelayed({ queueEvent { delayed=false };requestRender() },(c.settleMs-min(elapsed,SystemClock.uptimeMillis()-motionTime)).coerceAtLeast(1))
                }
            } catch(e:Exception) { fail(e) }
        }
    }
}
