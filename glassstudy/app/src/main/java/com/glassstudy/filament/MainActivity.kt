package com.glassstudy.filament

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.Gravity
import android.graphics.Color
import android.widget.*
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.filament.Camera
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import kotlin.math.*

class MainActivity : Activity() {
  companion object {
    init { Utils.init() }
    val ids = arrayOf("faceted","tumbler","wine","mug","pitcher","bowl","vase","sphere","cube","cylinder","cone")
    val names = arrayOf("Гранёный стакан","Стакан","Бокал","Чашка","Кувшин","Пиала","Ваза","Сфера","Куб","Цилиндр","Конус")
    val lods = arrayOf("low","medium","high")
  }
  lateinit var surface:SurfaceView
  lateinit var viewer:ModelViewer
  lateinit var obj:Spinner
  lateinit var lod:Spinner
  lateinit var status:TextView
  var yaw=.35;var pitch=.12;var distance=5.3; var touchX=0f;var touchY=0f;var fingers=0f
  var active=false;var prev=0L;var fps=0.0
  val frame=object:Choreographer.FrameCallback {
    override fun doFrame(t:Long) {
      if(!active)return
      Choreographer.getInstance().postFrameCallback(this)
      if(prev>0 && t>prev){val x=1e9/(t-prev).toDouble();fps=if(fps==0.0)x else fps*.9+x*.1}
      prev=t
      val x=distance*cos(pitch)*sin(yaw);val z=distance*cos(pitch)*cos(yaw);val y=1.15+distance*sin(pitch)
      viewer.camera.lookAt(x,y,z,0.0,1.15,0.0,0.0,1.0,0.0)
      viewer.camera.setProjection(45.0,surface.width.toDouble()/max(1,surface.height),.05,200.0,Camera.Fov.VERTICAL)
      viewer.render(t)
    }
  }
  fun dp(n:Int)=(n*resources.displayMetrics.density).toInt()
  fun button(t:String, f:()->Unit)=Button(this).apply { text=t;setAllCaps(false);setOnClickListener{f()} }
  override fun onCreate(savedInstanceState:Bundle?) {
    super.onCreate(savedInstanceState)
    surface=SurfaceView(this)
    val root=FrameLayout(this)
    root.addView(surface,FrameLayout.LayoutParams(-1,-1))
    setContentView(root)
    viewer=ModelViewer(surface,manipulator=null)
    viewer.scene.skybox=Skybox.Builder().color(.43f,.46f,.52f,1f).build(viewer.engine)
    viewer.camera.setExposure(16f,1f/125f,100f)
    viewer.view.antiAliasing=com.google.android.filament.View.AntiAliasing.FXAA
    surface.setOnTouchListener { _,e -> onTouch(e) }
    val panel=LinearLayout(this).apply {
      orientation=LinearLayout.VERTICAL
      setBackgroundColor(Color.argb(205,28,48,60))
      setPadding(dp(8),dp(5),dp(8),dp(5))
    }
    status=TextView(this).apply{setTextColor(Color.WHITE);textSize=16f;text="GlassStudy Filament"}
    panel.addView(status)
    val controls=LinearLayout(this)
    obj=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,names);setSelection(1)}
    lod=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Low","Medium","High"));setSelection(1)}
    controls.addView(obj,LinearLayout.LayoutParams(0,dp(50),2f))
    controls.addView(lod,LinearLayout.LayoutParams(0,dp(50),1f))
    panel.addView(controls)
    panel.addView(button("Показать объект"){loadModel()})
    root.addView(panel,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
    val footer=LinearLayout(this)
    footer.addView(button("Диагностика") {
      val s="GlassStudy Filament 0.2 beta\nRenderer: Google Filament (native)\nShared mesh: glTF GLB\nObject: ${ids[obj.selectedItemPosition]}\nFPS: ${"%.1f".format(fps)}\nGPU: ${android.os.Build.HARDWARE}"
      AlertDialog.Builder(this).setTitle("Диагностика").setMessage(s).setPositiveButton("OK",null).show()
    },LinearLayout.LayoutParams(-1,dp(52)))
    root.addView(footer,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
    loadModel()
  }
  fun loadModel(){
    try {
      val fn="models/${ids[obj.selectedItemPosition]}_${lods[lod.selectedItemPosition]}.glb"
      val a=assets.open(fn).use{it.readBytes()}
      val b=ByteBuffer.allocateDirect(a.size).apply{put(a);flip()}
      viewer.loadModelGlb(b)
      if(viewer.asset==null)error("gltfio rejected $fn")
      status.text="GlassStudy · Filament\n${names[obj.selectedItemPosition]} / ${lods[lod.selectedItemPosition]}"
    } catch(e:Exception) {
      AlertDialog.Builder(this).setTitle("Ошибка модели").setMessage(e.stackTraceToString().take(1500)).setPositiveButton("OK",null).show()
    }
  }
  fun pinch(e:MotionEvent):Float{
    val dx=e.getX(0)-e.getX(1);val dy=e.getY(0)-e.getY(1);return sqrt(dx*dx+dy*dy)
  }
  fun onTouch(e:MotionEvent):Boolean {
    when(e.actionMasked){
      MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> {
        touchX=e.getX(0);touchY=e.getY(0);fingers=if(e.pointerCount>=2)pinch(e) else 0f
      }
      MotionEvent.ACTION_MOVE -> {
        if(e.pointerCount>=2) {
          val p=pinch(e);if(p>1 && fingers>1)distance=(distance*fingers/p).coerceIn(2.0,35.0)
          fingers=p
        } else {
          yaw-=(e.x-touchX)/max(1,surface.width)*6
          pitch=(pitch-(e.y-touchY)/max(1,surface.height)*4).coerceIn(-1.25,1.25)
        }
        touchX=e.getX(0);touchY=e.getY(0)
      }
    }
    return true
  }
  override fun onResume(){super.onResume();if(::viewer.isInitialized){active=true;Choreographer.getInstance().postFrameCallback(frame)}}
  override fun onPause(){active=false;Choreographer.getInstance().removeFrameCallback(frame);super.onPause()}
  override fun onDestroy(){active=false;Choreographer.getInstance().removeFrameCallback(frame);if(::viewer.isInitialized)viewer.destroy();super.onDestroy()}
}
