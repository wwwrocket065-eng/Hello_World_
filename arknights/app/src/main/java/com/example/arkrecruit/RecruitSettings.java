package com.example.arkrecruit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
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
  public static void addPanel(LinearLayout parent,SharedPreferences prefs,Runnable changed){
    Context context=parent.getContext();
    LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);
    TextView label=new TextView(context);label.setText("Панель операторов");label.setTextSize(14);label.setTextColor(Color.WHITE);
    row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    CycleToggle control=new CycleToggle(context,2);
    Runnable update=()->{
      boolean on=prefs.getBoolean("panel",true);
      control.setContentDescription("Панель операторов: "+(on?"включена":"выключена"));
      control.setState(on?1:0,UiStyle.ACCENT,on);
    };
    update.run();
    control.setOnClickListener(v->{prefs.edit().putBoolean("panel",!prefs.getBoolean("panel",true)).apply();update.run();if(changed!=null)changed.run();});
    row.addView(control,new LinearLayout.LayoutParams(UiStyle.dp(context,40),UiStyle.dp(context,42)));
    parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
  private static void add(LinearLayout parent,SharedPreferences prefs,boolean region,Runnable changed){
    Context context=parent.getContext();
    LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);
    {
      TextView prefix=new TextView(context);prefix.setText(region?"Сервер: ":"Список операторов: ");prefix.setTextSize(14);prefix.setTextColor(Color.WHITE);prefix.setSingleLine(true);
      row.addView(prefix,new LinearLayout.LayoutParams(-2,-2));
    }
    TextView label=new TextView(context);
    label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
    label.setMarqueeRepeatLimit(-1);label.setSelected(true);label.setTextSize(14);label.setTextColor(Color.WHITE);
    row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    CycleToggle control=new CycleToggle(context,region?2:3);
    row.addView(control,new LinearLayout.LayoutParams(UiStyle.dp(context,region?40:60),UiStyle.dp(context,42)));
    Runnable update=()->{
      int mode=filter(prefs),level=mode==2?1:mode==1?2:3;
      String value=mode==0?"Все":mode==1?"Теги":"Выбранные";
      label.setText(region?region(prefs):value);
      label.setTextColor(region?regionColor(prefs):Color.WHITE);
      control.setContentDescription(region?"Сервер: "+region(prefs):"Список операторов: "+value+", "+level+" из 3");
      control.setState(region?(prefs.getBoolean("china",false)?1:0):level-1,
        region?regionColor(prefs):UiStyle.ACCENT,region||level>1);
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
