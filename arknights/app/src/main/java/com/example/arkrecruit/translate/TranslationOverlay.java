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
    public Item move(float dx,float dy){RectF b=new RectF(box);b.offset(dx,dy);float[] q=quad==null?null:quad.clone();if(q!=null)for(int i=0;i<8;i+=2){q[i]+=dx;q[i+1]+=dy;}return new Item(source,translated,b,clip,background,ink,angle,q,lineHeight,mask);}
  }
  public volatile List<Item> items=Collections.emptyList();public volatile boolean hidden;
  private final Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
  public volatile int drawn;public volatile float lastFontSize;
  public void draw(Canvas canvas,int width,int height,int originX,int originY){
    drawn=0;if(hidden)return;
    for(Item item:items){
      RectF r=new RectF(item.box.left*width-originX,item.box.top*height-originY,item.box.right*width-originX,item.box.bottom*height-originY);
      RectF clip=new RectF(item.clip.left*width-originX,item.clip.top*height-originY,item.clip.right*width-originX,item.clip.bottom*height-originY);
      if(!RectF.intersects(r,clip)||r.width()<5||r.height()<5)continue;
      int save=canvas.save();canvas.clipRect(clip);float localW=r.width(),localH=r.height();
      if(item.quad!=null){float[] q=item.quad.clone();for(int i=0;i<8;i+=2){q[i]=q[i]*width-originX;q[i+1]=q[i+1]*height-originY;}
        localW=(float)(Math.hypot(q[2]-q[0],q[3]-q[1])+Math.hypot(q[4]-q[6],q[5]-q[7]))/2;
        localH=(float)(Math.hypot(q[6]-q[0],q[7]-q[1])+Math.hypot(q[4]-q[2],q[5]-q[3]))/2;
        Matrix transform=new Matrix();if(!transform.setPolyToPoly(new float[]{0,0,localW,0,localW,localH,0,localH},0,q,0,4)){canvas.restoreToCount(save);continue;}canvas.concat(transform);
      }else{canvas.rotate(item.angle,r.centerX(),r.centerY());canvas.translate(r.left,r.top);}
      canvas.clipRect(0,0,localW,localH);bg.setColor(item.background);
      if(item.mask!=null)canvas.drawBitmap(item.mask,null,new RectF(0,0,localW,localH),bg);else canvas.drawRect(0,0,localW,localH,bg);
      int available=Math.max(1,(int)localW-2);text.setColor(item.ink);
      float line=Math.min(localH,item.lineHeight*height);text.setTypeface(Typeface.create("sans-serif-condensed",line>=32?Typeface.BOLD:Typeface.NORMAL));
      text.setTextSize(100);float size=Math.max(9,line*100/(text.descent()-text.ascent())*.94f);StaticLayout layout;
      // Fit to both bounds while retaining the source's line scale; no fixed26px cap.
      do{text.setTextSize(size);layout=StaticLayout.Builder.obtain(item.translated,0,item.translated.length(),text,available).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();if(layout.getHeight()<=localH-1||size<=9)break;size-=.75f;}while(true);
      lastFontSize=size;canvas.translate(1,Math.max(0,(localH-layout.getHeight())/2));layout.draw(canvas);drawn++;canvas.restoreToCount(save);
    }
  }
}
