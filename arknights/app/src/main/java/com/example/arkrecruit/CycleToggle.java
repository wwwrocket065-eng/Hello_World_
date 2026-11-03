package com.example.arkrecruit;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** Tap-only capsule switch: same track/thumb geometry for two or three states. */
public final class CycleToggle extends View {
  private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int positions;
  private float fraction;
  private int color=UiStyle.ACCENT;
  private boolean active=true,initialized;
  private ValueAnimator animation;
  public CycleToggle(Context context,int positions){
    super(context);this.positions=positions;setClickable(true);setFocusable(true);
  }
  public void setState(int position,int color,boolean active){
    float destination=position/(float)(positions-1);
    this.color=color;this.active=active;
    if(animation!=null)animation.cancel();
    if(initialized&&isAttachedToWindow()){
      animation=ValueAnimator.ofFloat(fraction,destination);animation.setDuration(140);
      animation.addUpdateListener(a->{fraction=(float)a.getAnimatedValue();invalidate();});animation.start();
    }else fraction=destination;
    initialized=true;invalidate();
  }
  @Override protected void onDraw(Canvas canvas){
    super.onDraw(canvas);
    float density=getResources().getDisplayMetrics().density;
    float cy=getHeight()/2f,left=3*density,right=getWidth()-3*density;
    float radius=10*density,halfTrack=7*density;
    // Neutral rail remains visible beyond the current three-state thumb.
    paint.setColor(UiStyle.BORDER);
    canvas.drawRoundRect(left,cy-halfTrack,right,cy+halfTrack,halfTrack,halfTrack,paint);
    float x=left+radius+(right-left-radius*2)*fraction;
    if(active){
      paint.setColor(color==UiStyle.ACCENT?UiStyle.TRACK:(color&0x00FFFFFF)|0x99000000);
      if(positions==3){
        int saved=canvas.save();canvas.clipRect(left,cy-halfTrack,x,cy+halfTrack);
        canvas.drawRoundRect(left,cy-halfTrack,right,cy+halfTrack,halfTrack,halfTrack,paint);
        canvas.restoreToCount(saved);
      }else canvas.drawRoundRect(left,cy-halfTrack,right,cy+halfTrack,halfTrack,halfTrack,paint);
    }
    paint.setColor(active?color:UiStyle.MUTED);canvas.drawCircle(x,cy,radius,paint);
    if(isPressed()||isFocused()){
      paint.setColor((color&0x00FFFFFF)|0x22000000);canvas.drawCircle(x,cy,radius+4*density,paint);
    }
  }
  @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(positions==2?"android.widget.Switch":"android.widget.Button");
    if(positions==2){info.setCheckable(true);info.setChecked(fraction>.5f);}
  }
  @Override protected void onDetachedFromWindow(){if(animation!=null)animation.cancel();super.onDetachedFromWindow();}
}
