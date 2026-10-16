package com.glassstudy.filament

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.os.Environment
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
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.Manipulator
import android.view.WindowInsets
import android.view.View
import android.widget.AdapterView
import android.os.Build
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
  var zoomDistance=4.0f
  var pinchStartDistance=4.0f
  var pinchStartSpan=0f
  var pinchLastDistance=4.0f
  var zoomValue=4.0f
  var fingersActive=false
  lateinit var manip:Manipulator
  var iblReady=false;var iblMessage="";var geometryTest=false;var loadedFile="";
  var modelLoads=0
  lateinit var patches:PatchManager
  private val PICK_PATCH=1209
  val frame=object:Choreographer.FrameCallback {
    override fun doFrame(t:Long) {
      if(!active)return
      Choreographer.getInstance().postFrameCallback(this)
      if(prev>0 && t>prev){val x=1e9/(t-prev).toDouble();fps=if(fps==0.0)x else fps*.9+x*.1}
      prev=t
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
    window.statusBarColor=Color.rgb(28,48,60)
    window.navigationBarColor=Color.rgb(28,48,60)
    if (Build.VERSION.SDK_INT >= 30) {
      root.setOnApplyWindowInsetsListener { v, ins ->
        val bars=ins.getInsets(WindowInsets.Type.systemBars())
        v.setPadding(0,bars.top,0,bars.bottom)
        ins
      }
      root.requestApplyInsets()
    } else root.fitsSystemWindows=true
    manip=Manipulator.Builder().targetPosition(0f,1.1f,0f).orbitHomePosition(0f,1.65f,4.0f).orbitSpeed(.0030f,-.0030f).zoomSpeed(1.0f).viewport(max(1,resources.displayMetrics.widthPixels),max(1,resources.displayMetrics.heightPixels)).build(Manipulator.Mode.ORBIT)
    patches=PatchManager(this)
    viewer=ModelViewer(surface,manipulator=manip)
    viewer.scene.skybox=Skybox.Builder().color(.17f,.20f,.24f,1f).build(viewer.engine)
    try {
      val bytes=patches.asset("studio_ibl.ktx") ?: assets.open("studio_ibl.ktx").use{it.readBytes()}
      val buffer=ByteBuffer.allocateDirect(bytes.size).apply{put(bytes);flip()}
      val light=KTX1Loader.createIndirectLight(viewer.engine,buffer)
      viewer.scene.indirectLight=light
      light.intensity=30_000.0f
      iblReady=true;iblMessage="IBL loaded ("+bytes.size+" bytes)"
    } catch(e:Exception){iblMessage=e.javaClass.simpleName+": "+e.message}
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
    panel.addView(button("Обновления .pupdate") { showPatchDialog() })
    root.addView(panel,FrameLayout.LayoutParams(-1,-2,Gravity.TOP))
    val footer=LinearLayout(this).apply { setBackgroundColor(Color.rgb(28,48,60)) }
    val diagnostic=button("Диагностика") {
      val entityCount=viewer.asset?.entities?.size ?: 0
      val renderables=viewer.scene.renderableCount
      val report="GlassStudy Filament 0.2.04\nПатчи: "+patches.report+"\nАктивная версия: "+patches.version+"\nGlassStudy Filament 0.2.03\nFilament PBR native\nModel: "+loadedFile+
        "\nLoaded assets: "+modelLoads+"\nAsset entities: "+entityCount+"\nScene renderables: "+renderables+
        "\nIBL: "+iblReady+" ("+iblMessage+")\nTriangle mesh overlay: "+geometryTest+
        "\nFPS: "+String.format("%.1f",fps)+"\nGPU: "+android.os.Build.HARDWARE
      AlertDialog.Builder(this).setTitle("Диагностика").setMessage(report)
        .setNeutralButton("Копировать"){ _,_ ->
          val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          clipboard.setPrimaryClip(ClipData.newPlainText("GlassStudy diagnostic",report))
        }.setPositiveButton("Закрыть",null).show()
    }
    footer.addView(diagnostic,LinearLayout.LayoutParams(0,dp(52),1f))
    val checkButton=button("Сетка") { geometryTest=!geometryTest;loadModel() }
    footer.addView(checkButton,LinearLayout.LayoutParams(0,dp(52),1f))
    root.addView(footer,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
    val selectListener=object: AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long){loadModel()}
      override fun onNothingSelected(parent:AdapterView<*>?) {}
    }
    obj.onItemSelectedListener=selectListener
    lod.onItemSelectedListener=selectListener
    loadModel()
    if(patches.hasDownloadsPermission()) {
      val previous=patches.version
      patches.scanDownloads()
      if(patches.version!=previous)loadModel()
    }
  }
  fun showPatchDialog() {
    val opts=arrayOf("Проверить Загрузки","Выбрать .pupdate","Разрешить доступ к Загрузкам","Отменить последний патч")
    AlertDialog.Builder(this).setTitle("Обновления GlassStudy").setItems(opts){ _,which ->
      try {
        when(which){
          0 -> {
            val old=patches.version
            val msg=patches.scanDownloads()
            if(patches.version!=old)loadModel()
            AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK",null).show()
          }
          1 -> {
            val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
              addCategory(Intent.CATEGORY_OPENABLE);type="*/*"
            }
            startActivityForResult(intent,PICK_PATCH)
          }
          2 -> if(Build.VERSION.SDK_INT>=30) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
              Uri.parse("package:"+packageName)))
          }
          3 -> { patches.revert("отменено пользователем");loadModel() }
        }
      }catch(e:Exception){
        AlertDialog.Builder(this).setTitle("Ошибка патча").setMessage(e.message).setPositiveButton("OK",null).show()
      }
    }.show()
  }
  override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
    super.onActivityResult(requestCode,resultCode,data)
    if(requestCode==PICK_PATCH && resultCode==Activity.RESULT_OK && data?.data!=null) {
      try {
        val msg=patches.installFromUri(data.data!!)
        loadModel()
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("OK",null).show()
      }catch(e:Exception){
        AlertDialog.Builder(this).setTitle("Патч отклонён").setMessage(e.message).setPositiveButton("OK",null).show()
      }
    }
  }
  fun loadModel(){
    try {
      val fn="models/${ids[obj.selectedItemPosition]}_${lods[lod.selectedItemPosition]}"+(if(geometryTest)"_debug" else "")+".glb"
      val a=patches.asset(fn) ?: assets.open(fn).use{it.readBytes()}
      val b=ByteBuffer.allocateDirect(a.size).apply{put(a);flip()}
      viewer.loadModelGlb(b)
      if(viewer.asset==null)error("gltfio rejected $fn")
      loadedFile=fn
      modelLoads++
      patches.confirmModelLoaded()
      status.text="GlassStudy · Filament 0.2.03\n${names[obj.selectedItemPosition]} / ${lods[lod.selectedItemPosition]}"+(if(geometryTest)" · СЕТКА" else " · Стекло")
    } catch(e:Exception) {
      if (patches.version > 0L) { patches.revert(e.message ?: "GLB ошибка"); loadModel(); return }
      status.text="Ошибка загрузки модели: "+e.message
      AlertDialog.Builder(this).setTitle("Ошибка модели").setMessage(e.stackTraceToString().take(1500)).setPositiveButton("OK",null).show()
    }
  }
  fun pinch(e:MotionEvent):Float{
    val dx=e.getX(0)-e.getX(1);val dy=e.getY(0)-e.getY(1);return sqrt(dx*dx+dy*dy)
  }
  fun onTouch(e:MotionEvent):Boolean {
    // Native Filament camera manipulators expect one drag coordinate system.
    // Camera orbit speed has its vertical sign corrected in the Builder.
    val x=e.getX(0).toInt()
    val y=e.getY(0).toInt()
    when(e.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        fingersActive=false
        touchX=e.x;touchY=e.y
        manip.grabBegin(x,y,false)
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        manip.grabEnd()
        if(e.pointerCount>=2) {
          fingersActive=true
          pinchStartSpan=pinch(e).coerceAtLeast(1f)
          pinchStartDistance=zoomDistance
        }
      }
      MotionEvent.ACTION_MOVE -> {
        if(e.pointerCount>=2) {
          val span=pinch(e).coerceAtLeast(1f)
          if(!fingersActive) {
            manip.grabEnd()
            fingersActive=true
            pinchStartSpan=span
            pinchStartDistance=zoomDistance
          }
          // A fixed gesture start eliminates pinch asymmetry and accumulated drift.
          val target=(pinchStartDistance*pinchStartSpan/span).coerceIn(1.6f,22f)
          val delta=target-zoomDistance
          // Filament scroll uses -delta * zoomSpeed along gaze; negative dolly is zoom in.
          // For our chosen zoomSpeed=1, physical distance change is -scrollDelta.
          if(kotlin.math.abs(delta)>0.0001f) {
            manip.scroll(x,y,-delta)
            zoomDistance=target
          }
        } else if(!fingersActive) {
          manip.grabUpdate(x,y)
        }
        touchX=e.x;touchY=e.y
      }
      MotionEvent.ACTION_POINTER_UP -> {
        // Re-anchor orbit only after the last pointer becomes active.
        if(e.pointerCount<=2){
          fingersActive=false
          val remain=if(e.actionIndex==0)1 else 0
          manip.grabBegin(e.getX(remain).toInt(),e.getY(remain).toInt(),false)
        } else {
          pinchStartSpan=pinch(e)
          pinchStartDistance=zoomDistance
        }
      }
      MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
        fingersActive=false
        manip.grabEnd()
      }
    }
    return true
  }
  override fun onResume(){super.onResume();if(::viewer.isInitialized){active=true;Choreographer.getInstance().postFrameCallback(frame)}}
  override fun onPause(){active=false;Choreographer.getInstance().removeFrameCallback(frame);super.onPause()}
  override fun onDestroy(){active=false;Choreographer.getInstance().removeFrameCallback(frame);super.onDestroy()}
}
