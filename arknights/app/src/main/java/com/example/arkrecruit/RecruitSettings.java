package com.example.arkrecruit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.*;

/** Shared tap-to-cycle controls; stored filter values retain their old meaning. */
public final class RecruitSettings {
  public static final int CHINA_COLOR=0xFFBE888D;
  public static final int GLOBAL_COLOR=0xFF8B9FBD;
  private RecruitSettings(){}
  public static int filter(SharedPreferences prefs){
    return Math.max(0,Math.min(2,prefs.getInt("target_filter",prefs.getBoolean("only_current_tags",true)?1:0)));
  }
  public static String region(SharedPreferences prefs){return prefs.getBoolean("china",false)?"China":"Global";}
  public static int regionColor(SharedPreferences prefs){return prefs.getBoolean("china",false)?CHINA_COLOR:GLOBAL_COLOR;}
  public static void addRegion(LinearLayout parent,SharedPreferences prefs,Runnable changed){
    add(parent,prefs,true,changed);
  }
  public static void addFilter(LinearLayout parent,SharedPreferences prefs,Runnable changed){
    add(parent,prefs,false,changed);
  }
  private static void add(LinearLayout parent,SharedPreferences prefs,boolean region,Runnable changed){
    Context context=parent.getContext();
    LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);
    {
      TextView prefix=new TextView(context);prefix.setText(region?"Сервер: ":"Выбор цели: ");prefix.setTextSize(14);prefix.setTextColor(Color.WHITE);prefix.setSingleLine(true);
      row.addView(prefix,new LinearLayout.LayoutParams(-2,-2));
    }
    TextView label=new TextView(context);
    label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
    label.setMarqueeRepeatLimit(-1);label.setSelected(true);label.setTextSize(14);label.setTextColor(Color.WHITE);
    row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    Button control=new Button(context);UiStyle.button(control,false);
    control.setMinWidth(0);control.setMinimumWidth(0);control.setMinHeight(0);control.setMinimumHeight(0);
    control.setPadding(UiStyle.dp(context,4),0,UiStyle.dp(context,4),0);control.setTextSize(13);
    row.addView(control,new LinearLayout.LayoutParams(UiStyle.dp(context,82),UiStyle.dp(context,42)));
    Runnable update=()->{
      int mode=filter(prefs),level=mode==2?1:mode==1?2:3;
      String value=mode==0?"Все операторы":mode==1?"Текущие теги":"Выбранные операторы";
      label.setText(region?region(prefs):value);
      label.setTextColor(region?regionColor(prefs):Color.WHITE);
      control.setText(region?"↔":level+" / 3");
      control.setContentDescription(region?"Сервер: "+region(prefs):"Выбор цели: "+value+", "+level+" из 3");
      int background=region?(prefs.getBoolean("china",false)?0xFF78545A:0xFF4B6489):level==3?UiStyle.ACCENT:level==2?0xFF31534F:UiStyle.RAISED;
      control.setTextColor(!region&&level==3?UiStyle.BACKGROUND:Color.WHITE);
      control.setBackground(new RippleDrawable(ColorStateList.valueOf(0x4456DCC7),UiStyle.rounded(context,background,12),UiStyle.rounded(context,Color.WHITE,12)));
    };
    update.run();
    control.setOnClickListener(v->{
      if(region)prefs.edit().putBoolean("china",!prefs.getBoolean("china",false)).apply();
      else prefs.edit().putInt("target_filter",FilterCycle.next(filter(prefs))).apply();
      update.run();if(changed!=null)changed.run();
    });
    parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
}
