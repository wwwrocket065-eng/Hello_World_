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
  private volatile boolean paused=true;
  private boolean inRecruitment=false;
  private WindowManager.LayoutParams gearParams,panelParams;
  private final List<Recognition> recognized=new ArrayList<>();
  private List<RecruitmentEngine.Operator> operators=Collections.emptyList();
  private RecruitmentEngine.Result recommendation;
  private TextView panelText,statusText,listToggle;
  private LinearLayout operatorRows;
  private OperatorPortraits portraits;
  private String operatorRowsKey="";
  private final LinkedHashMap<String,Integer> targets=new LinkedHashMap<>();
  private final Map<String,RecruitmentEngine.Result> targetPlans=new LinkedHashMap<>();
  private int nextTargetColor;
  private TextView goalText;
  private volatile View goalMenu;
  private String goalDisplayKey="";
  private List<RecruitmentEngine.Operator> reachableOperators=Collections.emptyList();
  private ScrollView operatorScroll;
  private boolean operatorListExpanded=false;
  private SharedPreferences prefs;
  private long lastScan=0; private int missing=0,width,height;
  private volatile int captureWidth=0,captureHeight=0;
  private volatile int detectedCount=0;
  private volatile boolean resizePending=false;
  // Screen-space rectangles of our own text-bearing windows. Never OCR our panel.
  private volatile Rect[] overlayExclusions=new Rect[0];
  private int recognitionWidth,recognitionHeight;
  private final java.util.concurrent.atomic.AtomicInteger scanEpoch=new java.util.concurrent.atomic.AtomicInteger();
  private volatile Rect[] knownTiles=new Rect[0];
  private volatile boolean screenPresent=false;
  private long lastProbe;
  private volatile long overlaySettlingUntil;
  private int probeMisses,probeHits;
  private volatile int recruitMinutes=-1;
  private boolean controlsSeen;
  private volatile long suppressUntil;
  private static final class Recognition {
    String tag; Rect bounds; Rect tile; Recognition(String t,Rect r,Rect tile){tag=t;bounds=r;this.tile=tile;}
  }
  @Override public void onCreate(){
    super.onCreate();prefs=getSharedPreferences("settings",MODE_PRIVATE);
    portraits=new OperatorPortraits(this);
    nextTargetColor=prefs.getInt("next_goal_color",0);
    try{
      org.json.JSONArray saved=new org.json.JSONArray(prefs.getString("goals","[]"));
      for(int i=0;i<saved.length();i++){
        org.json.JSONObject item=saved.getJSONObject(i);
        targets.put(item.getString("name"),item.getInt("color"));
      }
    }catch(Exception ignored){}
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
    paused=!paused;scanEpoch.incrementAndGet();
    lastScan=0;
    if(paused){
      inRecruitment=false;
      recognized.clear();recommendation=null;knownTiles=new Rect[0];screenPresent=false;probeHits=probeMisses=0;
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
    long now=SystemClock.elapsedRealtime();
    if(paused||goalMenu!=null){image.close();return;}
    // Sparse checks read the Image plane directly: no bitmap copy and no OCR.
    // They keep running while an OCR task is in flight.
    if(now<suppressUntil){image.close();return;}
    if(knownTiles.length==5&&now>=overlaySettlingUntil&&now-lastProbe>=16){
      lastProbe=now;
      Image.Plane probePlane=image.getPlanes()[0];
      ByteBuffer pixels=probePlane.getBuffer();
      int base=pixels.position(),row=probePlane.getRowStride(),step=probePlane.getPixelStride();
      Rect[] exclusions=captureExclusions(image.getWidth(),image.getHeight());
      RecruitmentScreenProbe.Pixels reader=(x,y)->{
        int offset=base+y*row+x*step;
        return Color.rgb(pixels.get(offset)&255,pixels.get(offset+1)&255,pixels.get(offset+2)&255);
      };
      int evidence=RecruitmentScreenProbe.screenEvidence(reader,knownTiles,image.getWidth(),image.getHeight(),exclusions);
      int controls=RecruitmentScreenProbe.controlEvidence(reader,knownTiles,image.getWidth(),image.getHeight(),exclusions);
      boolean controlExit=controlsSeen&&controls==0;
      if(controls==1)controlsSeen=true;
      if(controlExit)evidence=0;
      if(evidence==1){probeMisses=0;probeHits++;}
      else if(evidence==0){probeHits=0;probeMisses++;}
      else{probeHits=probeMisses=0;}
      if(screenPresent&&(controlExit||probeMisses>=1)){
        screenPresent=false;scanEpoch.incrementAndGet();lastScan=0;
        if(controlExit)suppressUntil=now+300;
        main.post(()->{
          if(screenPresent)return;
          inRecruitment=false;recognized.clear();recommendation=null;detectedCount=0;recruitMinutes=-1;controlsSeen=false;
          render();
        });
      }else if(!screenPresent&&probeHits>=2){
        screenPresent=true;scanEpoch.incrementAndGet();lastScan=0;
      }
    }
    if(now<suppressUntil){image.close();return;}
    long interval=screenPresent?250:knownTiles.length==0?500:1000;
    if(now-lastScan<interval||!busy.compareAndSet(false,true)){
      image.close();return;
    }
    lastScan=now;
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
    final int epoch=scanEpoch.get();
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
    final TagOcrInput input=TagOcrInput.prepare(frame,screenPresent?knownTiles:new Rect[0]);
    final Bitmap ocrFrame=input.bitmap;
    try{
      recognizer.process(InputImage.fromBitmap(ocrFrame,0))
        .addOnSuccessListener(text->processText(text,frame,epoch,input))
        .addOnFailureListener(e->{
          android.util.Log.w("RecruitAssist","OCR failed",e);
          if(statusText!=null)statusText.setText("Ошибка распознавания текста");
        })
        .addOnCompleteListener(task->{if(ocrFrame!=frame)ocrFrame.recycle();frame.recycle();busy.set(false);});
    }catch(Exception e){
      android.util.Log.e("RecruitAssist","OCR invocation failed",e);
      if(ocrFrame!=frame)ocrFrame.recycle();frame.recycle();busy.set(false);
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
    for(View view:new View[]{panel,gear,settings,goalMenu}){
      if(view==null||view.getVisibility()!=View.VISIBLE||view.getWidth()==0)continue;
      int[] xy=new int[2];view.getLocationOnScreen(xy);
      areas.add(new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight()));
    }
    Rect[] next=areas.toArray(new Rect[0]);
    if(!java.util.Arrays.equals(overlayExclusions,next))overlaySettlingUntil=SystemClock.elapsedRealtime()+100;
    overlayExclusions=next;
  }
  private Rect[] captureExclusions(int frameWidth,int frameHeight){
    Rect[] areas=overlayExclusions,result=new Rect[areas.length];
    float sx=frameWidth/(float)Math.max(1,width),sy=frameHeight/(float)Math.max(1,height);
    for(int i=0;i<areas.length;i++){
      Rect r=areas[i];
      result[i]=new Rect((int)(r.left*sx),(int)(r.top*sy),(int)Math.ceil(r.right*sx),(int)Math.ceil(r.bottom*sy));
    }
    return result;
  }
  private void recordTag(Map<String,Recognition> found,String raw,Rect box,
                         Bitmap frame,Rect[] exclusions,TagOcrInput input) {
    if(box==null||box.width()<5||box.height()<5||input.clockBox(box))return;
    String tag=RecruitmentEngine.normalizeOcr(raw);
    if(tag==null)return;
    Rect physical=input.tileFor(box),mapped=input.map(box);
    for(Rect area:exclusions)if(Rect.intersects(area,mapped))return;
    if(physical!=null){
      // The fast pixel probe independently confirmed this physical button.
      // A colour change cannot force us back to text-height edge guesses.
      if(RecruitmentScreenProbe.tilePresent(frame::getPixel,physical,frame.getWidth(),frame.getHeight()))
        found.putIfAbsent(tag,new Recognition(tag,new Rect(mapped),new Rect(physical)));
      return;
    }
    TagBoundsDetector.Detection hit=TagBoundsDetector.detect(frame,mapped);
    if(hit!=null)found.putIfAbsent(tag,new Recognition(tag,new Rect(mapped),hit.tile));
  }
  private void processText(Text result,Bitmap frame,int epoch,TagOcrInput input){
    LinkedHashMap<String,Recognition> found=new LinkedHashMap<>();
    Rect[] exclusions=captureExclusions(frame.getWidth(),frame.getHeight());
    for(Text.TextBlock block:result.getTextBlocks()){
      for(Text.Line line:block.getLines()){
        recordTag(found,line.getText(),line.getBoundingBox(),frame,exclusions,input);
        List<Text.Element> elements=line.getElements();
        for(int i=0;i<elements.size();i++){
          Text.Element item=elements.get(i);
          recordTag(found,item.getText(),item.getBoundingBox(),frame,exclusions,input);
          if(i+1<elements.size()){
            Text.Element next=elements.get(i+1);
            Rect a=item.getBoundingBox(),b=next.getBoundingBox();
            if(a!=null&&b!=null){
              Rect both=new Rect(a);both.union(b);
              recordTag(found,item.getText()+" "+next.getText(),both,frame,exclusions,input);
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
    Rect[] timeTiles=found.size()==5?found.values().stream().map(r->r.tile).toArray(Rect[]::new):knownTiles;
    Rect timeRegion=RecruitmentClock.region(timeTiles,frameWidth,frameHeight);
    int clockValue=-1;
    for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){
      Rect box=line.getBoundingBox();
      if(box==null)continue;
      if(input.clockBox(box)||(timeRegion!=null&&box.centerX()>=timeRegion.left&&box.centerX()<=timeRegion.right&&box.centerY()>=timeRegion.top&&box.centerY()<=timeRegion.bottom)){
        int value=RecruitmentClock.parse(line.getText());if(value>=0)clockValue=value;
      }
    }
    if(clockValue<0){
      ArrayList<Text.Element> digits=new ArrayList<>();
      for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines())for(Text.Element element:line.getElements()){
        Rect box=element.getBoundingBox();if(box==null)continue;
        boolean inside=input.clockBox(box)||(timeRegion!=null&&box.centerX()>=timeRegion.left&&box.centerX()<=timeRegion.right&&box.centerY()>=timeRegion.top&&box.centerY()<=timeRegion.bottom);
        if(inside&&element.getText().matches("[0-9Oo:：]+"))digits.add(element);
      }
      digits.sort(Comparator.comparingInt(e->e.getBoundingBox().centerX()));
      StringBuilder joined=new StringBuilder();for(Text.Element e:digits)joined.append(e.getText());
      clockValue=RecruitmentClock.parse(joined.toString());
    }
    final int detectedMinutes=clockValue;
    main.post(()->{
      if(paused||epoch!=scanEpoch.get())return;
      boolean timeChanged=detectedMinutes>=0&&detectedMinutes!=recruitMinutes;
      if(detectedMinutes>=0)recruitMinutes=detectedMinutes;
      detectedCount=count;
      if(statusText!=null)statusText.setText("Global • OCR: "+count+"/5");
      // In-game OCR sometimes misreads one of the five labels.
      // Commit all five physical buttons together; partial OCR cannot replace a snapshot.
      if(count!=5||!validGrid){
        // OCR on MediaProjection can miss a frame because transparent
        // overlays or transitions occlude labels. Never clear after a short
        // run of misses while the game remains on the recruitment page.
        if(screenPresent&&inRecruitment){
          HashSet<String> previous=new HashSet<>();
          for(Recognition r:recognized)previous.add(r.tag);
          if(!previous.containsAll(found.keySet()))recommendation=null;
        }
        if(statusText!=null)statusText.setText("Global • OCR: "+count+"/5");
        if(timeChanged&&inRecruitment)chooseRecommendation();
        render();return;
      }
      missing=0;

      inRecruitment=true;recognized.clear();
      recognized.addAll(found.values());
      recognitionWidth=frameWidth;recognitionHeight=frameHeight;
      knownTiles=found.values().stream().map(r->new Rect(r.tile)).toArray(Rect[]::new);
      screenPresent=true;probeMisses=0;probeHits=0;
      chooseRecommendation();
    });
  }
  private void chooseRecommendation(){
    ArrayList<String> tags=new ArrayList<>();
    for(Recognition r:recognized)tags.add(r.tag);
    List<RecruitmentEngine.Result> options=recruitMinutes<60?Collections.emptyList():RecruitmentEngine.combinationsAtMinutes(tags,operators,true,recruitMinutes);
    reachableOperators=RecruitmentEngine.reachable(options);
    recommendation=options.isEmpty()?null:options.get(0);
    targetPlans.clear();
    for(String name:targets.keySet())targetPlans.put(name,RecruitmentEngine.forTarget(options,name));
    if(targets.size()==1)recommendation=targetPlans.get(targets.keySet().iterator().next());
    render();
  }
  private void render(){
    if(markers!=null)markers.invalidate();
    if(panelText!=null){
      if(paused)panelText.setText("Анализ на паузе");
      else if(!inRecruitment)panelText.setText("Ожидаю рекрутинг • "+detectedCount+"/5 тегов");
      else if(recruitMinutes<60)panelText.setText("Время не распознано");
      else if(recommendation==null)panelText.setText("Тегов: "+detectedCount+
        "/5\nНет проверенной комбинации или базы");
      else panelText.setText(String.format(Locale.ROOT,"%d:%02d",recruitMinutes/60,recruitMinutes%60)+
        " ("+recommendation.guaranteed+"★"+
        (recommendation.guaranteed==recommendation.possible?"":"–"+recommendation.possible+"★")+") · "+variantLabel(recommendation));
    }
    if(paused||!screenPresent)closeGoalMenu();
    if(goalText!=null){
      String key=targets.toString();
      for(String name:targets.keySet())key+=targetPlans.get(name)==null?"0":"1";
      if(!key.equals(goalDisplayKey)){
        goalDisplayKey=key;
        android.text.SpannableStringBuilder goals=new android.text.SpannableStringBuilder("Цель: ");
        if(targets.isEmpty())goals.append("Макс цель");
        else for(Map.Entry<String,Integer> target:targets.entrySet()){
          if(goals.length()>6)goals.append("\n");
          String name=target.getKey();
          int color=targets.size()>1?target.getValue():UiStyle.TEXT;
          int start=goals.length();
          goals.append(targets.size()>1?"● ":"").append(name);
          if(targetPlans.get(name)==null)goals.append(" — нет тегов");
          goals.setSpan(new android.text.style.ClickableSpan(){
            @Override public void onClick(View view){openGoalMenu(name);}
            @Override public void updateDrawState(android.text.TextPaint paint){paint.setColor(color);paint.setUnderlineText(false);}
          },start,goals.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        goalText.setText(goals);
      }
    }
    if(listToggle!=null&&operatorScroll!=null&&operatorRows!=null){
      boolean any=!operators.isEmpty();
      listToggle.setVisibility(any?View.VISIBLE:View.GONE);
      listToggle.setText("Выбор цели "+(operatorListExpanded?"▴":"▾"));
      operatorScroll.setVisibility(any&&operatorListExpanded?View.VISIBLE:View.GONE);
      if(any&&operatorListExpanded)renderOperatorRows();
    }
    if(panel!=null){
      panel.setVisibility(projection!=null&&!paused&&screenPresent&&inRecruitment&&
        prefs.getBoolean("panel",true)?View.VISIBLE:View.GONE);
      panel.post(this::updateOverlayExclusions);
    }
  }
  private void renderOperatorRows(){
    HashSet<String> available=new HashSet<>();
    for(RecruitmentEngine.Operator op:reachableOperators)available.add(op.name);
    boolean onlyCurrent=prefs.getBoolean("only_current_tags",false);
    ArrayList<RecruitmentEngine.Operator> all=new ArrayList<>(onlyCurrent?reachableOperators:operators);
    all.sort((a,b)->{int c=Integer.compare(b.rarity,a.rarity);return c!=0?c:a.name.compareToIgnoreCase(b.name);});
    StringBuilder key=new StringBuilder(targets.toString()).append(onlyCurrent).append(available.toString());
    for(RecruitmentEngine.Operator op:all)key.append('|').append(op.rarity).append(':').append(op.name);
    if(operatorRowsKey.equals(key.toString()))return;
    operatorRowsKey=key.toString();
    int scrollY=operatorScroll.getScrollY();
    operatorRows.removeAllViews();
    for(RecruitmentEngine.Operator op:all){
      LinearLayout row=new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
      int color=targets.containsKey(op.name)?0xFF69E68B:available.contains(op.name)?0xFFFFD866:UiStyle.MUTED;
      TextView rarity=new TextView(this);rarity.setText(op.rarity+"★");
      rarity.setTextColor(color);rarity.setTextSize(11);rarity.setIncludeFontPadding(false);
      row.addView(rarity,new LinearLayout.LayoutParams(dp(22),-2));
      ImageView portrait=new ImageView(this);portrait.setScaleType(ImageView.ScaleType.CENTER_CROP);
      portrait.setBackground(UiStyle.rounded(this,0xFF344653,2));portrait.setClipToOutline(true);
      LinearLayout.LayoutParams imageParams=new LinearLayout.LayoutParams(dp(20),dp(20));
      imageParams.setMargins(0,0,dp(4),0);row.addView(portrait,imageParams);
      portraits.bind(portrait,op.name);
      TextView name=new TextView(this);name.setText(op.name);name.setTextColor(color);
      name.setTextSize(11);name.setIncludeFontPadding(false);name.setSingleLine(true);
      name.setEllipsize(android.text.TextUtils.TruncateAt.END);
      row.addView(name,new LinearLayout.LayoutParams(0,-2,1f));
      operatorRows.addView(row,new LinearLayout.LayoutParams(-1,dp(22)));
      row.setOnClickListener(v->{
        if(targets.containsKey(op.name))targets.remove(op.name);
        else targets.put(op.name,Color.HSVToColor(new float[]{(nextTargetColor++*137.508f+15)%360,.68f,1f}));
        saveGoals();
        chooseRecommendation();
      });
    }
    operatorScroll.post(()->operatorScroll.scrollTo(0,scrollY));
  }
  private void saveGoals(){
    org.json.JSONArray saved=new org.json.JSONArray();
    for(Map.Entry<String,Integer> target:targets.entrySet()){
      org.json.JSONObject item=new org.json.JSONObject();
      try{item.put("name",target.getKey());item.put("color",target.getValue());saved.put(item);}catch(Exception ignored){}
    }
    prefs.edit().putString("goals",saved.toString()).putInt("next_goal_color",nextTargetColor).apply();
  }
  private void closeGoalMenu(){
    if(goalMenu!=null){wm.removeView(goalMenu);goalMenu=null;scanEpoch.incrementAndGet();lastScan=0;lastProbe=0;updateOverlayExclusions();}
  }
  private void openGoalMenu(String name){
    closeGoalMenu();
    FrameLayout shade=new FrameLayout(this);shade.setBackgroundColor(0x66000000);
    shade.setOnClickListener(v->closeGoalMenu());
    shade.setFocusableInTouchMode(true);
    shade.setOnKeyListener((v,key,event)->{
      if(key!=KeyEvent.KEYCODE_BACK)return false;
      if(event.getAction()==KeyEvent.ACTION_UP)closeGoalMenu();
      return true;
    });
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
    card.setPadding(dp(12),dp(8),dp(12),dp(12));
    card.setBackground(UiStyle.rounded(this,UiStyle.SURFACE,16));card.setOnClickListener(v->{});
    LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView title=new TextView(this);title.setText(name);title.setTextColor(UiStyle.TEXT);title.setTextSize(16);
    heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
    Button close=new Button(this);close.setText("✕");UiStyle.button(close,false);
    heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));card.addView(heading);
    close.setOnClickListener(v->closeGoalMenu());
    Button remove=new Button(this);remove.setText("Удалить "+name+" из целей");UiStyle.button(remove,false);card.addView(remove);
    remove.setOnClickListener(v->{targets.remove(name);saveGoals();closeGoalMenu();chooseRecommendation();});
    Button clear=new Button(this);clear.setText("Очистить все цели");UiStyle.button(clear,false);card.addView(clear);
    clear.setOnClickListener(v->{targets.clear();saveGoals();closeGoalMenu();chooseRecommendation();});
    FrameLayout.LayoutParams center=new FrameLayout.LayoutParams(Math.min(dp(290),width-dp(24)),-2,Gravity.CENTER);
    shade.addView(card,center);scanEpoch.incrementAndGet();goalMenu=shade;
    wm.addView(goalMenu,layout(-1,-1,WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN));
    shade.requestFocus();
    goalMenu.post(this::updateOverlayExclusions);
  }
  private static String variantLabel(RecruitmentEngine.Result r){
    return r.tags.isEmpty()?"Без тегов":String.join(" + ",r.tags);
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
    gear.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateOverlayExclusions());
    gear.post(this::updateOverlayExclusions);
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
            overlaySettlingUntil=SystemClock.elapsedRealtime()+100;
            wm.updateViewLayout(gear,gearParams);
            gear.post(ScreenService.this::updateOverlayExclusions);
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
    goalText=new TextView(this);goalText.setTextColor(UiStyle.ACCENT);goalText.setTextSize(12);
    goalText.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());goalText.setHighlightColor(Color.TRANSPARENT);
    ScrollView goalScroll=new ScrollView(this){
      @Override protected void onMeasure(int w,int h){
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.min(dp(78),height/4),MeasureSpec.AT_MOST));
      }
    };
    goalScroll.addView(goalText);box.addView(goalScroll,new LinearLayout.LayoutParams(-1,-2));
    panelText=new TextView(this);panelText.setTextColor(0xFFFFD866);
    panelText.setTextSize(12);panelText.setPadding(0,dp(4),0,dp(3));
    box.addView(panelText);
    listToggle=new TextView(this);listToggle.setTextColor(UiStyle.ACCENT);
    listToggle.setTextSize(12);listToggle.setPadding(dp(2),dp(5),dp(2),dp(3));
    box.addView(listToggle);
    operatorRows=new LinearLayout(this);operatorRows.setOrientation(LinearLayout.VERTICAL);
    operatorScroll=new ScrollView(this){
      @Override protected void onMeasure(int w,int h){
        int cap=Math.min(dp(150),Math.max(dp(40),height/2));
        if(MeasureSpec.getMode(h)!=MeasureSpec.UNSPECIFIED)
          cap=Math.min(cap,MeasureSpec.getSize(h));
        super.onMeasure(w,MeasureSpec.makeMeasureSpec(cap,MeasureSpec.AT_MOST));
      }
    };
    operatorScroll.addView(operatorRows);
    operatorScroll.setFillViewport(false);
    box.addView(operatorScroll,new LinearLayout.LayoutParams(-1,-2));
    listToggle.setOnClickListener(v->{
      operatorListExpanded=!operatorListExpanded;
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
              overlaySettlingUntil=SystemClock.elapsedRealtime()+100;
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
    Switch show=new Switch(this);show.setText("Панель операторов");UiStyle.toggle(show);
    show.setChecked(prefs.getBoolean("panel",true));box.addView(show);
    show.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("panel",on).apply();render();});
    Switch current=new Switch(this);current.setText("Только операторы с текущими тегами");UiStyle.toggle(current);
    current.setChecked(prefs.getBoolean("only_current_tags",false));box.addView(current);
    current.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("only_current_tags",on).apply();render();});
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
      if(paused||!screenPresent||!inRecruitment||recommendation==null)return;
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
      // Add goal dots above the top-right edge; leave the existing fill untouched.
      float radius=dp(3),spacing=dp(8);
      if(targets.size()>1)for(Recognition r:recognized){
        int index=0,columns=Math.max(1,(int)(r.tile.width()*sx/spacing));
        for(Map.Entry<String,Integer> target:targets.entrySet()){
          RecruitmentEngine.Result plan=targetPlans.get(target.getKey());
          if(plan==null||!plan.tags.contains(r.tag))continue;
          float x=r.tile.right*sx-origin[0]-radius-(index%columns)*spacing;
          float y=r.tile.top*sy-origin[1]-radius-dp(2)-(index/columns)*spacing;
          paint.setColor(0xFF10202B);canvas.drawCircle(x,y,radius+dp(1),paint);
          paint.setColor(target.getValue());canvas.drawCircle(x,y,radius,paint);index++;
        }
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
    width=nextW;height=nextH;scanEpoch.incrementAndGet();
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
    inRecruitment=false;recognized.clear();recommendation=null;knownTiles=new Rect[0];screenPresent=false;probeHits=probeMisses=0;
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
    closeGoalMenu();
    paused=true;inRecruitment=false;screenPresent=false;
    if(portraits!=null){portraits.close();portraits=null;}
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

