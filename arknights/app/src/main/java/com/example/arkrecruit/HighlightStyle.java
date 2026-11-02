package com.example.arkrecruit;
import android.content.SharedPreferences;
import android.graphics.Color;
public final class HighlightStyle {
  public static final int GOLD=0xFFFFB600;
  private HighlightStyle(){}
  public static void ensure(SharedPreferences p){
    if(p.getInt("highlight_style",0)<1){reset(p);p.edit().putInt("highlight_style",1).apply();}
  }
  public static void reset(SharedPreferences p){
    p.edit().putInt("red",255).putInt("green",182).putInt("blue",0).putInt("highlightTransparency",20)
      .putInt("paletteBrightness",100).putInt("fog",35).putBoolean("twinkle",true).apply();
  }
  public static int color(SharedPreferences p){return Color.rgb(p.getInt("red",255),p.getInt("green",182),p.getInt("blue",0));}
}
