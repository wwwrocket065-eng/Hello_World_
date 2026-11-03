package com.example.arkrecruit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Consistent, rounded disclosure triangle independent of the system font. */
public final class DisclosureArrow extends Drawable {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final boolean expanded;
  public DisclosureArrow(int color,boolean expanded,float radius){
    this.expanded=expanded;
    paint.setColor(color);
    paint.setPathEffect(new CornerPathEffect(radius));
  }
  @Override public void draw(Canvas canvas){
    Rect b=getBounds();
    float left=b.left+b.width()*.12f,right=b.right-b.width()*.12f;
    float top=b.top+b.height()*.22f,bottom=b.bottom-b.height()*.15f;
    Path path=new Path();
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,b.width()/8f));
    paint.setStrokeCap(Paint.Cap.SQUARE);paint.setPathEffect(null);
    path.moveTo(left,expanded?bottom:top);
    path.lineTo(b.exactCenterX(),expanded?top:bottom);
    path.lineTo(right,expanded?bottom:top);canvas.drawPath(path,paint);
  }
  @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
  @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
  @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
