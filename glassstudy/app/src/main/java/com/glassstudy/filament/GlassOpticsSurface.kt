package com.glassstudy.filament

import android.content.Context
import android.opengl.GLES30 as GL
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.os.Debug
import com.glassstudy.filament.OpticalRenderMetrics.Stage
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
 * Unsupported drivers resume the retained Filament fallback surface.
 */
class GlassOpticsSurface(context:Context,private val onFailure:(String)->Unit):GLSurfaceView(context) {
    data class State(
        val yaw:Double,val pitch:Double,val distance:Double,val fov:Double,
        val azimuth:Double,val elevation:Double,val brightness:Double,
        val mode:LightEnvironmentMode,val floor:String,val scene:SceneModuleConfig,
        val render:RenderModuleConfig,val camera:CameraModuleConfig,val ior:Float,val antialias:Boolean,val analyticSurface:Boolean=false
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
    val metrics=OpticalRenderMetrics()
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
    var testOwnsState=false
        private set
    fun beginTest():State? {
        if(testOwnsState || failed!=null)return null
        val saved=lastState ?: return null
        testOwnsState=true
        return saved
    }
    fun testState(state:State,points:FloatArray) {
        check(testOwnsState)
        val at=System.nanoTime()
        queueEvent { renderer.contour=points.copyOf();renderer.profileDirty=true;renderer.maskDirty=true;renderer.invalidatePhotons();renderer.accept(state,at) };requestRender()
    }
    fun endTest(saved:State) {
        testOwnsState=false
        lastContour?.let { points -> queueEvent { renderer.contour=points.copyOf();renderer.profileDirty=true;renderer.maskDirty=true;renderer.invalidatePhotons() } }
        lastState=null
        update(saved)
    }
    data class TestCapture(val bitmap:android.graphics.Bitmap,val rays:String)
    fun captureTestImage(callback:(Result<TestCapture>)->Unit) {
        queueEvent {
            val result=runCatching {
                val rays=runCatching { renderer.probeRays() }.getOrElse { "Проба лучей недоступна: ${it.message}" }
                TestCapture(renderer.captureImage(),rays)
            }
            post { callback(result) }
        }
    }
    fun update(state:State) {
        if(testOwnsState)return
        if(state==lastState || failed!=null)return
        lastState=state
        val requestedAtNs=System.nanoTime()
        queueEvent { renderer.accept(state,requestedAtNs) }
        requestRender()
    }
    fun setContour(points:FloatArray) {
        if(testOwnsState)return
        if(lastContour?.contentEquals(points)==true)return
        lastContour=points.copyOf()
        val requestedAtNs=System.nanoTime()
        queueEvent { renderer.startProfileMeasurement(requestedAtNs);renderer.contour=points.copyOf();renderer.profileDirty=true;renderer.maskDirty=true;renderer.invalidatePhotons() }
        requestRender()
    }
    fun configureDiagnostics(config:DiagnosticsModuleConfig) {
        metrics.configure(config.sampleIntervalMs,config.historySeconds)
        queueEvent { renderer.configureGpuTiming(config.gpuTimingEnabled) }
    }
    override fun onPause() {
        queueEvent { renderer.pauseMeasurement() }
        super.onPause()
        metrics.pause()
    }
    override fun onResume() {
        super.onResume()
        queueEvent { renderer.resumeMeasurement() }
        requestRender()
    }
    fun diagnostics():String = failed?.let { "GPU-оптика: отказ ($it); Filament fallback" }
        ?: "GPU-оптика ES3: ${traceWidth}×${traceHeight}, $sampleCount spp; " +
            "каустика: ${if(photonReady)"карта потока" else "ожидание света"}; шум/предел путей: эксперимент"

    private inner class OpticalRenderer:Renderer {
        var contour= floatArrayOf(0f,0f,.01f,.01f,.02f,0f)
        var profileDirty=true
        var maskDirty=true
        private var state:State?=null
        private var oldLight:List<Any>?=null
        private var pathProgram=0
        private var photonProgram=0
        private var displayProgram=0
        private var maskProgram=0
        private var analyticMaskProgram=0
        private var maskTexture=0
        private var maskFbo=0
        private var profile=0
        private var boundsRadius=0f
        private var boundsLow=0f
        private var boundsHigh=0f
        private val uniformLocations=hashMapOf<Int,MutableMap<String,Int>>()
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
        private var completionFence=0L
        private var fencePollScheduled=false
        private var gpuDescription="не определён"
        private val gpuTimer=OpticalGpuTimer(metrics)

        fun configureGpuTiming(enabled:Boolean) = gpuTimer.configure(enabled)
        private inline fun measured(stage:Stage,block:()->Unit) {
            metrics.stageStarted(stage)
            val wall=System.nanoTime();val cpu=Debug.threadCpuTimeNanos()
            gpuTimer.begin(stage)
            try { block() } finally {
                gpuTimer.end()
                metrics.record(stage,System.nanoTime()-wall,Debug.threadCpuTimeNanos()-cpu)
            }
        }
        private fun clearFence() {
            if(completionFence!=0L) { GL.glDeleteSync(completionFence);completionFence=0L }
        }
        private fun restartMeasurement(reason:String,requestedAtNs:Long=System.nanoTime()) { clearFence();metrics.begin(reason,requestedAtNs) }
        fun pauseMeasurement() { clearFence();gpuTimer.pause() }
        fun resumeMeasurement() {
            if(state==null)return
            restartMeasurement("возврат в приложение")
            sampleCount=0;rowCursor=0;baseDirty=true;motionTime=SystemClock.uptimeMillis()
        }
        private fun pollCompletion() {
            if(completionFence==0L)return
            metrics.refreshElapsed()
            gpuTimer.poll()
            when(GL.glClientWaitSync(completionFence,0,0L)) {
                GL.GL_ALREADY_SIGNALED,GL.GL_CONDITION_SATISFIED -> { gpuTimer.poll();clearFence();metrics.complete() }
                GL.GL_WAIT_FAILED -> { clearFence();metrics.event("ошибка GPU fence: завершение не подтверждено") }
                else -> if(!fencePollScheduled) {
                    fencePollScheduled=true
                    postDelayed({ queueEvent { fencePollScheduled=false;pollCompletion() } },16L)
                }
            }
        }

        fun startProfileMeasurement(requestedAtNs:Long) = restartMeasurement("изменение профиля",requestedAtNs)
        fun invalidatePhotons() {
            dirtyPhotons=true;photonReady=false;sampleCount=0
            changeTime=SystemClock.uptimeMillis();motionTime=changeTime;rowCursor=0;baseDirty=true
        }
        fun accept(next:State,requestedAtNs:Long) {
            val previous=state
            if(previous==null || previous.yaw!=next.yaw || previous.pitch!=next.pitch ||
                previous.distance!=next.distance || previous.fov!=next.fov || previous.floor!=next.floor ||
                previous.scene.floorWidth!=next.scene.floorWidth ||
                previous.render.optics.maskSides!=next.render.optics.maskSides ||
                previous.analyticSurface!=next.analyticSurface)maskDirty=true
            val light=listOf(next.azimuth,next.elevation,next.brightness,next.mode,next.ior,
                next.scene,next.render,next.camera.lightIntensity,next.antialias,next.analyticSurface)
            if(light!=oldLight) { oldLight=light;invalidatePhotons() }
            val reason=when {
                previous==null -> "начальный рендер"
                previous.analyticSurface!=next.analyticSurface -> "смена поверхности лучей"
                previous.antialias!=next.antialias -> "переключение сглаживания"
                light!=listOf(previous.azimuth,previous.elevation,previous.brightness,previous.mode,previous.ior,
                    previous.scene,previous.render,previous.camera.lightIntensity,previous.antialias,previous.analyticSurface) -> "изменение света/сцены"
                else -> "движение камеры"
            }
            restartMeasurement(reason,requestedAtNs)
            state=next;sampleCount=0;rowCursor=0;baseDirty=true;motionTime=SystemClock.uptimeMillis()
        }
        fun probeRays():String {
            val s=state ?: error("Нет состояния")
            val w=s.render.optics.bounces*4;val h=9
            val fbo=IntArray(1);val tex=IntArray(1);var programId=0
            GL.glGenFramebuffers(1,fbo,0);GL.glGenTextures(1,tex,0)
            try {
                programId=program("quad.vert","probe.frag")
                GL.glBindTexture(GL.GL_TEXTURE_2D,tex[0])
                GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA32F,w,h,0,GL.GL_RGBA,GL.GL_FLOAT,null)
                GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST)
                GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST)
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbo[0])
                GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,tex[0],0)
                check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE) { "RGBA32F не поддерживается" }
                GL.glViewport(0,0,w,h);GL.glDisable(GL.GL_SCISSOR_TEST);GL.glDisable(GL.GL_BLEND)
                GL.glUseProgram(programId);common(programId,s)
                val cy=cos(s.pitch)
                v(programId,"uEye",(s.distance*cy*sin(s.yaw)).toFloat(),(1+s.distance*sin(s.pitch)).toFloat(),(s.distance*cy*cos(s.yaw)).toFloat())
                f(programId,"uTanFov",tan(Math.toRadians(s.fov)*.5).toFloat())
                f(programId,"uAspect",width.toFloat()/height);f(programId,"uFloorWidth",s.scene.floorWidth)
                GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                val buffer=ByteBuffer.allocateDirect(w*h*16).order(ByteOrder.nativeOrder()).asFloatBuffer()
                GL.glReadPixels(0,0,w,h,GL.GL_RGBA,GL.GL_FLOAT,buffer)
                check(GL.glGetError()==GL.GL_NO_ERROR) { "Ошибка чтения RGBA32F" }
                return buildString {
                    append("ПРОБА ЛУЧЕЙ: 9 лучей NDC x=-0.4/0/0.4 y=0.05/0.25/0.45; без jitter.\n")
                    append("Детерминированная передача, отражение только при TIR; это НЕ все случайные пути изображения.\n")
                    append("На шаг: hit.xyz/status (0=после выхода/лимит,1=стенка,2=пол,3=фон), normal.xyz/t, next.xyz/Fresnel, dot(d,n)/IOR/analytic/cull.\n")
                    for(ray in 0 until h)for(step in 0 until s.render.optics.bounces) {
                        append("ray=$ray step=$step ")
                        for(field in 0..3) {
                            val offset=(ray*w+step*4+field)*4
                            for(k in 0..3)append(String.format(java.util.Locale.US,"%.6g,",buffer.get(offset+k)))
                            append(" | ")
                        }
                        append('\n')
                    }
                }
            } finally {
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0);GL.glViewport(0,0,width,height)
                GL.glDeleteFramebuffers(1,fbo,0);GL.glDeleteTextures(1,tex,0)
                if(programId!=0) { uniformLocations.remove(programId);GL.glDeleteProgram(programId) }
                repeat(8) { GL.glGetError() }
            }
        }
        fun captureImage():android.graphics.Bitmap {
            check(metrics.snapshot().complete) { "Рендер не завершён GPU" }
            val s=state ?: error("Нет сцены")
            val w=bufferWidth;val h=bufferHeight
            check(w>0 && h>0)
            val target=IntArray(1);val textureId=IntArray(1)
            GL.glGenFramebuffers(1,target,0);GL.glGenTextures(1,textureId,0)
            try {
                GL.glBindTexture(GL.GL_TEXTURE_2D,textureId[0])
                GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RGBA8,w,h,0,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,null)
                GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST)
                GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST)
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,target[0])
                GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,textureId[0],0)
                check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE)
                GL.glViewport(0,0,w,h);GL.glDisable(GL.GL_SCISSOR_TEST);GL.glDisable(GL.GL_BLEND)
                GL.glUseProgram(displayProgram)
                texture(accum[shownIndex],0,displayProgram,"uImage")
                GL.glUniform2f(location(displayProgram,"uScreen"),w.toFloat(),h.toFloat())
                f(displayProgram,"uExposure",s.render.optics.exposure)
                i(displayProgram,"uFxaa",if(s.antialias)1 else 0)
                GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                val bytes=ByteBuffer.allocateDirect(w*h*4)
                GL.glReadPixels(0,0,w,h,GL.GL_RGBA,GL.GL_UNSIGNED_BYTE,bytes)
                check(GL.glGetError()==GL.GL_NO_ERROR) { "Ошибка чтения изображения" }
                val pixels=IntArray(w*h)
                for(y in 0 until h)for(x in 0 until w) {
                    val k=(y*w+x)*4
                    pixels[(h-1-y)*w+x]=android.graphics.Color.argb(255,
                        bytes.get(k).toInt() and 255,bytes.get(k+1).toInt() and 255,bytes.get(k+2).toInt() and 255)
                }
                return android.graphics.Bitmap.createBitmap(pixels,w,h,android.graphics.Bitmap.Config.ARGB_8888)
            } finally {
                GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0);GL.glViewport(0,0,width,height)
                GL.glDeleteFramebuffers(1,target,0);GL.glDeleteTextures(1,textureId,0)
            }
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
                // Context-local sync names cannot survive context recreation.
                completionFence=0L;fencePollScheduled=false;uniformLocations.clear()
                gpuDescription=listOf(GL.glGetString(GL.GL_VENDOR),GL.glGetString(GL.GL_RENDERER),
                    GL.glGetString(GL.GL_VERSION)).joinToString(" · ")
                metrics.begin("создание GL-контекста")
                gpuTimer.contextCreated(GL.glGetString(GL.GL_EXTENSIONS) ?: "")
                // Probe actual RGBA16F attachments and GL drawing errors below.
                // EXT_float_blend is required for 32F, not half-float blending.
                pathProgram=program("quad.vert","path.frag")
                photonProgram=program("photon.vert","photon.frag")
                displayProgram=program("quad.vert","display.frag")
                maskProgram=program("mask.vert","mask.frag")
                analyticMaskProgram=program("quad.vert","analytic_mask.frag")
                val ids=IntArray(1);GL.glGenTextures(1,ids,0);profile=ids[0]
                GL.glGenTextures(1,ids,0);maskTexture=ids[0]
                GL.glGenFramebuffers(1,ids,0);maskFbo=ids[0]
                GL.glGenTextures(2,accum,0);GL.glGenFramebuffers(2,fbos,0)
                GL.glGenTextures(1,ids,0);photons=ids[0]
                GL.glGenFramebuffers(1,ids,0);photonFbo=ids[0]
                val vao=IntArray(1);GL.glGenVertexArrays(1,vao,0);GL.glBindVertexArray(vao[0])
                initialized=true;bufferWidth=0;mapSize=0;profileDirty=true;maskDirty=true;havePreview=false;baseDirty=true
                invalidatePhotons()
            } catch(e:Exception) { fail(e) }
        }
        override fun onSurfaceChanged(gl:GL10?,w:Int,h:Int) {
            restartMeasurement("размер поверхности")
            width=w;height=h;sampleCount=0;rowCursor=0;baseDirty=true;maskDirty=true;motionTime=SystemClock.uptimeMillis()
        }
        private fun location(p:Int,name:String):Int = uniformLocations.getOrPut(p) { hashMapOf() }
            .getOrPut(name) { GL.glGetUniformLocation(p,name) }
        private fun f(p:Int,name:String,value:Float)=GL.glUniform1f(location(p,name),value)
        private fun i(p:Int,name:String,value:Int)=GL.glUniform1i(location(p,name),value)
        private fun v(p:Int,name:String,x:Float,y:Float,z:Float)=GL.glUniform3f(location(p,name),x,y,z)
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
        private fun allocateMask(w:Int,h:Int) {
            GL.glBindTexture(GL.GL_TEXTURE_2D,maskTexture)
            GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_R8,w,h,0,GL.GL_RED,GL.GL_UNSIGNED_BYTE,null)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_CLAMP_TO_EDGE)
            GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_CLAMP_TO_EDGE)
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,maskFbo)
            GL.glFramebufferTexture2D(GL.GL_FRAMEBUFFER,GL.GL_COLOR_ATTACHMENT0,GL.GL_TEXTURE_2D,maskTexture,0)
            check(GL.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER)==GL.GL_FRAMEBUFFER_COMPLETE) { "Silhouette mask incomplete" }
            maskDirty=true
        }
        private fun drawMask(s:State,w:Int,h:Int) {
            GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,maskFbo);GL.glViewport(0,0,w,h)
            GL.glDisable(GL.GL_SCISSOR_TEST);GL.glDisable(GL.GL_DEPTH_TEST);GL.glDisable(GL.GL_CULL_FACE)
            GL.glClearColor(0f,0f,0f,0f);GL.glClear(GL.GL_COLOR_BUFFER_BIT)
            val maskProgram=if(s.analyticSurface)analyticMaskProgram else this.maskProgram
            GL.glUseProgram(maskProgram);common(maskProgram,s)
            GL.glUniform2f(location(maskProgram,"uResolution"),w.toFloat(),h.toFloat())
            i(maskProgram,"uMaskSides",s.render.optics.maskSides)
            val cy=cos(s.pitch)
            v(maskProgram,"uEye",(s.distance*cy*sin(s.yaw)).toFloat(),(1+s.distance*sin(s.pitch)).toFloat(),
                (s.distance*cy*cos(s.yaw)).toFloat())
            f(maskProgram,"uTanFov",tan(Math.toRadians(s.fov)*.5).toFloat())
            f(maskProgram,"uAspect",width.toFloat()/height)
            f(maskProgram,"uFloorWidth",s.scene.floorWidth)
            i(maskProgram,"uFloorMode",if(s.floor=="none")0 else 1)
            GL.glDrawArrays(GL.GL_TRIANGLES,0,if(s.analyticSurface)3 else (contour.size/2-1)*s.render.optics.maskSides*6)
            maskDirty=false
        }
        private fun receiverExtent(s:State):Float=max(s.render.optics.photonExtent,
            s.scene.floorWidth*.5f+s.render.optics.photonMargin)
        private fun common(p:Int,s:State) {
            texture(profile,0,p,"uProfile");i(p,"uProfileCount",contour.size/2)
            i(p,"uCullSegments",if(s.render.optics.segmentCullingEnabled)1 else 0)
            v(p,"uBoundsMin",-boundsRadius-.001f,boundsLow-.001f,-boundsRadius-.001f)
            v(p,"uBoundsMax",boundsRadius+.001f,boundsHigh+.001f,boundsRadius+.001f)
            f(p,"uIor",s.ior);i(p,"uBounces",s.render.optics.bounces)
            val sun=LightDirectionMath.source(s.azimuth,s.elevation)
            v(p,"uSun",sun.x,sun.y,sun.z)
            f(p,"uPhotonExtent",receiverExtent(s))
            i(p,"uSmoothSurface",if(s.antialias)1 else 0)
            i(p,"uAnalyticSurface",if(s.analyticSurface)1 else 0)
        }
        override fun onDrawFrame(gl:GL10?) {
            val s=state ?: return
            if(!initialized || failed!=null || width<1 || height<1)return
            try {
                val c=s.render.optics
                gpuTimer.poll()
                if(completionFence!=0L)pollCompletion()
                if(profileDirty) {
                    measured(Stage.PROFILE) {
                        GL.glBindTexture(GL.GL_TEXTURE_2D,profile)
                        val data=ByteBuffer.allocateDirect(contour.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                        data.put(contour).position(0)
                        GL.glTexImage2D(GL.GL_TEXTURE_2D,0,GL.GL_RG32F,contour.size/2,1,0,GL.GL_RG,GL.GL_FLOAT,data)
                        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_NEAREST)
                        GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_NEAREST)
                        boundsRadius=0f;boundsLow=Float.POSITIVE_INFINITY;boundsHigh=Float.NEGATIVE_INFINITY
                        for(j in contour.indices step 2) {
                            boundsRadius=max(boundsRadius,contour[j+1]);boundsLow=min(boundsLow,contour[j]);boundsHigh=max(boundsHigh,contour[j])
                        }
                        profileDirty=false;maskDirty=true;sampleCount=0;rowCursor=0;baseDirty=true
                    }
                }
                val moving=SystemClock.uptimeMillis()-motionTime<c.settleMs
                val w=min(if(moving)c.movingWidth else c.width,width)
                val h=(w*height.toFloat()/width).roundToInt().coerceAtLeast(1)
                if(bufferWidth!=w || bufferHeight!=h) {
                    measured(Stage.BUFFERS) {
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
                        allocateMask(w,h)
                        bufferWidth=w;bufferHeight=h;sampleCount=0;rowCursor=0;shownIndex=0;traceWidth=w;traceHeight=h;baseDirty=true
                    }
                }
                // Cover every visible floor point plus a filter margin. Keep
                // world texel/photon density as the receiver grows beyond 4 units.
                if(maskDirty)measured(Stage.MASK) { drawMask(s,w,h) }
                val receiverScale=receiverExtent(s)/c.photonExtent
                val desiredMap=(c.photonMap*receiverScale).roundToInt().coerceIn(64,512)
                val launchSide=(c.photonSide*receiverScale).roundToInt().coerceIn(128,1536)
                if(mapSize!=desiredMap) {
                    measured(Stage.BUFFERS) { allocate(photons,photonFbo,desiredMap,desiredMap) }
                    mapSize=desiredMap;invalidatePhotons()
                }
                val elapsed=SystemClock.uptimeMillis()-changeTime
                if(dirtyPhotons && elapsed>=c.settleMs) {
                    metrics.event("пересчёт каустики")
                    measured(Stage.PHOTONS) {
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
                }
                if(!moving && !dirtyPhotons && w==min(c.width,width))metrics.qualityStarted()
                if(sampleCount<c.samples || baseDirty) {
                    val p=pathProgram
                    val region=OpticalScreenRegion.project(contour,s.yaw,s.pitch,s.distance,s.fov,width.toDouble()/height,w,h)
                    val rows=if(moving)region.height else min(region.height-rowCursor,max(1,c.pixelBudget/max(1,region.width)))
                    GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,fbos[sampleCount%2]);GL.glViewport(0,0,w,h)
                    GL.glUseProgram(p);common(p,s)
                    texture(accum[1-sampleCount%2],1,p,"uPrevious");texture(photons,2,p,"uPhotons")
                    texture(maskTexture,3,p,"uGlassMask");i(p,"uUseGlassMask",1)
                    GL.glUniform2f(location(p,"uResolution"),w.toFloat(),h.toFloat())
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
                        measured(Stage.BASE) {
                            i(p,"uBaseOnly",1);i(p,"uPreserveGlass",if(havePreview)1 else 0)
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
                    }
                    if(region.width>0 && region.height>0) {
                        measured(Stage.PATH) {
                            GL.glEnable(GL.GL_SCISSOR_TEST);GL.glScissor(region.x,region.y+rowCursor,region.width,rows)
                            GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                            GL.glDisable(GL.GL_SCISSOR_TEST)
                            shownIndex=sampleCount%2;rowCursor+=rows
                            if(rowCursor>=region.height) { sampleCount++;rowCursor=0 }
                        }
                    } else { sampleCount=c.samples;shownIndex=0 }
                    havePreview=true
                }
                measured(Stage.DISPLAY) {
                    GL.glBindFramebuffer(GL.GL_FRAMEBUFFER,0);GL.glViewport(0,0,width,height)
                    GL.glUseProgram(displayProgram)
                    texture(accum[shownIndex],0,displayProgram,"uImage")
                    GL.glUniform2f(location(displayProgram,"uScreen"),width.toFloat(),height.toFloat())
                    f(displayProgram,"uExposure",c.exposure)
                    i(displayProgram,"uFxaa",if(s.antialias)1 else 0)
                    GL.glDrawArrays(GL.GL_TRIANGLES,0,3)
                }
                val error=GL.glGetError();check(error==GL.GL_NO_ERROR) { "GL error 0x${error.toString(16)}" }
                val finalQuality=!moving && !dirtyPhotons && w==min(c.width,width)
                val phase=when {
                    moving -> "preview/стабилизация"
                    dirtyPhotons -> "ожидание каустики"
                    sampleCount<c.samples -> "накопление лучей"
                    else -> "ожидание GPU"
                }
                val previousMetrics=metrics.snapshot()
                val rebuildParameters=previousMetrics.run==0L || previousMetrics.width!=w || previousMetrics.height!=h || sampleCount<=1
                val regionInfo=if(rebuildParameters)OpticalScreenRegion.project(contour,s.yaw,s.pitch,s.distance,s.fov,width.toDouble()/height,w,h) else null
                val parameters=if(rebuildParameters) "Цель: ${min(c.width,width)}px · ${c.samples} spp · ${c.bounces} границ; preview ${c.movingWidth}px; settle ${c.settleMs} мс; бюджет ${c.pixelBudget} px/подачу\n"+
                    "Отсечение сегментов ${s.render.optics.segmentCullingEnabled}; профиль ${contour.size/2} точек; маска ${c.maskSides} сторон; scissor ${regionInfo?.width}×${regionInfo?.height} (прямоугольник планирования, не площадь стекла)\n"+
                    "Карта каустики ${mapSize}×${mapSize}; запуск ${launchSide}² (${launchSide.toLong()*launchSide}) фотонов; полуразмер ${receiverExtent(s)}; готова=$photonReady\n"+
                    "Поверхность лучей: ${if(s.analyticSurface)"гладкий кубический профиль" else "исходные отрезки"}\n"+
                    "IOR ${s.ior}; сглаживание/FXAA ${s.antialias}; отсечение выбросов ${c.maxSampleRadiance}" else previousMetrics.parameters
                metrics.progress(phase,w,h,sampleCount,c.samples,finalQuality,parameters,gpuDescription,
                    17L*w*h+8L*mapSize*mapSize+contour.size*4L)
                if(finalQuality && sampleCount>=c.samples && !metrics.snapshot().complete) {
                    completionFence=GL.glFenceSync(GL.GL_SYNC_GPU_COMMANDS_COMPLETE,0)
                    check(completionFence!=0L) { "Cannot confirm GPU completion" }
                    GL.glFlush();pollCompletion()
                }
                if(sampleCount<c.samples)requestRender()
                else if((dirtyPhotons || moving) && !delayed) {
                    delayed=true
                    postDelayed({ queueEvent { delayed=false };requestRender() },(c.settleMs-min(elapsed,SystemClock.uptimeMillis()-motionTime)).coerceAtLeast(1))
                }
            } catch(e:Exception) { fail(e) }
        }
    }
}
