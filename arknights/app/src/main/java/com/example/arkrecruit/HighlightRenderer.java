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
    inset.set(r);// Frame centerline follows the measured button boundary.
    p.setStyle(Paint.Style.FILL);p.setShader(null);
    if(prefs.getBoolean("showFill",true)){
      p.setColor(alpha(color,.08f*opacity));c.drawRoundRect(inset,radius,radius,p);
    }
    if(prefs.getBoolean("showFrame",true)){
      p.setStyle(Paint.Style.STROKE);
      for(int i=12;i>=4;i-=2){p.setStrokeWidth(i*unit);p.setColor(alpha(color,opacity*.07f));c.drawRoundRect(inset,radius,radius,p);}
      p.setStrokeWidth(3.6f*unit);p.setColor(alpha(color,1));c.drawRoundRect(inset,radius,radius,p);
      p.setStrokeWidth(1.6f*unit);p.setColor(alpha(Color.WHITE,1));c.drawRoundRect(inset,radius,radius,p);
    }
    p.setStyle(Paint.Style.FILL);
    int save=c.save();c.clipRect(r.left+5*unit,r.top+5*unit,r.right-5*unit,r.bottom-5*unit);

    // Quiet diagonal glass bands, like the supplied examples.
    p.setColor(alpha(color,.035f*opacity));
    if(prefs.getBoolean("showFill",true))for(int i=-1;i<4;i++){
      Path band=star;band.reset();float x=r.left+r.width()*(i*.35f);
      band.moveTo(x,r.bottom);band.lineTo(x+r.width()*.12f,r.bottom);
      band.lineTo(x+r.width()*.12f+r.height()*1.5f,r.top);band.lineTo(x+r.height()*1.5f,r.top);band.close();c.drawPath(band,p);
    }
    int count=prefs.getBoolean("showStars",true)?HighlightPattern.COUNT:0;
    for(int i=0;i<count;i++){
      float x=r.left+HighlightPattern.x(seed,i)*r.width(),y=r.top+HighlightPattern.y(seed,i)*r.height();
      float size=HighlightPattern.size(seed,i)*unit;
      float pulse=HighlightPattern.pulse(time,seed,i);
      // Leave letters clear by omitting overlapping stars, never painting a mask.
      if(text!=null && x+size*2.8f>text.left && x-size*2.8f<text.right
          && y+size*2.8f>text.top && y-size*2.8f<text.bottom)continue;
      float fog=.16f;
      haloRect.set(x-size*2.8f,y-size*2.8f,x+size*2.8f,y+size*2.8f);
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
