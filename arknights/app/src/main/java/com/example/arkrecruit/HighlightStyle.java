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
      .putInt("paletteBrightness",100).putBoolean("showFrame",true).putBoolean("showFill",true).putBoolean("showStars",true).apply();
  }
  public static float brightness(SharedPreferences p){return Math.max(0,Math.min(200,p.getInt("paletteBrightness",100)))/100f;}
  public static int boosted(int color,float gain){
    int r=Color.red(color),g=Color.green(color),b=Color.blue(color);
    if(gain<=1)return Color.rgb(Math.round(r*gain),Math.round(g*gain),Math.round(b*gain));
    float white=(gain-1)*.35f;
    return Color.rgb(Math.round(r+(255-r)*white),Math.round(g+(255-g)*white),Math.round(b+(255-b)*white));
  }
  public static int color(SharedPreferences p){return Color.rgb(p.getInt("red",255),p.getInt("green",182),p.getInt("blue",0));}
}
