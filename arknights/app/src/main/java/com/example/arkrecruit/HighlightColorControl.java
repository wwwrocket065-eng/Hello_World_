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
import android.widget.SeekBar;

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
  private final LinearLayout expanded;
  private final TextView brightnessText;
  private final SeekBar brightnessBar;

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

    expanded=new LinearLayout(context);
    expanded.setOrientation(VERTICAL);
    expanded.setPadding(UiStyle.dp(context,3),UiStyle.dp(context,8),
      UiStyle.dp(context,3),UiStyle.dp(context,9));
    expanded.setBackground(UiStyle.rounded(context,UiStyle.SURFACE,12));
    addView(expanded,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
    palette=new GridLayout(context);
    palette.setColumnCount(5);
    palette.setPadding(UiStyle.dp(context,3),UiStyle.dp(context,8),UiStyle.dp(context,3),UiStyle.dp(context,9));
    expanded.addView(palette,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
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
    brightnessText=new TextView(context);
    brightnessText.setTextColor(UiStyle.TEXT);
    brightnessText.setTextSize(14);
    brightnessText.setPadding(UiStyle.dp(context,6),UiStyle.dp(context,7),0,0);
    expanded.addView(brightnessText);
    brightnessBar=new SeekBar(context);
    brightnessBar.setMax(100);
    float[] hsv=new float[3];
    Color.colorToHSV(selectedColor(),hsv);
    int currentBrightness=prefs.getInt("paletteBrightness",Math.round(hsv[2]*100f));
    brightnessBar.setProgress(Math.max(0,Math.min(100,currentBrightness)));
    UiStyle.slider(brightnessBar);
    expanded.addView(brightnessBar,new LayoutParams(LayoutParams.MATCH_PARENT,
      LayoutParams.WRAP_CONTENT));
    updateBrightnessLabel();
    brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      @Override public void onProgressChanged(SeekBar slider,int percent,boolean fromUser){
        if(fromUser){
          int base=prefs.getInt("paletteBaseColor",baseColorFromCurrent());
          applyColor(base,percent);
        }
      }
      @Override public void onStartTrackingTouch(SeekBar slider){}
      @Override public void onStopTrackingTouch(SeekBar slider){}
    });
    expanded.setVisibility(GONE);
    updateTrigger();
    trigger.setOnClickListener(v->{
      expanded.setVisibility(expanded.getVisibility()==VISIBLE?GONE:VISIBLE);
      updateTrigger();
    });
  }

  private int selectedColor(){
    return Color.rgb(prefs.getInt("red",0),prefs.getInt("green",255),prefs.getInt("blue",90));
  }
  private int baseColorFromCurrent(){
    float[] hsv=new float[3];
    Color.colorToHSV(selectedColor(),hsv);
    hsv[2]=1f;
    return Color.HSVToColor(hsv);
  }
  private void updateBrightnessLabel(){
    brightnessText.setText("Яркость: "+brightnessBar.getProgress()+"%");
  }
  private void applyColor(int base,int percent){
    float[] hsv=new float[3];
    Color.colorToHSV(base,hsv);
    hsv[2]=Math.max(0,Math.min(100,percent))/100f;
    int result=Color.HSVToColor(hsv);
    prefs.edit().putInt("paletteBaseColor",base).putInt("paletteBrightness",percent)
      .putInt("red",Color.red(result)).putInt("green",Color.green(result))
      .putInt("blue",Color.blue(result)).apply();
    updateBrightnessLabel();
    updateTrigger();
    if(onChange!=null)onChange.run();
  }
  private void updateTrigger(){
    int color=selectedColor();
    boolean expandedNow=expanded.getVisibility()==VISIBLE;
    trigger.setText("Цвет подсветки  "+(expandedNow?"▴":"▾"));
    // Text remains readable even when the chosen palette color is very bright.
    int brightness=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;
    trigger.setTextColor(brightness>=150?UiStyle.BACKGROUND:UiStyle.TEXT);
    trigger.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55777777),
      UiStyle.rounded(getContext(),color,12),null));
  }
  private void selectColor(int color){
    applyColor(color,brightnessBar.getProgress());
  }
}
