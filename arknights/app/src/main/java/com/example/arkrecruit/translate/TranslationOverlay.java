package com.example.arkrecruit.translate;
import android.graphics.*;
import android.text.*;
import java.util.*;
/** Draw the mask and fitted glyphs in the OCR text plane, including perspective/skew. */
public final class TranslationOverlay {
  public static final float WINDOW_ALPHA=.79f;
  public static final class Item {
    public final String source,translated;public final RectF box,clip;public final int background,ink;public final float angle;
    public final float[] quad;public final float lineHeight;public final Bitmap mask;
    public Item(String s,String t,RectF b,RectF c,int bg,int fg){this(s,t,b,c,bg,fg,0);}
    public Item(String s,String t,RectF b,RectF c,int bg,int fg,float degrees){this(s,t,b,c,bg,fg,degrees,null,b.height(),null);}
    public Item(String s,String t,RectF b,RectF c,int bg,int fg,float degrees,float[] q,float line,Bitmap patch){angle=degrees;source=s;translated=t;box=new RectF(b);clip=new RectF(c);background=bg;ink=fg;quad=q==null?null:q.clone();lineHeight=line;mask=patch;}
    public Item translated(String value){return new Item(source,value,box,clip,background,ink,angle,quad,lineHeight,mask);}
    public Item posed(float[] q){float l=q[0],r=l,t=q[1],b=t;for(int i=2;i<8;i+=2){l=Math.min(l,q[i]);r=Math.max(r,q[i]);t=Math.min(t,q[i+1]);b=Math.max(b,q[i+1]);}return new Item(source,translated,new RectF(l,t,r,b),clip,background,ink,angle,q,lineHeight,mask);}
    public Item move(float dx,float dy){RectF b=new RectF(box);b.offset(dx,dy);float[] q=quad==null?null:quad.clone();if(q!=null)for(int i=0;i<8;i+=2){q[i]+=dx;q[i+1]+=dy;}return new Item(source,translated,b,clip,background,ink,angle,q,lineHeight,mask);}
  }
  public volatile List<Item> items=Collections.emptyList();public volatile boolean hidden;
  public final OverlayReadback readback=new OverlayReadback();
  private static final class Render {Bitmap bitmap;int[] pixels;float font;}
  private static final class Pose {Bitmap bitmap;int left,top;OverlayReadback.Patch patch;}
  private final LinkedHashMap<String,Pose> poses=new LinkedHashMap<String,Pose>(64,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Pose> e){return size()>64;}};
  private final LinkedHashMap<String,Render> cache=new LinkedHashMap<String,Render>(64,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Render> e){return size()>64;}};
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  public volatile int drawn;public volatile float lastFontSize,drawMs;
  private Render render(Item item,int w,int h,int displayHeight){
    String key=item.source+"|"+item.translated+"|"+System.identityHashCode(item.mask)+"|"+w+"x"+h+"|"+item.background+"|"+item.ink;
    Render cached=cache.get(key);if(cached!=null)return cached;
    Render out=new Render();out.bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out.bitmap);Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);bg.setColor(item.background);
    if(item.mask!=null)c.drawBitmap(item.mask,null,new RectF(0,0,w,h),bg);else c.drawRect(0,0,w,h,bg);
    TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);text.setColor(item.ink);float line=Math.min(h,item.lineHeight*displayHeight);text.setTypeface(Typeface.create("sans-serif-condensed",line>=32?Typeface.BOLD:Typeface.NORMAL));text.setTextSize(100);
    float size=Math.max(9,line*100/(text.descent()-text.ascent())*.94f);StaticLayout layout;
    do{text.setTextSize(size);layout=StaticLayout.Builder.obtain(item.translated,0,item.translated.length(),text,Math.max(1,w-2)).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();if(layout.getHeight()<=h-1||size<=9)break;size-=.75f;}while(true);
    out.font=size;c.translate(1,Math.max(0,(h-layout.getHeight())/2));layout.draw(c);out.pixels=new int[w*h];out.bitmap.getPixels(out.pixels,0,w,0,0,w,h);cache.put(key,out);return out;
  }
  public void draw(Canvas canvas,int width,int height,int originX,int originY){
    long started=System.nanoTime();drawn=0;List<OverlayReadback.Patch> patches=new ArrayList<>();if(hidden){readback.publish(patches);return;}
    for(Item item:items){
      float[] norm=item.quad==null?new float[]{item.box.left,item.box.top,item.box.right,item.box.top,item.box.right,item.box.bottom,item.box.left,item.box.bottom}:item.quad.clone();
      if(item.quad==null&&item.angle!=0){Matrix rotation=new Matrix();rotation.setRotate(item.angle,item.box.centerX()*width,item.box.centerY()*height);for(int i=0;i<8;i+=2){norm[i]*=width;norm[i+1]*=height;}rotation.mapPoints(norm);for(int i=0;i<8;i+=2){norm[i]/=width;norm[i+1]/=height;}}
      float[] q=norm.clone();for(int i=0;i<8;i+=2){q[i]=q[i]*width-originX;q[i+1]=q[i+1]*height-originY;}
      int w=Math.max(1,Math.round((float)(Math.hypot(q[2]-q[0],q[3]-q[1])+Math.hypot(q[4]-q[6],q[5]-q[7]))/2)),h=Math.max(1,Math.round((float)(Math.hypot(q[6]-q[0],q[7]-q[1])+Math.hypot(q[4]-q[2],q[5]-q[3]))/2));
      if(w<5||h<5||w>width*2||h>height*2)continue;
      Render render=render(item,w,h,height);Matrix draw=new Matrix();if(!draw.setPolyToPoly(new float[]{0,0,w,0,w,h,0,h},0,q,0,4))continue;
      // Rasterize with Canvas once per pose, then present and invert the exact same pixels.
      // Analytic bilinear sampling differs from Skia at fractional positions and amplifies
      // the mismatch through the translucent window.
      String poseKey=System.identityHashCode(render)+"|"+Arrays.toString(q)+"|"+item.clip+"|"+width+"x"+height+"|"+originX+","+originY;
      Pose pose=poses.get(poseKey);
      if(pose==null){
        float l=q[0],r=l,t=q[1],b=t;for(int i=2;i<8;i+=2){l=Math.min(l,q[i]);r=Math.max(r,q[i]);t=Math.min(t,q[i+1]);b=Math.max(b,q[i+1]);}
        int left=Math.max(-originX,(int)Math.floor(l)-1),top=Math.max(-originY,(int)Math.floor(t)-1),right=Math.min(width-originX,(int)Math.ceil(r)+1),bottom=Math.min(height-originY,(int)Math.ceil(b)+1);
        if(right<=left||bottom<=top)continue;
        pose=new Pose();pose.left=left;pose.top=top;pose.bitmap=Bitmap.createBitmap(right-left,bottom-top,Bitmap.Config.ARGB_8888);
        Canvas raster=new Canvas(pose.bitmap);raster.translate(-left,-top);raster.clipRect(item.clip.left*width-originX,item.clip.top*height-originY,item.clip.right*width-originX,item.clip.bottom*height-originY);raster.drawBitmap(render.bitmap,draw,paint);
        float[] rasterQuad={(left+originX)/(float)width,(top+originY)/(float)height,(right+originX)/(float)width,(top+originY)/(float)height,(right+originX)/(float)width,(bottom+originY)/(float)height,(left+originX)/(float)width,(bottom+originY)/(float)height};
        pose.patch=new OverlayReadback.Patch(pose.bitmap,rasterQuad,new RectF(0,0,1,1));poses.put(poseKey,pose);
      }
      canvas.drawBitmap(pose.bitmap,pose.left,pose.top,null);drawn++;lastFontSize=render.font;patches.add(pose.patch);
    }readback.publish(patches);drawMs=Math.round((System.nanoTime()-started)/100000f)/10f;
  }
}
