package com.example.arkrecruit;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenService extends Service {
  private static final String CHANNEL="recruit_capture";
  private final Handler main=new Handler(Looper.getMainLooper());
  private final AtomicBoolean busy=new AtomicBoolean(false);
  private MediaProjection projection; private VirtualDisplay virtualDisplay;
  private ImageReader reader; private HandlerThread worker; private Handler workerHandler;
  private TextRecognizer recognizer;
  private WindowManager wm; private Markers markers; private View gear, panel, settings;
  private WindowManager.LayoutParams gearParams;
  private final List<Recognition> recognized=new ArrayList<>();
  private List<RecruitmentEngine.Operator> operators=Collections.emptyList();
  private RecruitmentEngine.Result recommendation;
  private TextView panelText,statusText;
  private SharedPreferences prefs;
  private long lastScan=0; private int missing=0,width,height;
  private static final class Recognition {
    String tag; Rect bounds; Recognition(String t,Rect r){tag=t;bounds=r;}
  }
  @Override public void onCreate(){
    super.onCreate();prefs=getSharedPreferences("settings",MODE_PRIVATE);
    wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Анализ рекрутинга",NotificationManager.IMPORTANCE_LOW));
  }
  private Notification notification(){
    Intent stop=new Intent(this,ScreenService.class);stop.setAction("STOP");
    PendingIntent pi=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    return new Notification.Builder(this,CHANNEL).setContentTitle("Arknights Recruitment Assistant")
      .setContentText("Анализ экрана активен").setSmallIcon(android.R.drawable.ic_menu_search)
      .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Остановить",pi).build();
  }
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent==null)return START_NOT_STICKY;
    if("STOP".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
    if(!"START".equals(intent.getAction())||projection!=null)return START_NOT_STICKY;
    try {
      if(Build.VERSION.SDK_INT>=29)startForeground(73,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
      else startForeground(73,notification());
      Intent token;
      if(Build.VERSION.SDK_INT>=33)token=intent.getParcelableExtra("captureData",Intent.class);
      else token=(Intent)intent.getParcelableExtra("captureData");
      if(token==null)throw new IllegalStateException("Missing screen capture consent");
      MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
      projection=m.getMediaProjection(intent.getIntExtra("captureCode",Activity.RESULT_CANCELED),token);
      if(projection==null)throw new IllegalStateException("Capture denied");
      projection.registerCallback(new MediaProjection.Callback(){
        @Override public void onStop(){main.post(()->stopSelf());}
      },main);
      DisplayMetrics dm=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(dm);
      width=dm.widthPixels;height=dm.heightPixels;
      worker=new HandlerThread("RecruitCapture");worker.start();workerHandler=new Handler(worker.getLooper());
      reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
      recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
      createOverlays();
      reader.setOnImageAvailableListener(this::consumeFrame,workerHandler);
      virtualDisplay=projection.createVirtualDisplay("RecruitOCR",width,height,dm.densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,workerHandler);
      OperatorDatabase.load(this,false,(list,state)->{
        operators=list;
        if(statusText!=null)statusText.setText(state);
        render();
      });
    }catch(Exception e){
      android.util.Log.e("RecruitAssist","Capture initialization failed",e);
      stopSelf();
    }
    return START_NOT_STICKY;
  }
  private void consumeFrame(ImageReader source){
    Image image=source.acquireLatestImage();
    if(image==null)return;
    if(SystemClock.elapsedRealtime()-lastScan<1400||!busy.compareAndSet(false,true)){
      image.close();return;
    }
    lastScan=SystemClock.elapsedRealtime();
    Bitmap bmp=null;
    try {
      Image.Plane plane=image.getPlanes()[0];
      ByteBuffer buffer=plane.getBuffer();
      int stride=plane.getRowStride(),pixel=plane.getPixelStride();
      int padded=width+(stride-width*pixel)/pixel;
      Bitmap paddedBitmap=Bitmap.createBitmap(padded,height,Bitmap.Config.ARGB_8888);
      paddedBitmap.copyPixelsFromBuffer(buffer);
      bmp=Bitmap.createBitmap(paddedBitmap,0,0,width,height);
      paddedBitmap.recycle();
    }catch(Exception e){busy.set(false);}
    finally{image.close();}
    if(bmp==null)return;
    final Bitmap frame=bmp;
    recognizer.process(InputImage.fromBitmap(frame,0))
      .addOnSuccessListener(text->processText(text))
      .addOnFailureListener(e->android.util.Log.w("RecruitAssist","OCR failed",e))
      .addOnCompleteListener(task->{frame.recycle();busy.set(false);});
  }
  private void processText(Text result){
    LinkedHashMap<String,Rect> found=new LinkedHashMap<>();
    for(Text.TextBlock block:result.getTextBlocks()){
      for(Text.Line line:block.getLines()){
        String tag=RecruitmentEngine.normalize(line.getText());
        Rect bounds=line.getBoundingBox();
        if(tag!=null&&bounds!=null&&bounds.width()>10&&!found.containsKey(tag))found.put(tag,new Rect(bounds));
      }
    }
    if(found.size()<3){
      if(++missing>=3)main.post(()->{
        recognized.clear();recommendation=null;render();
      });
      return;
    }
    missing=0;
    if(found.size()>5)return; // ignore untrusted OCR detections
    main.post(()->{
      recognized.clear();
      for(Map.Entry<String,Rect> e:found.entrySet())recognized.add(new Recognition(e.getKey(),e.getValue()));
      chooseRecommendation();
    });
  }
  private void chooseRecommendation(){
    ArrayList<String> tags=new ArrayList<>();
    for(Recognition r:recognized)tags.add(r.tag);
    recommendation=RecruitmentEngine.best(tags,operators,prefs.getBoolean("chance",false),9);
    render();
  }
  private void render(){
    if(markers!=null)markers.invalidate();
    if(panelText!=null){
      if(recognized.isEmpty()) panelText.setText("Откройте экран рекрутинга\nОжидание пяти тегов...");
      else if(recommendation==null)panelText.setText("Теги найдены: "+recognized.size()+"\nНет проверенного результата");
      else panelText.setText("Выбрать: "+String.join(" + ",recommendation.tags)+
          "\n"+recommendation.description());
    }
    if(panel!=null)panel.setVisibility(prefs.getBoolean("panel",true)?View.VISIBLE:View.GONE);
  }
  private int dp(int px){return (int)(px*getResources().getDisplayMetrics().density+0.5f);}
  private WindowManager.LayoutParams layout(int w,int h,int flags){
    return new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      flags,PixelFormat.TRANSLUCENT);
  }
  private void createOverlays(){
    markers=new Markers(this);
    WindowManager.LayoutParams pass=layout(-1,-1,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
    // Android 12+ blocks touches through opaque untrusted overlays.
    pass.alpha=.70f;
    wm.addView(markers,pass);
    Button g=new Button(this);g.setText("⚙");g.setTextSize(21);g.setAllCaps(false);gear=g;
    gearParams=layout(dp(52),dp(52),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    gearParams.gravity=Gravity.LEFT|Gravity.TOP;gearParams.x=dp(8);gearParams.y=height/2;
    wm.addView(gear,gearParams);
    g.setOnTouchListener(new View.OnTouchListener(){
      float startX,startY,originX,originY;boolean dragged=false;
      public boolean onTouch(View v,android.view.MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){startX=e.getRawX();startY=e.getRawY();
          originX=gearParams.x;originY=gearParams.y;dragged=false;return true;}
        if(e.getAction()==MotionEvent.ACTION_MOVE){
          float dx=e.getRawX()-startX,dy=e.getRawY()-startY;
          if(Math.abs(dx)+Math.abs(dy)>dp(10))dragged=true;
          if(dragged){gearParams.x=(int)(originX+dx);gearParams.y=(int)(originY+dy);wm.updateViewLayout(gear,gearParams);}
          return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP){if(!dragged)toggleSettings();return true;}
        return false;
      }
    });
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(9),dp(6),dp(9),dp(6));box.setBackgroundColor(0xDD17232E);
    statusText=new TextView(this);statusText.setTextColor(Color.LTGRAY);statusText.setTextSize(11);
    statusText.setText("Загрузка базы Global...");box.addView(statusText);
    panelText=new TextView(this);panelText.setTextColor(Color.WHITE);panelText.setTextSize(12);
    box.addView(panelText);panel=box;
    WindowManager.LayoutParams p=layout(dp(244),-2,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    p.gravity=Gravity.LEFT|Gravity.CENTER_VERTICAL;p.x=dp(65);p.y=0;
    wm.addView(panel,p);
    render();
  }
  private void toggleSettings(){
    if(settings!=null){wm.removeView(settings);settings=null;return;}
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(12),dp(8),dp(12),dp(8));box.setBackgroundColor(0xEE21303B);
    TextView title=new TextView(this);title.setText("⚙ Настройки");title.setTextSize(18);title.setTextColor(Color.WHITE);box.addView(title);
    Switch sw=new Switch(this);sw.setText("Приоритет: шанс");sw.setTextColor(Color.WHITE);
    sw.setChecked(prefs.getBoolean("chance",false));box.addView(sw);
    sw.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("chance",on).apply();chooseRecommendation();});
    Switch show=new Switch(this);show.setText("Панель операторов");show.setTextColor(Color.WHITE);
    show.setChecked(prefs.getBoolean("panel",true));box.addView(show);
    show.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("panel",on).apply();render();});
    settingSlider(box,"Цвет: R","red",0,255,0);
    settingSlider(box,"Цвет: G","green",0,255,255);
    settingSlider(box,"Цвет: B","blue",0,255,90);
    settingSlider(box,"Прозрачность","alpha",20,100,85);
    settingSlider(box,"Толщина","stroke",1,12,4);
    settingSlider(box,"Скругление","corner",0,30,12);
    Button close=new Button(this);close.setText("Закрыть");box.addView(close);
    close.setOnClickListener(v->toggleSettings());
    settings=box;
    WindowManager.LayoutParams p=layout(dp(275),-2,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    p.gravity=Gravity.CENTER;p.alpha=.96f;wm.addView(settings,p);
  }
  private void settingSlider(LinearLayout box,String label,String key,int min,int max,int def){
    TextView t=new TextView(this);t.setTextColor(Color.WHITE);
    t.setText(label);box.addView(t);
    SeekBar b=new SeekBar(this);b.setMax(max-min);b.setProgress(prefs.getInt(key,def)-min);box.addView(b);
    b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar x,int v,boolean u){
        if(u){prefs.edit().putInt(key,v+min).apply();if(markers!=null)markers.invalidate();}
      }
      public void onStartTrackingTouch(SeekBar x){}
      public void onStopTrackingTouch(SeekBar x){}
    });
  }
  private final class Markers extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    Markers(Context c){super(c);setWillNotDraw(false);}
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);
      if(recommendation==null)return;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(dp(prefs.getInt("stroke",4)));
      paint.setColor(Color.argb((int)(255*prefs.getInt("alpha",85)/100f),
        prefs.getInt("red",0),prefs.getInt("green",255),prefs.getInt("blue",90)));
      float radius=dp(prefs.getInt("corner",12));
      for(Recognition r:recognized){
        if(!recommendation.tags.contains(r.tag))continue;
        RectF rect=new RectF(r.bounds);
        rect.inset(-dp(8),-dp(7));
        canvas.drawRoundRect(rect,radius,radius,paint);
      }
    }
  }
  @Override public void onConfigurationChanged(Configuration c){
    super.onConfigurationChanged(c); // projection size changes need a new consent/session
    stopSelf();
  }
  @Override public void onDestroy(){
    if(reader!=null)reader.setOnImageAvailableListener(null,null);
    if(virtualDisplay!=null)virtualDisplay.release();
    if(reader!=null)reader.close();
    if(projection!=null){projection.stop();projection=null;}
    if(recognizer!=null)recognizer.close();
    if(worker!=null)worker.quitSafely();
    if(settings!=null){wm.removeView(settings);settings=null;}
    if(panel!=null){wm.removeView(panel);panel=null;}
    if(gear!=null){wm.removeView(gear);gear=null;}
    if(markers!=null){wm.removeView(markers);markers=null;}
    super.onDestroy();
  }
  @Override public IBinder onBind(Intent intent){return null;}
}
