package com.example.arkrecruit.translate;
import android.content.*;
import android.graphics.*;
import android.media.Image;
import android.os.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
/** Translation has its own OCR, queue, generation, persistence and rendering. Recruitment stays isolated. */
public final class TranslationController implements AutoCloseable {
  private final Context context;private final Runnable redraw;
  public final TranslationOverlay overlay=new TranslationOverlay();
  private final Handler main=new Handler(Looper.getMainLooper());
  private final ExecutorService vision=Executors.newSingleThreadExecutor(),inference=Executors.newSingleThreadExecutor();
  private final TextRecognizer ocr=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
  private final ConcurrentMap<String,String> readyResults=new ConcurrentHashMap<>();
  private final TranslationVisibility visible=new TranslationVisibility();
  private volatile String processingSource="";
  private volatile int recognizedLines,completedLines;
  private volatile long translatedAt;
  public String progress(){return "OCR "+recognizedLines+" · перевод "+completedLines+"/"+recognizedLines;}
  private final TranslationTrace trace=new TranslationTrace();
  private volatile long operationStarted,lastOcrMs,lastZoom;
  private volatile String rawOcr="",ocrDescription="ещё не выполнено",lastModelProgress="";
  private String diagnostics(){
    String model=lastModelProgress;try{if(engine!=null)model=engine.progress();}catch(Throwable ignored){}
    return progress()+"\nЭтап: "+status+"\nВремя этапа: "+Math.max(0,SystemClock.elapsedRealtime()-operationStarted)/1000+" с\nQwen: "+model+
      "\nСлежение: "+trackingHz+" Гц, "+trackingMs+" мс; отрисовка "+overlay.drawMs+" мс; цель до60 Гц\nОтмена: "+cancellation+"\nOCR: "+ocrDescription+" · "+lastOcrMs+" мс\nСлой скрыт: "+overlay.hidden+" · нарисовано: "+overlay.drawn+" · последний шрифт: "+overlay.lastFontSize+" px\nИсходный OCR:\n"+rawOcr+"\nСтроки:"+trace.report(SystemClock.elapsedRealtime());
  }
  private final Runnable heartbeat=new Runnable(){public void run(){if(closed)return;if(enabled){liveDetails=diagnostics();context.getSharedPreferences("translation_model",0).edit().putString("last_report",liveDetails).apply();redraw.run();}main.postDelayed(this,1000);}};
  public static volatile String liveDetails="";
  private final TranslationPolicy.Epoch epoch=new TranslationPolicy.Epoch();
  private final AtomicBoolean scanning=new AtomicBoolean(),requesting=new AtomicBoolean();
  private final TranslationStore store;private final ScreenMemory screens;
  private final Map<String,String> names=new LinkedHashMap<>();
  private volatile QwenEngine engine;private volatile boolean enabled,closed,paused,blocked;
  private volatile String status="Выключен";
  public static volatile String liveStatus="Перевод не запущен";
  private String target(){return context.getSharedPreferences("translation_preferences",0).getString("target","ru");}
  private volatile String activeTarget="";
  private void report(String value){if(!value.equals(status))operationStarted=SystemClock.elapsedRealtime();status=value;liveStatus=value;liveDetails=diagnostics()+"\n"+value;redraw.run();}
  private volatile long nextRead,lastTick,movingUntil,hiddenAt;
  private volatile boolean cleanRequested;
  private static final class Node {TranslationOverlay.Item item;int[] reference;long last,moving;int misses;long lastReliable=SystemClock.elapsedRealtime();Node(TranslationOverlay.Item item,int[] image){this.item=item;reference=image;}}
  private final Map<String,Node> nodes=new LinkedHashMap<>();
  private final RegionalTracker.Groups groups=new RegionalTracker.Groups();
  private int[] previousGray;
  private static boolean retained(Node node,long now){return now-node.lastReliable<=700;}
  private volatile int[] ocrGray;private volatile int trackWidth=480,trackHeight;
  private volatile long trackingFrames,trackingWindow=SystemClock.elapsedRealtime();
  private volatile float trackingHz,trackingMs;
  private volatile String cancellation="нет";
  private volatile String previousText="",history="";
  private volatile List<TranslationOverlay.Item> sourceItems=Collections.emptyList();
  public TranslationController(Context c,Runnable draw){context=c;main.post(heartbeat);redraw=draw;store=new TranslationStore(c);screens=new ScreenMemory(store);
    // All recruitable names are protected by default, even without a transliteration.
    try{org.json.JSONArray data=new org.json.JSONArray(readAsset(c,"translation_names.json"));for(int i=0;i<data.length();i++){String n=data.getString(i);if(!n.isEmpty())names.put(n,n);}}catch(Exception ignored){}
    for(Map.Entry<String,String> e:TranslationPolicy.TERMS.entrySet())names.remove(e.getKey());
  }
  private static String readAsset(Context c,String name)throws Exception{try(java.io.InputStream in=c.getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");}}
  public String status(){return status;}
  public boolean enabled(){return enabled;}
  public void setEnabled(boolean on){
    enabled=on;readyResults.clear();if(on)context.getSharedPreferences("translation_model",0).edit().remove("last_error").apply();cancellation=on?"новая сессия":"перевод выключен";synchronized(nodes){nodes.clear();groups.clear();previousGray=null;}visible.clear();recognizedLines=completedLines=0;epoch.advance();if(engine!=null)engine.cancel();overlay.items=Collections.emptyList();sourceItems=Collections.emptyList();overlay.hidden=true;previousText="";history="";nextRead=0;cleanRequested=false;report(on?"Ожидание кадра":"Выключен");redraw.run();
  }
  public void pause(boolean p){if(paused==p)return;paused=p;if(p)cancellation="пауза пользователя";if(p&&!processingSource.isEmpty())trace.error(processingSource,"Пауза/открыты настройки",SystemClock.elapsedRealtime());epoch.advance();if(engine!=null)engine.cancel();if(p){visible.clear();overlay.items=Collections.emptyList();sourceItems=Collections.emptyList();}overlay.hidden=p;nextRead=0;redraw.run();}
  public void touch(float x,float y){if(!enabled||closed)return;movingUntil=SystemClock.elapsedRealtime()+900;nextRead=0;vision.execute(()->screens.touch(x,y));}
  public void frame(Image image,Rect[] exclusions,boolean userPaused,boolean interfaceBlocked){
    pause(userPaused);boolean changedInterface=blocked!=interfaceBlocked;blocked=interfaceBlocked;if(changedInterface)redraw.run();overlay.hidden=paused||blocked||!enabled;
    if(!enabled||closed||paused||blocked)return;
    String selected=target();if(!selected.equals(activeTarget)){activeTarget=selected;setEnabled(true);}
    long now=SystemClock.elapsedRealtime();if(now-lastTick<(now<movingUntil?16:66))return;lastTick=now;
    long trackingStarted=System.nanoTime();
    Image.Plane plane=image.getPlanes()[0];ByteBuffer pixels=plane.getBuffer();int base=pixels.position(),stride=plane.getRowStride(),step=plane.getPixelStride(),iw=image.getWidth(),ih=image.getHeight();
    OverlayReadback.Snapshot decoder=overlay.readback.at(image.getTimestamp());
    int w=480,h=Math.max(32,ih*w/iw);int[] gray=new int[w*h];float[] work=new float[2];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      int sx=x*iw/w,sy=y*ih/h,offset=base+sy*stride+sx*step;
      int color=Color.rgb(pixels.get(offset)&255,pixels.get(offset+1)&255,pixels.get(offset+2)&255);
      color=decoder.restore(color,(sx+.5f)/iw,(sy+.5f)/ih,work);gray[y*w+x]=(Color.red(color)*3+Color.green(color)*6+Color.blue(color))/10;
      for(Rect own:exclusions)if(own.contains(sx,sy)){gray[y*w+x]=0;break;}
    }
    if(trackHeight!=h){synchronized(nodes){nodes.clear();groups.clear();previousGray=null;}visible.clear();overlay.items=Collections.emptyList();sourceItems=Collections.emptyList();nextRead=0;trackHeight=h;}
    trackWidth=w;updateMotion(gray,w,h,now);
    trackingFrames++;if(now-trackingWindow>=1000){trackingHz=Math.round(trackingFrames*10000f/(now-trackingWindow))/10f;trackingFrames=0;trackingWindow=now;}
    trackingMs=Math.round((System.nanoTime()-trackingStarted)/100000f)/10f;
    if(scanning.get()||now<nextRead)return;if(!scanning.compareAndSet(false,true))return;
    nextRead=now+1800;ocrGray=gray;
    // Native bulk copy while Image is owned; correction/OCR run off the capture thread.
    int paddedW=stride/step;Bitmap padded=Bitmap.createBitmap(paddedW,ih,Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(pixels.duplicate());
    Bitmap raw=padded;if(paddedW!=iw){raw=Bitmap.createBitmap(iw,ih,Bitmap.Config.ARGB_8888);new Canvas(raw).drawBitmap(padded,0,0,null);padded.recycle();}
    final Bitmap captured=raw;final long generation=epoch.current();
    vision.execute(()->{
      Bitmap bitmap=null;
      try{
        // Undo compositing at capture resolution before any downsampling mixes edge pixels.
        int[] nativeColors=new int[iw*ih];captured.getPixels(nativeColors,0,iw,0,0,iw,ih);decoder.restore(nativeColors,iw,ih);captured.setPixels(nativeColors,0,iw,0,0,iw,ih);
        int ow=Math.min(2048,iw),oh=ih*ow/iw;Bitmap scaled=captured;
        if(ow!=iw)scaled=Bitmap.createScaledBitmap(captured,ow,oh,true);
        int[] color=new int[ow*oh];scaled.getPixels(color,0,ow,0,0,ow,oh);if(scaled!=captured)scaled.recycle();captured.recycle();
        bitmap=TranslationFrame.create(color,ow,oh,exclusions,iw,ih);scanOcr(bitmap,generation);
      }catch(RuntimeException error){if(!captured.isRecycled())captured.recycle();if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();scanning.set(false);failFrame(error);}
    });
  }
  private void updateMotion(int[] gray,int w,int h,long now){
    boolean moved=false;Map<String,TranslationOverlay.Item> poses=new HashMap<>();
    synchronized(nodes){
      Map<String,RegionalTracker.Pose> local=new HashMap<>();List<RegionalTracker.Groups.Sample> samples=new ArrayList<>();
      for(Node node:nodes.values()){
        TranslationOverlay.Item item=node.item;
        RegionalTracker.Pose p=RegionalTracker.track(node.reference,gray,w,h,item.box.left*w,item.box.top*h,item.box.right*w,item.box.bottom*h);
        local.put(item.source,p);
        samples.add(new RegionalTracker.Groups.Sample(item.source,item.box.centerX()*w,item.box.centerY()*h,
          node.reference==previousGray?p:new RegionalTracker.Pose(1,0,0,0,false)));
      }
      Map<String,RegionalTracker.Pose> shared=groups.resolve(samples);
      for(Node node:nodes.values()){
        TranslationOverlay.Item item=node.item;RegionalTracker.Pose direct=local.get(item.source),p=shared.get(item.source);
        if(direct.reliable){node.lastReliable=now;node.misses=0;}else{node.misses++;if(node.misses>3)nextRead=0;}
        if(p==null||!p.reliable)p=direct;
        if(p.reliable&&retained(node,now)){
          float[] q=item.quad==null?new float[]{item.box.left,item.box.top,item.box.right,item.box.top,item.box.right,item.box.bottom,item.box.left,item.box.bottom}:item.quad.clone();
          float displacement=0;for(int i=0;i<8;i+=2){float x=q[i]*w,y=q[i+1]*h,xx=p.x(x,y),yy=p.y(x,y);displacement=Math.max(displacement,(float)Math.hypot(xx-x,yy-y));q[i]=xx/w;q[i+1]=yy/h;}
          node.item=item.posed(q);
          if(displacement>.08f){node.moving=now+500;movingUntil=now+500;moved=true;}
        }
        // Advance all references together, so panel transforms describe the same interval.
        node.reference=gray;node.last=now;
        if(retained(node,now))poses.put(node.item.source,node.item);
      }
      previousGray=gray;
    }
    List<TranslationOverlay.Item> display=new ArrayList<>();for(TranslationOverlay.Item pose:poses.values()){String value=readyResults.get(activeTarget+"|"+pose.source);if(value!=null)display.add(pose.translated(value));}
    boolean changed=display.size()!=overlay.items.size();overlay.items=display;completedLines=display.size();if(moved||changed)redraw.run();
  }
  private TranslationOverlay.Item currentPose(TranslationOverlay.Item item){synchronized(nodes){Node node=nodes.get(item.source);return node==null?item:node.item;}}
  private void finishOcr(Bitmap frame){frame.recycle();scanning.set(false);overlay.hidden=!enabled||paused||blocked;redraw.run();}
  private void scanOcr(Bitmap bitmap,long generation){
    long started=SystemClock.elapsedRealtime();
    try{ocr.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(vision,text->{
      try{
        if(!enabled||closed||!epoch.accepts(generation)){finishOcr(bitmap);return;}
        List<OcrText> found=OcrText.collect(text,1,0,0);rawOcr=text.getText();lastOcrMs=SystemClock.elapsedRealtime()-started;
        ocrDescription=bitmap.getWidth()+"×"+bitmap.getHeight()+"; основной проход";
        accept(found,bitmap,generation);
        boolean small=found.size()<8;for(OcrText r:found)if(r.lineHeight<19)small=true;
        if(small&&SystemClock.elapsedRealtime()-lastZoom>6000){lastZoom=SystemClock.elapsedRealtime();zoomOcr(bitmap,generation,found,0,started);}else finishOcr(bitmap);
      }catch(RuntimeException error){finishOcr(bitmap);failFrame(error);}
    }).addOnFailureListener(vision,e->{finishOcr(bitmap);report("OCR: "+e.getMessage());});
    }catch(RuntimeException error){finishOcr(bitmap);failFrame(error);}
  }
  private void zoomOcr(Bitmap frame,long generation,List<OcrText> found,int index,long started){
    if(index>=4||closed||!enabled||!epoch.accepts(generation)){
      try{if(enabled&&epoch.accepts(generation)){StringBuilder raw=new StringBuilder();for(OcrText r:found)raw.append(r.text).append('\n');rawOcr=raw.toString();lastOcrMs=SystemClock.elapsedRealtime()-started;ocrDescription=frame.getWidth()+"×"+frame.getHeight()+"; основной + 4 увеличенных участка";accept(found,frame,generation);}}catch(RuntimeException error){failFrame(error);}finally{finishOcr(frame);}return;
    }
    int halfW=(frame.getWidth()+1)/2,halfH=(frame.getHeight()+1)/2;
    int left=Math.max(0,(index%2)*halfW-18),top=Math.max(0,(index/2)*halfH-18);
    int w=Math.min(halfW+36,frame.getWidth()-left),h=Math.min(halfH+36,frame.getHeight()-top);
    Bitmap crop=Bitmap.createBitmap(frame,left,top,w,h),zoom=Bitmap.createScaledBitmap(crop,w*2,h*2,true);if(crop!=frame&&crop!=zoom)crop.recycle();
    try{ocr.process(InputImage.fromBitmap(zoom,0)).addOnSuccessListener(vision,text->{
      try{OcrText.merge(found,OcrText.collect(text,2,left,top));}finally{zoom.recycle();}
      zoomOcr(frame,generation,found,index+1,started);
    }).addOnFailureListener(vision,e->{zoom.recycle();zoomOcr(frame,generation,found,index+1,started);});}
    catch(RuntimeException error){zoom.recycle();finishOcr(frame);failFrame(error);}
  }

  public void failFrame(RuntimeException error){
    android.util.Log.e("ArknightsTranslate","Translation frame failed",error);
    java.io.StringWriter trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));
    context.getSharedPreferences("translation_model",0).edit().putString("last_error",trace.toString()).apply();
    setEnabled(false);scanning.set(false);report("Ошибка обработки кадра: "+error.getClass().getSimpleName());
    main.post(()->android.widget.Toast.makeText(context,"Перевод остановлен из-за ошибки обработки кадра",android.widget.Toast.LENGTH_LONG).show());
  }
  private void accept(List<OcrText> regions,Bitmap frame,long generation){
    if(closed||!enabled||!epoch.accepts(generation))return;
    StringBuilder transcript=new StringBuilder();for(OcrText r:regions)transcript.append(r.text).append("\n");String full=transcript.toString();
    if(full.trim().isEmpty()){
      boolean tracked=false;synchronized(nodes){for(Node n:nodes.values())if(retained(n,SystemClock.elapsedRealtime())){tracked=true;break;}}
      if(tracked){report("OCR: пропуск кадра, перевод удерживается слежением");return;}
      visible.clear();recognizedLines=completedLines=0;overlay.items=Collections.emptyList();overlay.hidden=paused||blocked||!enabled;report("Текст не найден");redraw.run();return;
    }
    TranslationPolicy.Kind kind=TranslationPolicy.classify(full);
    // Stable anchors: short button/header labels, excluding long story paragraphs.
    StringBuilder anchors=new StringBuilder();for(OcrText r:regions)if(r.text.length()<60)anchors.append(r.text).append(' ');final long screenId=screens.identify(anchors.toString(),System.identityHashCode(frame));
    if(!similar(previousText,full)&&kind==TranslationPolicy.Kind.STORY)history=previousText.substring(Math.max(0,previousText.length()-400));
    previousText=full;
    List<TranslationOverlay.Item> sources=new ArrayList<>(),ready=new ArrayList<>();
    for(OcrText r:regions)addSource(sources,r.text,r.bounds,r.corners,r.lineHeight,frame);
    // A missed OCR line must not erase a label whose visual track is still reliable.
    synchronized(nodes){for(Node old:nodes.values()){
      if(!retained(old,SystemClock.elapsedRealtime()))continue;boolean present=false,replaced=false;
      for(TranslationOverlay.Item current:sources){if(current.source.equals(old.item.source)){present=true;break;}
        RectF intersection=new RectF(current.box);if(intersection.intersect(old.item.box)&&intersection.width()*intersection.height()>.4f*Math.min(current.box.width()*current.box.height(),old.item.box.width()*old.item.box.height()))replaced=true;
      }if(!present&&!replaced)sources.add(old.item);
    }}
    List<String> admitted=new ArrayList<>();for(TranslationOverlay.Item item:sources)admitted.add(item.source);
    visible.update(admitted);recognizedLines=sources.size();
    synchronized(nodes){nodes.keySet().retainAll(admitted);for(TranslationOverlay.Item item:sources){Node node=nodes.get(item.source);if(node==null)nodes.put(item.source,new Node(item,ocrGray));else {
      // Refresh OCR geometry/style only after matching its capture to the latest
      // tracked image. An asynchronous OCR result must not jump back in time.
      RegionalTracker.Pose alignment=RegionalTracker.track(ocrGray,node.reference,trackWidth,trackHeight,item.box.left*trackWidth,item.box.top*trackHeight,item.box.right*trackWidth,item.box.bottom*trackHeight);
      if(alignment.reliable){float[] q=item.quad.clone();for(int i=0;i<8;i+=2){float x=q[i]*trackWidth,y=q[i+1]*trackHeight;q[i]=alignment.x(x,y)/trackWidth;q[i+1]=alignment.y(x,y)/trackHeight;}node.item=item.posed(q);node.misses=0;node.lastReliable=SystemClock.elapsedRealtime();}
    } }}

    // Stable labels first; changing counters never hold up the rest of the screen.
    sources.sort((a,b)->kind==TranslationPolicy.Kind.STORY?Integer.compare(b.source.length(),a.source.length()):Boolean.compare(a.source.matches(".*\\d.*"),b.source.matches(".*\\d.*")));
    String context=history;final String language=activeTarget;
    for(TranslationOverlay.Item item:sources){String translated=InterfaceGlossary.find(item.source,language);if(translated==null)translated=store.get(TranslationPolicy.cacheKey(item.source,kind,context,language));if(translated!=null){readyResults.put(language+"|"+item.source,translated);ready.add(currentPose(item).translated(translated));trace.shown(item.source,translated,true);}}
    saveScreen(screenId,frame,sources,language);
    sourceItems=sources;synchronized(nodes){ready.clear();for(Node node:nodes.values()){String value=readyResults.get(activeTarget+"|"+node.item.source);if(value!=null&&retained(node,SystemClock.elapsedRealtime()))ready.add(node.item.translated(value));}overlay.items=ready;}completedLines=ready.size();overlay.hidden=paused||blocked||!enabled;if(!requesting.get())report("Распознано строк: "+recognizedLines);else liveDetails=diagnostics()+"\n"+status;redraw.run();
    if(requesting.compareAndSet(false,true)){
      final long request=generation;
      inference.execute(()->{try{
        if(!ModelStore.ready(context())){report("Нужна модель Qwen · откройте настройки перевода");return;}

        for(TranslationOverlay.Item item:sources){
          if(closed||!enabled||paused||!epoch.accepts(request))break;
          if(!visible.contains(item.source))continue;
          String key=TranslationPolicy.cacheKey(item.source,kind,context,language),translated=InterfaceGlossary.find(item.source,language);if(translated==null)translated=store.get(key);
          if(translated==null){
            processingSource=item.source;trace.start(item.source,SystemClock.elapsedRealtime());
            try{
              if(engine==null){report("Загрузка Qwen в память…");engine=new QwenEngine();engine.load(ModelStore.file(context()));}
              report("Qwen: перевод строки…");translated=engine.translate(TranslationPolicy.prompt(item.source,kind,context,names,language),Math.min(480,Math.max(64,item.source.length()*2))).trim();
              trace.finish(item.source,translated,SystemClock.elapsedRealtime());lastModelProgress=engine.progress();
              if(translated.isEmpty()){trace.error(item.source,"Пустой ответ: отмена или генерация не началась",SystemClock.elapsedRealtime());continue;}
              if(!epoch.accepts(request)){trace.error(item.source,"Ответ получен после смены сессии",SystemClock.elapsedRealtime());break;}store.put(key,translated);
            }catch(RuntimeException error){trace.error(item.source,error.toString(),SystemClock.elapsedRealtime());report("Qwen: "+error.getMessage());continue;}
          }
          store.visualTranslation(screenId,item.source,translated,language);
          if(epoch.accepts(request)&&visible.contains(item.source)){readyResults.put(language+"|"+item.source,translated);List<TranslationOverlay.Item> display=new ArrayList<>(overlay.items);boolean exists=false;for(TranslationOverlay.Item shown:display)if(shown.source.equals(item.source)){exists=true;break;}if(!exists)for(TranslationOverlay.Item current:sourceItems)if(current.source.equals(item.source)){display.add(currentPose(current).translated(translated));break;}overlay.items=display;trace.shown(item.source,translated,false);completedLines=display.size();translatedAt=SystemClock.elapsedRealtime();overlay.hidden=paused||blocked;redraw.run();}
        }
        if(epoch.accepts(request)){report("Перевод активен · кэш сохранён");}
      }catch(Throwable e){report("Qwen: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));main.post(()->android.widget.Toast.makeText(context(),status,android.widget.Toast.LENGTH_LONG).show());}
      finally{processingSource="";requesting.set(false);liveDetails=diagnostics()+"\n"+status;}});
    }
  }
  private void saveScreen(long id,Bitmap frame,List<TranslationOverlay.Item> sources,String language){
    if(id<=0)return;
    try{
      org.json.JSONArray labels=new org.json.JSONArray();
      for(TranslationOverlay.Item item:sources){org.json.JSONObject label=new org.json.JSONObject();label.put("source",item.source);String translated=readyResults.get(language+"|"+item.source);label.put("translated",translated==null?"":translated);label.put("angle",item.angle);label.put("background",item.background);label.put("ink",item.ink);
        org.json.JSONArray q=new org.json.JSONArray();float[] quad=item.quad==null?new float[]{item.box.left,item.box.top,item.box.right,item.box.top,item.box.right,item.box.bottom,item.box.left,item.box.bottom}:item.quad;for(float v:quad)q.put(v);label.put("quad",q);labels.put(label);
      }
      int width=Math.min(640,frame.getWidth());Bitmap small=Bitmap.createScaledBitmap(frame,width,Math.max(1,frame.getHeight()*width/frame.getWidth()),true);
      java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,75,bytes);if(small!=frame)small.recycle();store.visual(id,bytes.toByteArray(),labels.toString(),language);
    }catch(org.json.JSONException e){android.util.Log.w("ScreenMap","Cannot save screen",e);}
  }
  private Context context(){return context;}
  private static boolean similar(String a,String b){Set<String>x=ScreenMemory.words(a),y=ScreenMemory.words(b);if(x.isEmpty())return false;int common=0;for(String t:x)if(y.contains(t))common++;return common/(double)Math.max(x.size(),y.size())>.68;}
  private void addSource(List<TranslationOverlay.Item> out,String s,Rect b,Point[] corners,float lineHeight,Bitmap frame){
    s=TranslationPolicy.normalize(s);if(b==null||s.length()<2||!s.matches("(?s).*[A-Za-z]{2}.*")||s.length()>900){if(!s.isEmpty())trace.skip(s,b==null?"Нет границ OCR":s.length()>900?"Слишком длинный блок": "Нет латинского текста для перевода");return;}
    int w=frame.getWidth(),h=frame.getHeight();TextPlacement placement=TextPlacement.from(b,corners);
    RectF r=new RectF(placement.bounds.left/w,placement.bounds.top/h,placement.bounds.right/w,placement.bounds.bottom/h);
    int bg=placement.background(frame),fg=(Color.red(bg)*3+Color.green(bg)*6+Color.blue(bg))/10>140?Color.BLACK:Color.WHITE;
    trace.seen(s,"угол "+String.format(java.util.Locale.ROOT,"%.1f",placement.angle)+"°; область "+r+"; цвет #"+Integer.toHexString(bg));
    out.add(new TranslationOverlay.Item(s,"",r,new RectF(0,0,1,1),bg,fg,placement.angle,placement.normalized(w,h),lineHeight/h,placement.mask(frame,bg,TranslationOverlay.WINDOW_ALPHA)));
  }

  public void close(){closed=true;main.removeCallbacks(heartbeat);enabled=false;epoch.advance();if(engine!=null)engine.cancel();ocr.close();vision.shutdown();inference.execute(()->{if(engine!=null)engine.close();store.close();});inference.shutdown();overlay.items=Collections.emptyList();}
}

