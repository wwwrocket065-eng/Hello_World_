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
  private FloatingControlButton playPause;
  private boolean paused=true, inRecruitment=false;
  private WindowManager.LayoutParams gearParams,panelParams;
  private final List<Recognition> recognized=new ArrayList<>();
  private List<RecruitmentEngine.Operator> operators=Collections.emptyList();
  private RecruitmentEngine.Result recommendation;
  private TextView panelText,statusText,operatorListText,listToggle;
  private ScrollView operatorScroll,variantsScroll;
  private TextView variantsToggle;
  private LinearLayout variantsBox;
  private boolean variantsExpanded;
  private String selectedVariant,lastVariantsSignature="";
  private List<RecruitmentEngine.Result> variants=Collections.emptyList();
  private boolean operatorListExpanded=false;
  private SharedPreferences prefs;
  private long lastScan=0; private int missing=0,width,height;
  private volatile int captureWidth=0,captureHeight=0;
  private volatile int detectedCount=0;
  private volatile boolean resizePending=false;
  // Screen-space rectangles of our own text-bearing windows. Never OCR our panel.
  private volatile Rect[] overlayExclusions=new Rect[0];
  private int recognitionWidth,recognitionHeight;
  private volatile int scanEpoch;
  private static final class Recognition {
    String tag; Rect bounds; Rect tile; Recognition(String t,Rect r,Rect tile){tag=t;bounds=r;this.tile=tile;}
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
      .setContentText(projection==null?"Плавающие кнопки включены":"Анализ экрана активен").setSmallIcon(android.R.drawable.ic_menu_search)
      .setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Остановить",pi).build();
  }
  @Override public int onStartCommand(Intent intent,int flags,int startId){
    if(intent==null)return START_NOT_STICKY;
    String action=intent.getAction();
    if("STOP".equals(action)){stopSelf();return START_NOT_STICKY;}
    if("SHOW".equals(action)){
      try{
        if(projection==null){
          if(Build.VERSION.SDK_INT>=34)
            startForeground(73,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
          else startForeground(73,notification());
        }
        createOverlays();
      }catch(Exception e){
        android.util.Log.e("RecruitAssist","Overlay creation failed",e);
        Toast.makeText(this,"Не удалось показать кнопки. Проверьте разрешение.",Toast.LENGTH_LONG).show();
        stopSelf();
      }
      return START_NOT_STICKY;
    }
    if(!"CAPTURE".equals(action)||projection!=null)return START_NOT_STICKY;
    try {
      if(Build.VERSION.SDK_INT>=29)startForeground(73,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
      else startForeground(73,notification());
      Intent token;
      if(Build.VERSION.SDK_INT>=33)token=intent.getParcelableExtra("captureData",Intent.class);
      else token=(Intent)intent.getParcelableExtra("captureData");
      if(token==null)throw new IllegalStateException("Missing screen capture consent");
      MediaProjectionManager manager=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
      projection=manager.getMediaProjection(intent.getIntExtra("captureCode",Activity.RESULT_CANCELED),token);
      if(projection==null)throw new IllegalStateException("Capture denied");
      projection.registerCallback(new MediaProjection.Callback(){
        @Override public void onStop(){main.post(()->stopSelf());}
      },main);
      DisplayMetrics dm=new DisplayMetrics();
      wm.getDefaultDisplay().getRealMetrics(dm);
      width=dm.widthPixels;height=dm.heightPixels;
      captureWidth=width;captureHeight=height;
      worker=new HandlerThread("RecruitCapture");worker.start();workerHandler=new Handler(worker.getLooper());
      reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
      recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
      createOverlays();
      reader.setOnImageAvailableListener(this::consumeFrame,workerHandler);
      virtualDisplay=projection.createVirtualDisplay("RecruitOCR",width,height,dm.densityDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,workerHandler);
      paused=false;inRecruitment=false;lastScan=0;
      updatePlayButton();render();
      NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
      nm.notify(73,notification());
      OperatorDatabase.load(this,false,(list,state)->{
        operators=list;
        if(statusText!=null)statusText.setText(state);
        if(!paused&&inRecruitment)chooseRecommendation();
      });
    }catch(Exception e){
      android.util.Log.e("RecruitAssist","Capture initialization failed",e);
      Toast.makeText(this,"Ошибка захвата экрана. Проверьте разрешения.",Toast.LENGTH_LONG).show();
      stopSelf();
    }
    return START_NOT_STICKY;
  }
  private void updatePlayButton(){
    if(playPause!=null)playPause.setPlaying(projection!=null&&!paused);
  }
  private void togglePlayPause(){
    if(projection==null){
      try{
        Intent permission=new Intent(this,CapturePermissionActivity.class);
        // Use a separate transient task so completing system consent resumes
        // the previously foreground game, not MainActivity in portrait mode.
        DisplayMetrics dm=new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        permission.putExtra("captureLandscape",dm.widthPixels>dm.heightPixels);
        permission.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
          Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS|Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(permission);
      }catch(Exception e){
        android.util.Log.e("RecruitAssist","Cannot show capture consent",e);
        Toast.makeText(this,"Откройте приложение для разрешения захвата",Toast.LENGTH_LONG).show();
        Intent app=new Intent(this,MainActivity.class);
        app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(app);
      }
      return;
    }
    paused=!paused;scanEpoch++;
    lastScan=0;
    if(paused){
      inRecruitment=false;
      recognized.clear();recommendation=null;
    }
    updatePlayButton();render();
  }
  private void consumeFrame(ImageReader source){
    Image image;
    try{image=source.acquireLatestImage();}catch(IllegalStateException closed){return;}
    if(image==null)return;
    // Catch rotations even when Android does not invoke the Service callback promptly.
    DisplayMetrics measured=new DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(measured);
    if((width!=measured.widthPixels||height!=measured.heightPixels)&&!resizePending){
      resizePending=true;
      image.close();
      main.post(()->{try{refreshDisplaySize();}finally{resizePending=false;}});
      return;
    }
    if(paused||SystemClock.elapsedRealtime()-lastScan<(inRecruitment?1300:2500)||!busy.compareAndSet(false,true)){
      image.close();return;
    }
    lastScan=SystemClock.elapsedRealtime();
    Bitmap bmp=null;
    try {
      Image.Plane plane=image.getPlanes()[0];
      ByteBuffer buffer=plane.getBuffer();
      int stride=plane.getRowStride(),pixel=plane.getPixelStride();
      int imageWidth=image.getWidth(),imageHeight=image.getHeight();
      int padded=imageWidth+(stride-imageWidth*pixel)/pixel;
      Bitmap paddedBitmap=Bitmap.createBitmap(padded,imageHeight,Bitmap.Config.ARGB_8888);
      paddedBitmap.copyPixelsFromBuffer(buffer);
      bmp=Bitmap.createBitmap(paddedBitmap,0,0,imageWidth,imageHeight);
      if(bmp!=paddedBitmap)paddedBitmap.recycle();
      captureWidth=imageWidth;captureHeight=imageHeight;
    }catch(Exception e){android.util.Log.w("RecruitAssist","Capture frame read failed",e);}
    finally{image.close();}
    if(bmp==null){busy.set(false);return;}
    final Bitmap frame=bmp;
    final int epoch=scanEpoch;
    // An all-black capture previously looked like OCR simply wasn't working.
    if(isBlankFrame(frame)){
      frame.recycle();busy.set(false);
      main.post(()->{
        if(paused)return;
        detectedCount=0;
        // A transient blank frame must not erase a valid on-screen recommendation.
        // Keep the last confirmed tags/highlights until pause, a new valid tag set,
        // or reliable evidence of leaving the recruitment page.
        if(statusText!=null)statusText.setText(inRecruitment
          ?"Захват: пропуск кадра • подсветка сохранена"
          :"Захват: чёрный кадр");
        render();
      });
      return;
    }
    try{
      recognizer.process(InputImage.fromBitmap(frame,0))
        .addOnSuccessListener(text->processText(text,frame,epoch))
        .addOnFailureListener(e->{
          android.util.Log.w("RecruitAssist","OCR failed",e);
          if(statusText!=null)statusText.setText("Ошибка распознавания текста");
        })
        .addOnCompleteListener(task->{frame.recycle();busy.set(false);});
    }catch(Exception e){
      android.util.Log.e("RecruitAssist","OCR invocation failed",e);
      frame.recycle();busy.set(false);
    }
  }
  private boolean isBlankFrame(Bitmap frame){
    int black=0;
    for(int y=1;y<=10;y++)for(int x=1;x<=10;x++){
      int p=frame.getPixel(frame.getWidth()*x/11,frame.getHeight()*y/11);
      if(Color.red(p)+Color.green(p)+Color.blue(p)<45)black++;
    }
    return black>=98;
  }
  private void updateOverlayExclusions(){
    ArrayList<Rect> areas=new ArrayList<>();
    for(View view:new View[]{panel,gear,settings}){
      if(view==null||view.getVisibility()!=View.VISIBLE||view.getWidth()==0)continue;
      int[] xy=new int[2];view.getLocationOnScreen(xy);
      areas.add(new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight()));
    }
    overlayExclusions=areas.toArray(new Rect[0]);
  }
  private void recordTag(Map<String,Recognition> found,String raw,Rect box,
                         Bitmap frame,Rect[] exclusions) {
    if(box==null||box.width()<5||box.height()<5)return;
    String tag=RecruitmentEngine.normalizeOcr(raw);
    if(tag==null)return;
    for(Rect area:exclusions)if(Rect.intersects(area,box))return;
    TagBoundsDetector.Detection hit=TagBoundsDetector.detect(frame,box);
    // A label is not a button. Never draw an estimated rectangle around text.
    if(hit==null)return;
    found.putIfAbsent(tag,new Recognition(tag,new Rect(box),hit.tile));
  }
  private void processText(Text result,Bitmap frame,int epoch){
    LinkedHashMap<String,Recognition> found=new LinkedHashMap<>();
    Rect[] screenAreas=overlayExclusions;
    Rect[] exclusions=new Rect[screenAreas.length];
    float sx=frame.getWidth()/(float)Math.max(1,width);
    float sy=frame.getHeight()/(float)Math.max(1,height);
    for(int i=0;i<screenAreas.length;i++){
      Rect r=screenAreas[i];
      exclusions[i]=new Rect((int)(r.left*sx),(int)(r.top*sy),
        (int)Math.ceil(r.right*sx),(int)Math.ceil(r.bottom*sy));
    }
    for(Text.TextBlock block:result.getTextBlocks()){
      for(Text.Line line:block.getLines()){
        recordTag(found,line.getText(),line.getBoundingBox(),frame,exclusions);
        List<Text.Element> elements=line.getElements();
        for(int i=0;i<elements.size();i++){
          Text.Element item=elements.get(i);
          recordTag(found,item.getText(),item.getBoundingBox(),frame,exclusions);
          if(i+1<elements.size()){
            Text.Element next=elements.get(i+1);
            Rect a=item.getBoundingBox(),b=next.getBoundingBox();
            if(a!=null&&b!=null){
              Rect both=new Rect(a);both.union(b);
              recordTag(found,item.getText()+" "+next.getText(),both,frame,exclusions);
            }
          }
        }
      }
    }
    // Validate one regular 3-column / 2-row group, not scattered text matches.
    boolean validGrid=TagGrid.isRecruitmentGrid(new ArrayList<Recognition>(found.values())
      .stream().map(r->r.tile).collect(java.util.stream.Collectors.toList()));
    final int frameWidth=frame.getWidth(),frameHeight=frame.getHeight();
    int count=found.size();
    main.post(()->{
      if(paused||epoch!=scanEpoch)return;
      detectedCount=count;
      if(statusText!=null)statusText.setText("Global • OCR: "+count+"/5");
      // In-game OCR sometimes misreads one of the five labels.
      // Commit all five physical buttons together; partial OCR cannot replace a snapshot.
      if(count!=5||!validGrid){
        // OCR on MediaProjection can miss a frame because transparent
        // overlays or transitions occlude labels. Never clear after a short
        // run of misses while the game remains on the recruitment page.
        String screen=result.getText().toLowerCase(Locale.ROOT);
        boolean recruitmentUi=screen.contains("job tags")||
          screen.contains("recruitment time")||screen.contains("tap to refresh")||
          screen.contains("contacting hr")||screen.contains("refresh");
        boolean mainMenu=(screen.contains("terminal")&&screen.contains("operators"))||
          (screen.contains("missions")&&screen.contains("store"))||
          (screen.contains("squad")&&screen.contains("depot"));
        if(!recruitmentUi&&mainMenu){
          if(++missing>=3){
            inRecruitment=false;recognized.clear();recommendation=null;missing=0;
          }
        }else missing=0;
        if(statusText!=null)statusText.setText("Global • OCR: "+count+
          "/5"+(inRecruitment?" • сохранено":""));
        render();return;
      }
      missing=0;
      HashSet<String> previous=new HashSet<>();
      for(Recognition r:recognized)previous.add(r.tag);
      if(!previous.equals(found.keySet()))selectedVariant=null;
      inRecruitment=true;recognized.clear();
      recognized.addAll(found.values());
      recognitionWidth=frameWidth;recognitionHeight=frameHeight;
      chooseRecommendation();
    });
  }
  private void chooseRecommendation(){
    ArrayList<String> tags=new ArrayList<>();
    for(Recognition r:recognized)tags.add(r.tag);
    variants=RecruitmentEngine.combinations(tags,operators,prefs.getBoolean("chance",false),9);
    recommendation=variants.isEmpty()?null:variants.get(0);
    if(selectedVariant!=null){
      boolean retained=false;
      for(RecruitmentEngine.Result choice:variants)
        if(variantKey(choice).equals(selectedVariant)){recommendation=choice;retained=true;break;}
      if(!retained)selectedVariant=null;
    }
    render();
  }
  private void render(){
    if(markers!=null)markers.invalidate();
    if(panelText!=null){
      if(paused)panelText.setText("Анализ на паузе");
      else if(!inRecruitment)panelText.setText("Ожидаю рекрутинг • "+detectedCount+"/5 тегов");
      else if(recommendation==null)panelText.setText("Тегов: "+detectedCount+
        "/5\nНет проверенной комбинации или базы");
      else panelText.setText("Выбрать: "+variantLabel(recommendation)+
        "\n"+recommendation.raritySummary());
    }
    if(listToggle!=null&&operatorScroll!=null&&operatorListText!=null){
      int count=recommendation==null?0:recommendation.operators.size();
      listToggle.setVisibility(count>0?View.VISIBLE:View.GONE);
      listToggle.setText("Операторы ("+count+") "+(operatorListExpanded?"▴":"▾"));
      operatorScroll.setVisibility(count>0&&operatorListExpanded?View.VISIBLE:View.GONE);
      if(count>0){
        StringBuilder names=new StringBuilder();
        for(RecruitmentEngine.Operator op:recommendation.operators)
          names.append(op.rarity).append("★ ").append(op.name).append(" — ")
            .append(count==1?"100%*":"шанс неизвестен").append("\n");
        operatorListText.setText(names.toString().trim());
      }else operatorListText.setText("");
    }
    if(variantsToggle!=null){
      boolean available=inRecruitment&&!variants.isEmpty()&&recommendation!=null;
      variantsToggle.setVisibility(available?View.VISIBLE:View.GONE);
      variantsToggle.setText("Все варианты ("+variants.size()+") "+(variantsExpanded?"▴":"▾"));
      variantsScroll.setVisibility(available&&variantsExpanded?View.VISIBLE:View.GONE);
      if(available&&variantsExpanded)renderVariants();
    }
    if(panel!=null){
      panel.setVisibility(projection!=null&&!paused&&
        prefs.getBoolean("panel",true)?View.VISIBLE:View.GONE);
      panel.post(this::updateOverlayExclusions);
    }
  }
  private static String variantKey(RecruitmentEngine.Result r){
    return String.join("|",r.tags);
  }
  private static String variantLabel(RecruitmentEngine.Result r){
    return r.tags.isEmpty()?"Без тегов":String.join(" + ",r.tags);
  }
  private void renderVariants(){
    StringBuilder signature=new StringBuilder(variantKey(recommendation));
    for(RecruitmentEngine.Result r:variants)signature.append(';').append(variantKey(r))
      .append(':').append(r.description());
    if(lastVariantsSignature.equals(signature.toString()))return;
    lastVariantsSignature=signature.toString();
    variantsBox.removeAllViews();
    for(RecruitmentEngine.Result choice:variants){
      TextView row=new TextView(this);
      row.setText(variantLabel(choice)+"\n"+choice.guaranteed+"–"+choice.possible+
        "★ • "+choice.operators.size()+" операторов");
      row.setTextColor(variantKey(choice).equals(variantKey(recommendation))?UiStyle.ACCENT:UiStyle.TEXT);
      row.setTextSize(11);row.setPadding(dp(3),dp(8),dp(3),dp(8));
      variantsBox.addView(row,new LinearLayout.LayoutParams(-1,-2));
      row.setOnClickListener(v->{
        recommendation=choice;selectedVariant=variantKey(choice);render();
      });
    }
  }
  private int dp(int px){return (int)(px*getResources().getDisplayMetrics().density+0.5f);}
  private WindowManager.LayoutParams layout(int w,int h,int flags){
    return new WindowManager.LayoutParams(w,h,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      flags,PixelFormat.TRANSLUCENT);
  }
  private void createOverlays(){
    if(gear!=null)return;
    DisplayMetrics metrics=new DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(metrics);
    width=metrics.widthPixels;height=metrics.heightPixels;
    markers=new Markers(this);
    WindowManager.LayoutParams pass=layout(-1,-1,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
    pass.alpha=.70f;
    wm.addView(markers,pass);

    LinearLayout controls=new LinearLayout(this);
    controls.setOrientation(LinearLayout.HORIZONTAL);
    controls.setBackground(UiStyle.rounded(this,0xF01D2B36,14));
    FloatingControlButton g=new FloatingControlButton(this,FloatingControlButton.SETTINGS);
    playPause=new FloatingControlButton(this,FloatingControlButton.PLAY_PAUSE);
    // Play/Pause on the left, movable settings gear on the right.
    controls.addView(playPause,new LinearLayout.LayoutParams(dp(52),dp(52)));
    controls.addView(g,new LinearLayout.LayoutParams(dp(52),dp(52)));
    gear=controls;
    gearParams=layout(dp(104),dp(52),WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    gearParams.gravity=Gravity.LEFT|Gravity.TOP;
    gearParams.x=dp(8);gearParams.y=height/2;
    wm.addView(gear,gearParams);
    playPause.setOnClickListener(v->togglePlayPause());
    g.setOnTouchListener(new View.OnTouchListener(){
      float startX,startY,originX,originY;boolean dragged=false;
      public boolean onTouch(View v,MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){
          startX=e.getRawX();startY=e.getRawY();
          originX=gearParams.x;originY=gearParams.y;dragged=false;return true;
        }
        if(e.getAction()==MotionEvent.ACTION_MOVE){
          float dx=e.getRawX()-startX,dy=e.getRawY()-startY;
          if(Math.abs(dx)+Math.abs(dy)>dp(10))dragged=true;
          if(dragged){
            gearParams.x=Math.max(0,Math.min(width-dp(104),(int)(originX+dx)));
            gearParams.y=Math.max(0,Math.min(height-dp(70),(int)(originY+dy)));
            wm.updateViewLayout(gear,gearParams);
          }
          return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP){if(!dragged)toggleSettings();return true;}
        return false;
      }
    });
    LinearLayout box=new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(8),dp(4),dp(8),dp(8));
    box.setBackground(UiStyle.rounded(this,0xF01D2B36,14));
    // Drag using the narrow header; the operator list stays independently scrollable.
    TextView drag=new TextView(this);
    drag.setText("⠿  Рекомендация");
    drag.setTextColor(UiStyle.MUTED);
    drag.setTextSize(12);
    drag.setGravity(Gravity.CENTER_VERTICAL);
    drag.setPadding(dp(3),dp(4),dp(3),dp(6));
    box.addView(drag,new LinearLayout.LayoutParams(-1,dp(32)));
    statusText=new TextView(this);statusText.setTextColor(UiStyle.MUTED);
    statusText.setTextSize(10);statusText.setText("База Global");
    box.addView(statusText);
    panelText=new TextView(this);panelText.setTextColor(Color.WHITE);
    panelText.setTextSize(12);panelText.setPadding(0,dp(4),0,dp(3));
    box.addView(panelText);
    listToggle=new TextView(this);listToggle.setTextColor(UiStyle.ACCENT);
    listToggle.setTextSize(12);listToggle.setPadding(dp(2),dp(7),dp(2),dp(7));
    box.addView(listToggle);
    operatorListText=new TextView(this);
    operatorListText.setTextColor(UiStyle.TEXT);operatorListText.setTextSize(11);
    operatorListText.setPadding(dp(2),dp(3),dp(3),dp(3));
    operatorScroll=new ScrollView(this){
      @Override protected void onMeasure(int w,int h){
        int cap=Math.min(dp(150),Math.max(dp(40),height/2));
        if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)
          cap=Math.min(cap,MeasureSpec.getSize(h));
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
      }
    };
    operatorScroll.addView(operatorListText);
    operatorScroll.setFillViewport(false);
    box.addView(operatorScroll,new LinearLayout.LayoutParams(-1,-2));
    listToggle.setOnClickListener(v->{
      operatorListExpanded=!operatorListExpanded;
      if(operatorListExpanded)variantsExpanded=false;
      render();
    });
    variantsToggle=new TextView(this);variantsToggle.setTextColor(UiStyle.ACCENT);
    variantsToggle.setTextSize(12);variantsToggle.setPadding(dp(2),dp(7),dp(2),dp(7));
    box.addView(variantsToggle);
    variantsBox=new LinearLayout(this);variantsBox.setOrientation(LinearLayout.VERTICAL);
    variantsScroll=new ScrollView(this){
      @Override protected void onMeasure(int w,int h){
        int cap=Math.min(dp(110),Math.max(dp(35),height/3));
        if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)cap=Math.min(cap,MeasureSpec.getSize(h));
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
      }
    };
    variantsScroll.addView(variantsBox);box.addView(variantsScroll,new LinearLayout.LayoutParams(-1,-2));
    variantsToggle.setOnClickListener(v->{
      variantsExpanded=!variantsExpanded;
      if(variantsExpanded)operatorListExpanded=false;
      render();
    });
    panel=box;
    panelParams=layout(dp(192),-2,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    panelParams.gravity=Gravity.LEFT|Gravity.TOP;
    panelParams.x=dp(3);
    panelParams.y=Math.max(dp(70),height/2-dp(120));
    if(prefs.contains("panel_frac_x")&&prefs.contains("panel_frac_y")){
      panelParams.x=Math.round(prefs.getFloat("panel_frac_x",0f)*width);
      panelParams.y=Math.round(prefs.getFloat("panel_frac_y",.2f)*height);
    }
    panelParams.x=Math.max(0,Math.min(Math.max(0,width-dp(192)),panelParams.x));
    panelParams.y=Math.max(0,Math.min(Math.max(0,height-dp(60)),panelParams.y));
    wm.addView(panel,panelParams);
    box.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
      int maxY=Math.max(0,height-v.getHeight());
      if(panelParams.y>maxY){panelParams.y=maxY;wm.updateViewLayout(panel,panelParams);}
      updateOverlayExclusions();
    });
    drag.setOnTouchListener(new View.OnTouchListener(){
      float startX,startY;int fromX,fromY;boolean dragging;
      @Override public boolean onTouch(View v,MotionEvent event){
        switch(event.getActionMasked()){
          case MotionEvent.ACTION_DOWN:
            startX=event.getRawX();startY=event.getRawY();
            fromX=panelParams.x;fromY=panelParams.y;dragging=false;return true;
          case MotionEvent.ACTION_MOVE:
            float dx=event.getRawX()-startX,dy=event.getRawY()-startY;
            if(Math.abs(dx)+Math.abs(dy)>dp(5))dragging=true;
            if(dragging&&panel!=null){
              panelParams.x=Math.max(0,Math.min(Math.max(0,width-dp(192)),fromX+(int)dx));
              panelParams.y=Math.max(0,Math.min(Math.max(0,height-dp(60)),fromY+(int)dy));
              wm.updateViewLayout(panel,panelParams);
              panel.post(ScreenService.this::updateOverlayExclusions);
            }
            return true;
          case MotionEvent.ACTION_UP:
            if(dragging){
              prefs.edit().putFloat("panel_frac_x",panelParams.x/(float)Math.max(1,width))
                .putFloat("panel_frac_y",panelParams.y/(float)Math.max(1,height)).apply();
            }
            return true;
          case MotionEvent.ACTION_CANCEL:return true;
          default:return false;
        }
      }
    });
    updatePlayButton();render();
  }
  private void toggleSettings(){
    if(settings!=null){wm.removeView(settings);settings=null;updateOverlayExclusions();return;}
    LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(dp(12),dp(8),dp(12),dp(8));box.setBackground(UiStyle.rounded(this,UiStyle.SURFACE,16));
    LinearLayout heading=new LinearLayout(this);
    heading.setOrientation(LinearLayout.HORIZONTAL);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView title=new TextView(this);
    title.setText("Настройки");title.setTextSize(18);title.setTextColor(UiStyle.TEXT);
    title.setTypeface(null,android.graphics.Typeface.BOLD);
    heading.addView(title,new LinearLayout.LayoutParams(0,dp(44),1f));
    Button close=new Button(this);close.setText("✕");UiStyle.button(close,false);
    close.setTextSize(18);close.setTextColor(UiStyle.MUTED);
    heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
    box.addView(heading);
    close.setOnClickListener(v->toggleSettings());
    Switch sw=new Switch(this);sw.setText("Приоритет: максимальная редкость");UiStyle.toggle(sw);
    sw.setChecked(prefs.getBoolean("chance",false));box.addView(sw);
    sw.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("chance",on).apply();selectedVariant=null;chooseRecommendation();});
    Switch show=new Switch(this);show.setText("Панель операторов");UiStyle.toggle(show);
    show.setChecked(prefs.getBoolean("panel",true));box.addView(show);
    show.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("panel",on).apply();render();});
    box.addView(new HighlightColorControl(this,prefs,
      ()->{if(markers!=null)markers.invalidate();}));
    settingSlider(box,"Непрозрачность подсветки","fillAlpha",10,70,35);
    Button off=new Button(this);off.setText("Выключить помощник");UiStyle.button(off,false);box.addView(off);
    off.setOnClickListener(v->stopSelf());
    // The single close control is the compact X at the top right.
    ScrollView scroll=new ScrollView(this);scroll.addView(box);settings=scroll;
    WindowManager.LayoutParams p=layout(dp(285),Math.min(dp(520),Math.max(dp(160),height-dp(80))),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
    p.gravity=Gravity.CENTER;p.alpha=.96f;wm.addView(settings,p);
    settings.post(this::updateOverlayExclusions);
  }
  private void settingSlider(LinearLayout box,String label,String key,int min,int max,int def){
    TextView t=new TextView(this);t.setTextColor(Color.WHITE);
    t.setText(label);box.addView(t);
    SeekBar b=new SeekBar(this);b.setMax(max-min);b.setProgress(prefs.getInt(key,def)-min);UiStyle.slider(b);box.addView(b);
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
      if(paused||!inRecruitment||recommendation==null)return;
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(Color.argb((int)(255*prefs.getInt("fillAlpha",35)/100f),
        prefs.getInt("red",0),prefs.getInt("green",255),prefs.getInt("blue",90)));
      // Tile bounds are taken from image edges when available. The OCR
      // text rectangle is only a fallback, not the drawn highlight geometry.
      // Capture uses physical display pixels; overlay may start below an inset.
      // Map via the display, then subtract the actual overlay origin. Scaling
      // directly by the smaller overlay view size shifts every rectangle.
      int[] origin=new int[2];getLocationOnScreen(origin);
      float sx=width/(float)Math.max(1,recognitionWidth);
      float sy=height/(float)Math.max(1,recognitionHeight);
      for(Recognition r:recognized){
        if(!recommendation.tags.contains(r.tag))continue;
        Rect tile=r.tile;
        RectF rect=new RectF(tile.left*sx-origin[0],tile.top*sy-origin[1],
          tile.right*sx-origin[0],tile.bottom*sy-origin[1]);
        canvas.drawRect(rect,paint);
      }
    }
  }
  @Override public void onConfigurationChanged(Configuration c){
    super.onConfigurationChanged(c);
    refreshDisplaySize();
  }
  /** Keep one MediaProjection session, resizing its virtual display/surface. */
  private void refreshDisplaySize(){
    DisplayMetrics dm=new DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(dm);
    int nextW=dm.widthPixels,nextH=dm.heightPixels;
    if(nextW==width&&nextH==height)return;
    width=nextW;height=nextH;scanEpoch++;
    captureWidth=nextW;captureHeight=nextH;
    if(virtualDisplay!=null&&workerHandler!=null){
      try{
        ImageReader next=ImageReader.newInstance(nextW,nextH,PixelFormat.RGBA_8888,2);
        next.setOnImageAvailableListener(this::consumeFrame,workerHandler);
        virtualDisplay.resize(nextW,nextH,dm.densityDpi);
        virtualDisplay.setSurface(next.getSurface());
        ImageReader old=reader;
        reader=next;
        if(old!=null){
          old.setOnImageAvailableListener(null,null);
          old.close();
        }
      }catch(Exception e){
        android.util.Log.e("RecruitAssist","Capture resize failed",e);
        stopSelf();return;
      }
    }
    lastScan=0;missing=0;detectedCount=0;
    inRecruitment=false;recognized.clear();recommendation=null;
    if(gear!=null){
      gearParams.x=Math.min(gearParams.x,Math.max(0,width-dp(104)));
      gearParams.y=Math.min(gearParams.y,Math.max(0,height-dp(70)));
      wm.updateViewLayout(gear,gearParams);
    }
    if(panel!=null&&panelParams!=null){
      panelParams.x=Math.max(0,Math.min(width-dp(192),
        Math.round(prefs.getFloat("panel_frac_x",panelParams.x/(float)Math.max(1,width))*width)));
      panelParams.y=Math.max(0,Math.min(height-dp(60),
        Math.round(prefs.getFloat("panel_frac_y",panelParams.y/(float)Math.max(1,height))*height)));
      wm.updateViewLayout(panel,panelParams);
    }
    render();
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

