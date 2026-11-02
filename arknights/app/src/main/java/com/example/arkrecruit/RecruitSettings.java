package com.example.arkrecruit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

/** Shared discrete settings controls for both settings surfaces. */
public final class RecruitSettings {
  public static final int CHINA_COLOR=0xFFBE888D;
  private RecruitSettings(){}
  public static int filter(SharedPreferences prefs){
    return Math.max(0,Math.min(2,prefs.getInt("target_filter",prefs.getBoolean("only_current_tags",true)?1:0)));
  }
  public static String region(SharedPreferences prefs){return prefs.getBoolean("china",false)?"China":"Global";}
  public static int regionColor(SharedPreferences prefs){return prefs.getBoolean("china",false)?CHINA_COLOR:UiStyle.MUTED;}
  public static void addRegion(LinearLayout parent,SharedPreferences prefs,Runnable changed){
    add(parent,prefs,"china",new String[]{"Global","China"},prefs.getBoolean("china",false)?1:0,true,changed);
  }
  public static void addFilter(LinearLayout parent,SharedPreferences prefs,Runnable changed){
    add(parent,prefs,"target_filter",new String[]{"Все операторы","Текущие теги","Выбранные операторы"},filter(prefs),false,changed);
  }
  private static void add(LinearLayout parent,SharedPreferences prefs,String key,String[] labels,int initial,boolean region,Runnable changed){
    Context context=parent.getContext();
    LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);
    if(!region){
      TextView prefix=new TextView(context);prefix.setText("Выбор цели: ");prefix.setTextSize(14);prefix.setTextColor(Color.WHITE);prefix.setSingleLine(true);
      row.addView(prefix,new LinearLayout.LayoutParams(-2,-2));
    }
    TextView label=new TextView(context);
    label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
    label.setMarqueeRepeatLimit(-1);label.setSelected(true);
    label.setTextSize(14);label.setText(labels[initial]);label.setTextColor(region?regionColor(prefs):Color.WHITE);
    row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    SeekBar control=new SeekBar(context,null,0,android.R.style.Widget_Material_SeekBar_Discrete);
    control.setMax(labels.length-1);control.setProgress(initial);
    control.setContentDescription(labels[initial]);
    Runnable tint=()->{
      int color=region?regionColor(prefs):UiStyle.ACCENT;
      control.setThumbTintList(ColorStateList.valueOf(color));control.setProgressTintList(ColorStateList.valueOf(color));
      control.setProgressBackgroundTintList(ColorStateList.valueOf(UiStyle.BORDER));control.setTickMarkTintList(ColorStateList.valueOf(color));
    };
    tint.run();
    row.addView(control,new LinearLayout.LayoutParams(UiStyle.dp(context,82),UiStyle.dp(context,42)));
    control.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int value,boolean fromUser){
        if(!fromUser)return;
        if(region)prefs.edit().putBoolean(key,value==1).apply();else prefs.edit().putInt(key,value).apply();
        label.setText(labels[value]);label.setTextColor(region?regionColor(prefs):Color.WHITE);
        bar.setContentDescription(labels[value]);tint.run();if(changed!=null)changed.run();
      }
      public void onStartTrackingTouch(SeekBar bar){}
      public void onStopTrackingTouch(SeekBar bar){}
    });
    parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
}
