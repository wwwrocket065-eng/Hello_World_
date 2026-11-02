package com.example.arkrecruit;
import android.content.SharedPreferences;
import android.graphics.*;
/** Transparent reference-style gold rim, haze and fixed-shape four-point stars. */
public final class HighlightRenderer {
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path star=new Path();
  private final RectF inset=new RectF(),haloRect=new RectF();
  private final Bitmap halo=Bitmap.createBitmap(48,48,Bitmap.Config.ARGB_8888);
  public HighlightRenderer(){
    Canvas c=new Canvas(halo);Paint glow=new Paint(Paint.ANTI_ALIAS_FLAG);
    glow.setShader(new RadialGradient(24,24,24,0xFFFFF3D1,Color.TRANSPARENT,Shader.TileMode.CLAMP));
    c.drawCircle(24,24,24,glow);
  }
  private static int alpha(int c,float a){return (c&0xFFFFFF)|(Math.max(0,Math.min(255,Math.round(a*255)))<<24);}
  public void draw(Canvas c,RectF r,SharedPreferences prefs,long time,int seed,RectF text){
    int color=HighlightStyle.color(prefs);
    float opacity=1-prefs.getInt("highlightTransparency",20)/100f;
    float unit=Math.max(1,r.height()/42f),radius=3*unit;
    inset.set(r);inset.inset(2*unit,2*unit);
    p.setStyle(Paint.Style.FILL);p.setShader(null);
    p.setColor(alpha(color,.08f*opacity));c.drawRoundRect(inset,radius,radius,p);
    // Layered rim preserves transparency instead of putting an opaque picture over text.
    p.setStyle(Paint.Style.STROKE);
    for(int i=7;i>=1;i--){p.setStrokeWidth(i*unit);p.setColor(alpha(color,opacity*(i==1?.85f:.055f)));c.drawRoundRect(inset,radius,radius,p);}
    p.setStrokeWidth(1.1f*unit);p.setColor(alpha(Color.WHITE,.95f*opacity));c.drawRoundRect(inset,radius,radius,p);
    p.setStyle(Paint.Style.FILL);
    int save=c.save();c.clipRect(r.left+5*unit,r.top+5*unit,r.right-5*unit,r.bottom-5*unit);
    if(text!=null)c.clipOutRect(text);
    // Quiet diagonal glass bands, like the supplied examples.
    p.setColor(alpha(color,.035f*opacity));
    for(int i=-1;i<4;i++){
      Path band=star;band.reset();float x=r.left+r.width()*(i*.35f);
      band.moveTo(x,r.bottom);band.lineTo(x+r.width()*.12f,r.bottom);
      band.lineTo(x+r.width()*.12f+r.height()*1.5f,r.top);band.lineTo(x+r.height()*1.5f,r.top);band.close();c.drawPath(band,p);
    }
    int count=16;
    for(int i=0;i<count;i++){
      long hash=(seed*1103515245L+i*2654435761L)&0x7FFFFFFF;
      float nx=((hash%997)+.5f)/997f,ny=(((hash/997)%991)+.5f)/991f;
      float x=r.left+(0.06f+nx*.88f)*r.width(),y=r.top+(0.10f+ny*.80f)*r.height();
      float size=(i%4==0?2.2f:i%3==0?1.6f:1.0f)*unit;
      float pulse=prefs.getBoolean("twinkle",true)?(.35f+.65f*(.5f+.5f*(float)Math.sin(time*.0016+i*2.39+seed))):1;
      float fog=prefs.getInt("fog",35)/100f;
      haloRect.set(x-size*4,y-size*4,x+size*4,y+size*4);
      p.setColor(Color.WHITE);p.setAlpha(Math.round(255*opacity*pulse*fog*.6f));
      c.drawBitmap(halo,null,haloRect,p);p.setAlpha(255);
      star.reset();star.moveTo(x,y-size*1.4f);
      star.quadTo(x+size*.16f,y-size*.16f,x+size,y);
      star.quadTo(x+size*.16f,y+size*.16f,x,y+size*1.4f);
      star.quadTo(x-size*.16f,y+size*.16f,x-size,y);
      star.quadTo(x-size*.16f,y-size*.16f,x,y-size*1.4f);star.close();
      p.setColor(alpha(0xFFFFFBEF,opacity*pulse));c.drawPath(star,p);
    }
    c.restoreToCount(save);
  }
}
