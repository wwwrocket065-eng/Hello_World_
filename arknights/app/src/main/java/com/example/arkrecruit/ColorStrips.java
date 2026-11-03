package com.example.arkrecruit;
import android.content.Context;
import android.graphics.*;
import android.view.*;
public final class ColorStrips extends View {
 public interface Listener{void changed(int color);}
 private final Paint p=new Paint(3); private final Listener listener; private float hue,tone=.5f; private int row;
 public ColorStrips(Context c,int color,Listener l){super(c);listener=l;setColor(color);setContentDescription("Верхняя полоса — цвет; нижняя — от белого через цвет к чёрному");}
 public void setColor(int color){float[] h=new float[3];Color.colorToHSV(color,h);hue=h[0];tone=h[2]<.999f?1-h[2]/2:h[1]/2;invalidate();}
 private int base(){return Color.HSVToColor(new float[]{hue,1,1});}
 public int color(){int b=base();float k=tone<=.5f?tone*2:(1-tone)*2;return Color.rgb(channel(Color.red(b),k),channel(Color.green(b),k),channel(Color.blue(b),k));}
 private int channel(int c,float k){return Math.round(tone<=.5f?255+(c-255)*k:c*k);}
 @Override protected void onDraw(Canvas c){float d=getResources().getDisplayMetrics().density,l=12*d,r=getWidth()-l,h=24*d;
   for(int i=0;i<2;i++){float y=(i*44+22)*d;p.setStyle(Paint.Style.FILL);p.setShader(i==0?new LinearGradient(l,y,r,y,new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED},null,Shader.TileMode.CLAMP):new LinearGradient(l,y,r,y,new int[]{Color.WHITE,base(),Color.BLACK},new float[]{0,.5f,1},Shader.TileMode.CLAMP));c.drawRect(l,y-h/2,r,y+h/2,p);p.setShader(null);
     float x=l+(r-l)*(i==0?hue/360:tone);p.setColor(Color.BLACK);p.setStrokeWidth(4*d);p.setStyle(Paint.Style.STROKE);c.drawCircle(x,y,8*d,p);p.setColor(Color.WHITE);p.setStrokeWidth(2*d);c.drawCircle(x,y,8*d,p);}
 }
 @Override public boolean onTouchEvent(MotionEvent e){int a=e.getActionMasked();float d=getResources().getDisplayMetrics().density;
   if(a==MotionEvent.ACTION_DOWN){row=e.getY()<44*d?0:1;getParent().requestDisallowInterceptTouchEvent(true);}
   if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_MOVE){float f=Math.max(0,Math.min(1,(e.getX()-12*d)/Math.max(1,getWidth()-24*d)));if(row==0)hue=f*360;else tone=f;listener.changed(color());invalidate();return true;}
   if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);if(a==MotionEvent.ACTION_UP)performClick();return true;}return true;
 }
 @Override public boolean performClick(){super.performClick();return true;}
}
