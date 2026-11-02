package com.example.arkrecruit;
import android.content.Context;
import android.graphics.*;
import android.view.*;
/** Continuous hue/saturation wheel; brightness remains a separate parameter. */
public final class ColorWheel extends View {
  public interface Listener{void changed(float hue,float saturation);}
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float[] hsv=new float[3];
  private final Listener listener;
  private boolean tracking;
  public ColorWheel(Context c,int color,Listener listener){super(c);this.listener=listener;setColor(color);setContentDescription("Палитра цвета подсветки");}
  public void setColor(int color){Color.colorToHSV(color,hsv);invalidate();}
  @Override protected void onDraw(Canvas c){
    float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)-12;
    p.setStyle(Paint.Style.FILL);p.setShader(new SweepGradient(x,y,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null));c.drawCircle(x,y,r,p);
    p.setShader(new RadialGradient(x,y,r,Color.WHITE,Color.TRANSPARENT,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,p);p.setShader(null);
    float px=x+(float)Math.cos(Math.toRadians(hsv[0]))*hsv[1]*r,py=y+(float)Math.sin(Math.toRadians(hsv[0]))*hsv[1]*r;
    p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xFF1D2B36);c.drawCircle(px,py,9,p);p.setStrokeWidth(2);p.setColor(Color.WHITE);c.drawCircle(px,py,9,p);p.setStyle(Paint.Style.FILL);
  }
  @Override public boolean onTouchEvent(MotionEvent e){
    float dx=e.getX()-getWidth()/2f,dy=e.getY()-getHeight()/2f;
    float radius=Math.min(getWidth(),getHeight())/2f-12;
    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
      tracking=Math.hypot(dx,dy)<=radius;
      if(!tracking)return false;
    }
    if(!tracking)return false;
    if(e.getActionMasked()==MotionEvent.ACTION_DOWN||e.getActionMasked()==MotionEvent.ACTION_MOVE){
      getParent().requestDisallowInterceptTouchEvent(true);
      // A drag beginning inside the wheel may continue to its rim.
      hsv[0]=(float)((Math.toDegrees(Math.atan2(dy,dx))+360)%360);
      hsv[1]=Math.min(1,(float)Math.hypot(dx,dy)/radius);
      if(listener!=null)listener.changed(hsv[0],hsv[1]);invalidate();return true;
    }
    if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){
      tracking=false;getParent().requestDisallowInterceptTouchEvent(false);
      if(e.getActionMasked()==MotionEvent.ACTION_UP)performClick();return true;
    }
    return true;
  }
  @Override public boolean performClick(){super.performClick();return true;}
}
