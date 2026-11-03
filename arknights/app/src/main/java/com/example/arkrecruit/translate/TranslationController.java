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
  private final TranslationPolicy.Epoch epoch=new TranslationPolicy.Epoch();
  private final AtomicBoolean scanning=new AtomicBoolean(),requesting=new AtomicBoolean();
  private final TranslationStore store;private final ScreenMemory screens;
  private final Map<String,String> names=new LinkedHashMap<>();
  private volatile QwenEngine engine;private volatile boolean enabled,closed,paused;
  private volatile String status="Выключен";
  private volatile long nextRead,lastTick,movingUntil,hiddenAt;
  private int[] previous;private int thumbW,thumbH;private volatile boolean cleanRequested;
  private volatile String previousText="",history="";
  private volatile List<TranslationOverlay.Item> sourceItems=Collections.emptyList();
  public TranslationController(Context c,Runnable draw){context=c;redraw=draw;store=new TranslationStore(c);screens=new ScreenMemory(store);
    // All recruitable names are protected by default, even without a transliteration.
    try{org.json.JSONArray data=new org.json.JSONArray(readAsset(c,"translation_names.json"));for(int i=0;i<data.length();i++){String n=data.getString(i);if(!n.isEmpty())names.put(n,n);}}catch(Exception ignored){}
    for(Map.Entry<String,String> e:TranslationPolicy.TERMS.entrySet())names.remove(e.getKey());
  }
  private static String readAsset(Context c,String name)throws Exception{try(java.io.InputStream in=c.getAssets().open(name)){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toString("UTF-8");}}
  public String status(){return status;}
  public boolean enabled(){return enabled;}
  public void setEnabled(boolean on){
    enabled=on;epoch.advance();if(engine!=null)engine.cancel();overlay.items=Collections.emptyList();sourceItems=Collections.emptyList();overlay.hidden=true;previous=null;previousText="";history="";nextRead=0;cleanRequested=false;status=on?"Ожидание кадра":"Выключен";redraw.run();
  }
  public void pause(boolean p){if(paused==p)return;paused=p;epoch.advance();if(engine!=null)engine.cancel();if(p){overlay.items=Collections.emptyList();sourceItems=Collections.emptyList();}overlay.hidden=p;nextRead=0;previous=null;redraw.run();}
  public void touch(float x,float y){if(!enabled)return;movingUntil=SystemClock.elapsedRealtime()+900;epoch.advance();if(engine!=null)engine.cancel();overlay.hidden=true;cleanRequested=true;hiddenAt=SystemClock.elapsedRealtime();vision.execute(()->screens.touch(x,y));redraw.run();}
  public void frame(Image image,Rect[] exclusions,boolean analysisPaused){
    pause(analysisPaused);if(!enabled||closed||analysisPaused)return;
    long now=SystemClock.elapsedRealtime();if(now-lastTick<(now<movingUntil?66:160))return;lastTick=now;
    Image.Plane p=image.getPlanes()[0];ByteBuffer pixels=p.getBuffer();int base=pixels.position(),stride=p.getRowStride(),step=p.getPixelStride(),iw=image.getWidth(),ih=image.getHeight();
    int w=160,h=Math.max(60,ih*w/iw);int[] gray=new int[w*h];boolean[] mask=new boolean[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      int sx=x*iw/w,sy=y*ih/h,i=y*w+x,offset=base+sy*stride+sx*step;
      int r=pixels.get(offset)&255,g=pixels.get(offset+1)&255,b=pixels.get(offset+2)&255;gray[i]=(r*3+g*6+b)/10;
      for(Rect area:exclusions)if(area.contains(sx,sy)){mask[i]=true;break;}
      for(TranslationOverlay.Item item:overlay.items)if(item.box.contains(x/(float)w,y/(float)h)){mask[i]=true;break;}
    }
    if(previous!=null&&previous.length!=gray.length){previous=null;epoch.advance();overlay.items=Collections.emptyList();overlay.hidden=true;nextRead=0;}
    MotionTracker.Shift shift=MotionTracker.estimate(previous,gray,w,h,mask);
    long difference=previous==null?255:MotionTracker.error(previous,gray,w,h,0,0,mask);
    if(previous!=null&&difference>9&&!cleanRequested){
      movingUntil=now+600;
      if(shift.confident&&(shift.dx!=0||shift.dy!=0)&&Math.abs(shift.dy)<18){
        List<TranslationOverlay.Item> moved=new ArrayList<>();for(TranslationOverlay.Item item:overlay.items)moved.add(item.move(shift.dx/(float)w,shift.dy/(float)h));overlay.items=moved;redraw.run();
      }else if(!shift.confident){epoch.advance();if(engine!=null)engine.cancel();overlay.hidden=true;cleanRequested=true;hiddenAt=now;redraw.run();}
      nextRead=Math.min(nextRead,now+100);
    }
    previous=gray;thumbW=w;thumbH=h;
    if(scanning.get()||now<nextRead)return;
    // MediaProjection includes our own overlay: request one clean capture, then restore cached text.
    if(!overlay.items.isEmpty()&&!cleanRequested){overlay.hidden=true;cleanRequested=true;hiddenAt=now;redraw.run();return;}
    if(cleanRequested&&now-hiddenAt<45)return;
    if(!scanning.compareAndSet(false,true))return;
    nextRead=now+(now<movingUntil?330:1800);cleanRequested=false;
    int ow=Math.min(1280,iw),oh=ih*ow/iw;int[] color=new int[ow*oh];
    for(int y=0;y<oh;y++)for(int x=0;x<ow;x++){int sx=x*iw/ow,sy=y*ih/oh,offset=base+sy*stride+sx*step;color[y*ow+x]=Color.rgb(pixels.get(offset)&255,pixels.get(offset+1)&255,pixels.get(offset+2)&255);}
    Bitmap bitmap=Bitmap.createBitmap(color,ow,oh,Bitmap.Config.ARGB_8888);
    Canvas erase=new Canvas(bitmap);Paint paint=new Paint();paint.setColor(Color.BLACK);
    for(Rect r:exclusions)erase.drawRect(r.left*ow/(float)iw,r.top*oh/(float)ih,r.right*ow/(float)iw,r.bottom*oh/(float)ih,paint);
    long generation=epoch.current();
    ocr.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(vision,text->{try{accept(text,bitmap,generation);}finally{bitmap.recycle();scanning.set(false);}})
      .addOnFailureListener(vision,e->{bitmap.recycle();scanning.set(false);overlay.hidden=false;status="OCR: "+e.getMessage();redraw.run();});
  }
  private void accept(Text result,Bitmap frame,long generation){
    if(closed||!enabled||!epoch.accepts(generation))return;
    List<Text.TextBlock> blocks=result.getTextBlocks();String full=result.getText();
    if(full.isBlank()){overlay.items=Collections.emptyList();overlay.hidden=false;status="Текст не найден";redraw.run();return;}
    TranslationPolicy.Kind kind=TranslationPolicy.classify(full);
    // Stable anchors: short button/header labels, excluding long story paragraphs.
    StringBuilder anchors=new StringBuilder();for(Text.TextBlock b:blocks)if(b.getText().length()<60)anchors.append(b.getText()).append(' ');screens.identify(anchors.toString());
    if(!similar(previousText,full)){if(kind==TranslationPolicy.Kind.STORY)history=previousText.substring(Math.max(0,previousText.length()-400));epoch.advance();generation=epoch.current();if(engine!=null)engine.cancel();overlay.items=Collections.emptyList();}
    previousText=full;
    List<TranslationOverlay.Item> sources=new ArrayList<>(),ready=new ArrayList<>();
    for(Text.TextBlock block:blocks){
      if(block.getText().length()>650){for(Text.Line line:block.getLines())addSource(sources,line.getText(),line.getBoundingBox(),frame);}else addSource(sources,block.getText(),block.getBoundingBox(),frame);
    }
    String context=history;
    for(TranslationOverlay.Item item:sources){String translated=store.get(TranslationPolicy.cacheKey(item.source,kind,context));if(translated!=null)ready.add(new TranslationOverlay.Item(item.source,translated,item.box,item.clip,item.background,item.ink));}
    sourceItems=sources;overlay.items=ready;overlay.hidden=false;redraw.run();
    if(requesting.compareAndSet(false,true)){
      final long request=generation;
      inference.execute(()->{try{
        if(!ModelStore.ready(context())){status="Нужна модель Qwen · откройте настройки перевода";return;}
        if(engine==null)engine=new QwenEngine();engine.load(ModelStore.file(context()));
        for(TranslationOverlay.Item item:sources){
          if(closed||!enabled||paused||!epoch.accepts(request))break;
          String key=TranslationPolicy.cacheKey(item.source,kind,context),translated=store.get(key);
          if(translated==null){status="Перевод…";translated=engine.translate(TranslationPolicy.prompt(item.source,kind,context,names)).trim();if(translated.isEmpty())continue;if(!epoch.accepts(request))break;store.put(key,translated);}
          if(epoch.accepts(request)){List<TranslationOverlay.Item> display=new ArrayList<>(overlay.items);boolean exists=false;for(TranslationOverlay.Item shown:display)if(shown.source.equals(item.source)){exists=true;break;}if(!exists)for(TranslationOverlay.Item current:sourceItems)if(current.source.equals(item.source)){display.add(new TranslationOverlay.Item(current.source,translated,current.box,current.clip,current.background,current.ink));break;}overlay.items=display;overlay.hidden=false;redraw.run();}
        }
        if(epoch.accepts(request)){status="Перевод активен · кэш сохранён";}
      }catch(Throwable e){status="Qwen: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());main.post(()->android.widget.Toast.makeText(context(),status,android.widget.Toast.LENGTH_LONG).show());}
      finally{requesting.set(false);}});
    }
  }
  private Context context(){return context;}
  private static boolean similar(String a,String b){Set<String>x=ScreenMemory.words(a),y=ScreenMemory.words(b);if(x.isEmpty())return false;int common=0;for(String t:x)if(y.contains(t))common++;return common/(double)Math.max(x.size(),y.size())>.68;}
  private void addSource(List<TranslationOverlay.Item> out,String s,Rect b,Bitmap frame){
    s=TranslationPolicy.normalize(s);if(b==null||s.length()<2||!s.matches("(?s).*[A-Za-z]{2}.*")||s.length()>900)return;
    int w=frame.getWidth(),h=frame.getHeight();RectF r=new RectF(Math.max(0,b.left-2)/(float)w,Math.max(0,b.top-2)/(float)h,Math.min(w,b.right+2)/(float)w,Math.min(h,b.bottom+2)/(float)h);
    int[] red=new int[10],green=new int[10],blue=new int[10];for(int i=0;i<10;i++){int x=Math.max(0,Math.min(w-1,b.left+(b.width()*i/9))),y=Math.max(0,Math.min(h-1,b.top-3));int c=frame.getPixel(x,y);red[i]=Color.red(c);green[i]=Color.green(c);blue[i]=Color.blue(c);}Arrays.sort(red);Arrays.sort(green);Arrays.sort(blue);int bg=Color.rgb(red[5],green[5],blue[5]);int fg=(red[5]*3+green[5]*6+blue[5])/10>140?Color.BLACK:Color.WHITE;
    // Restrict every mask to its own text panel box; never cover neighboring controls.
    out.add(new TranslationOverlay.Item(s,"",r,new RectF(0,0,1,1),bg,fg));
  }
  public void close(){closed=true;enabled=false;epoch.advance();if(engine!=null)engine.cancel();ocr.close();vision.shutdown();inference.execute(()->{if(engine!=null)engine.close();store.close();});inference.shutdown();overlay.items=Collections.emptyList();}
}
