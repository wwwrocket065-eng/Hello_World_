package com.example.arkrecruit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Expandable highlight-color palette used by the app and the floating settings. */
public final class HighlightColorControl extends LinearLayout {
  private static final int[] COLORS = {
    0xFF00FF5A, 0xFF48DBBC, 0xFF32B9FF, 0xFF697DFF, 0xFFB47CFF,
    0xFFFF6FC3, 0xFFFF667B, 0xFFFF9347, 0xFFFFCC4C, 0xFFC4E85A,
    0xFF12A567, 0xFF208F95, 0xFF2773B0, 0xFF565FA4, 0xFF8452AA,
    0xFFC04F91, 0xFFBB5B55, 0xFFB77C4C, 0xFF8D8C51, 0xFF778995
  };
  private final SharedPreferences prefs;
  private final Runnable onChange;
  private final Button trigger;
  private final GridLayout palette;

  public HighlightColorControl(Context context, SharedPreferences prefs, Runnable onChange) {
    super(context);
    this.prefs=prefs;
    this.onChange=onChange;
    setOrientation(VERTICAL);
    trigger=new Button(context);
    UiStyle.button(trigger,false);
    LayoutParams mainButtonLayout=new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
    mainButtonLayout.setMargins(0,UiStyle.dp(context,5),0,UiStyle.dp(context,5));
    addView(trigger,mainButtonLayout);

    palette=new GridLayout(context);
    palette.setColumnCount(5);
    palette.setPadding(UiStyle.dp(context,3),UiStyle.dp(context,8),UiStyle.dp(context,3),UiStyle.dp(context,9));
    palette.setBackground(UiStyle.rounded(context,UiStyle.SURFACE,12));
    addView(palette,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
    for(int color:COLORS){
      Button tile=new Button(context);
      tile.setContentDescription(String.format(java.util.Locale.US,"Цвет #%06X",color & 0xFFFFFF));
      tile.setText("");
      tile.setMinWidth(0);
      tile.setMinHeight(0);
      tile.setPadding(0,0,0,0);
      tile.setBackground(UiStyle.rounded(context,color,9));
      GridLayout.LayoutParams cell=new GridLayout.LayoutParams();
      cell.width=UiStyle.dp(context,42);
      cell.height=UiStyle.dp(context,42);
      cell.setMargins(UiStyle.dp(context,3),UiStyle.dp(context,3),UiStyle.dp(context,3),UiStyle.dp(context,3));
      palette.addView(tile,cell);
      tile.setOnClickListener(v->selectColor(color));
    }
    palette.setVisibility(GONE);
    updateTrigger();
    trigger.setOnClickListener(v->{
      palette.setVisibility(palette.getVisibility()==VISIBLE?GONE:VISIBLE);
      updateTrigger();
    });
  }

  private int selectedColor(){
    return Color.rgb(prefs.getInt("red",0),prefs.getInt("green",255),prefs.getInt("blue",90));
  }
  private void updateTrigger(){
    int color=selectedColor();
    boolean expanded=palette.getVisibility()==VISIBLE;
    trigger.setText("Цвет подсветки  "+(expanded?"▴":"▾"));
    // Text remains readable even when the chosen palette color is very bright.
    int brightness=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;
    trigger.setTextColor(brightness>=150?UiStyle.BACKGROUND:UiStyle.TEXT);
    trigger.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55777777),
      UiStyle.rounded(getContext(),color,12),null));
  }
  private void selectColor(int color){
    prefs.edit().putInt("red",Color.red(color)).putInt("green",Color.green(color))
      .putInt("blue",Color.blue(color)).apply();
    updateTrigger();
    if(onChange!=null)onChange.run();
  }
}
