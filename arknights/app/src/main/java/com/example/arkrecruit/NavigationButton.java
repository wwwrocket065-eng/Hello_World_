package com.example.arkrecruit;
import android.content.Context;
import android.graphics.*;
import android.widget.Button;
public final class NavigationButton extends Button {
  private final boolean back; private final Paint p=new Paint(3);
  public NavigationButton(Context c,String label,boolean back){super(c);this.back=back;setText(label);UiStyle.button(this,false);if(back){setBackgroundColor(Color.TRANSPARENT);setContentDescription("Назад");}}
  @Override protected void onDraw(Canvas c){super.onDraw(c);float d=getResources().getDisplayMetrics().density,x=back?getWidth()/2f:getWidth()-22*d,y=getHeight()/2f,sign=back?-1:1;
    p.setColor(getCurrentTextColor());p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2*d);p.setStrokeCap(Paint.Cap.SQUARE);Path path=new Path();path.moveTo(x-sign*3*d,y-6*d);path.lineTo(x+sign*3*d,y);path.lineTo(x-sign*3*d,y+6*d);c.drawPath(path,p);}
}
