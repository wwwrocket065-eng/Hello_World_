package com.example.arkrecruit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Lightweight floating control. Drawn icons prevent system emoji/color badges. */
public final class FloatingControlButton extends View {
  public static final int PLAY_PAUSE=0, SETTINGS=1;
  private static final int ICON_COLOR=UiStyle.ACCENT;
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final int kind;
  private boolean playing;

  public FloatingControlButton(Context context,int kind) {
    super(context);
    this.kind=kind;
    setClickable(true);
    setFocusable(true);
    updateDescription();
  }

  public void setPlaying(boolean playing) {
    if(this.playing==playing)return;
    this.playing=playing;
    updateDescription();
    invalidate();
  }

  private void updateDescription() {
    setContentDescription(kind==SETTINGS?"Настройки":playing?"Пауза":"Запустить анализ");
  }

  @Override protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    final float s=Math.min(getWidth(),getHeight())/52f;
    final float cx=getWidth()/2f,cy=getHeight()/2f;
    p.reset();p.setAntiAlias(true);p.setColor(isPressed()?0xFF354D59:UiStyle.RAISED);
    p.setStyle(Paint.Style.FILL);
    canvas.drawRoundRect(2*s,2*s,getWidth()-2*s,getHeight()-2*s,0,0,p);
    p.setColor(kind==SETTINGS?UiStyle.MUTED:ICON_COLOR);
    if(kind==SETTINGS)drawGear(canvas,cx,cy,s);
    else if(playing)drawPause(canvas,cx,cy,s);
    else drawPlay(canvas,cx,cy,s);
  }

  private void drawPause(Canvas c,float x,float y,float s) {
    // Two identical soft-corner bars, no system emoji or coloured dot.
    c.drawRoundRect(x-10*s,y-12*s,x-3*s,y+12*s,2.4f*s,2.4f*s,p);
    c.drawRoundRect(x+3*s,y-12*s,x+10*s,y+12*s,2.4f*s,2.4f*s,p);
  }

  private void drawPlay(Canvas c,float x,float y,float s) {
    // A real filled triangle with rounded geometry, not a Unicode glyph.
    Path path=new Path();
    path.moveTo(x-9*s,y-11*s);
    path.quadTo(x-9*s,y-14*s,x-5.7f*s,y-12.4f*s);
    path.lineTo(x+12.5f*s,y-2.2f*s);
    path.quadTo(x+16*s,y,x+12.5f*s,y+2.2f*s);
    path.lineTo(x-5.7f*s,y+12.4f*s);
    path.quadTo(x-9*s,y+14*s,x-9*s,y+11*s);
    path.close();
    c.drawPath(path,p);
  }

  private void drawGear(Canvas c,float x,float y,float s) {
    p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeWidth(3.1f*s);
    c.drawCircle(x,y,10*s,p);
    c.drawCircle(x,y,3.5f*s,p);
    p.setStrokeWidth(4*s);
    for(int i=0;i<8;i++) {
      double a=Math.PI*i/4d;
      float dx=(float)Math.cos(a),dy=(float)Math.sin(a);
      c.drawLine(x+12*s*dx,y+12*s*dy,x+15*s*dx,y+15*s*dy,p);
    }
    p.setStyle(Paint.Style.FILL);
  }

  @Override public void setPressed(boolean pressed) {
    super.setPressed(pressed);invalidate();
  }
}
