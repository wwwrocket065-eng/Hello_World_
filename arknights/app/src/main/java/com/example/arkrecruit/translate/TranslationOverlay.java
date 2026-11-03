package com.example.arkrecruit.translate;
import android.graphics.*;
import android.text.*;
import java.util.*;
/** Immutable frame snapshot. Non-touchable renderer shares the existing overlay window. */
public final class TranslationOverlay {
  public static final class Item {
    public final String source,translated;public final RectF box,clip;public final int background,ink;
    public Item(String s,String t,RectF b,RectF c,int bg,int fg){source=s;translated=t;box=new RectF(b);clip=new RectF(c);background=bg;ink=fg;}
    public Item move(float dx,float dy){RectF b=new RectF(box);b.offset(dx,dy);return new Item(source,translated,b,clip,background,ink);}
  }
  public volatile List<Item> items=Collections.emptyList();
  public volatile boolean hidden;
  private final Paint bg=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
  public void draw(Canvas canvas,int width,int height,int originX,int originY){
    if(hidden)return;
    for(Item item:items){
      RectF r=new RectF(item.box.left*width-originX,item.box.top*height-originY,item.box.right*width-originX,item.box.bottom*height-originY);
      RectF clip=new RectF(item.clip.left*width-originX,item.clip.top*height-originY,item.clip.right*width-originX,item.clip.bottom*height-originY);
      if(!RectF.intersects(r,clip)||r.width()<5||r.height()<5)continue;
      int save=canvas.save();canvas.clipRect(clip);canvas.clipRect(r);
      bg.setColor(item.background);canvas.drawRect(r,bg);
      int available=Math.max(1,(int)r.width()-4);text.setColor(item.ink);text.setTypeface(Typeface.create("sans-serif-condensed",Typeface.NORMAL));
      float size=Math.min(26,Math.max(12,r.height()*.7f));StaticLayout layout;
      do{ text.setTextSize(size);layout=StaticLayout.Builder.obtain(item.translated,0,item.translated.length(),text,available).setIncludePad(false).setAlignment(Layout.Alignment.ALIGN_NORMAL).setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build();size-=1;}while(layout.getHeight()>r.height()-2&&size>=9);
      canvas.translate(r.left+2,r.top+Math.max(1,(r.height()-layout.getHeight())/2));layout.draw(canvas);canvas.restoreToCount(save);
    }
  }
}
