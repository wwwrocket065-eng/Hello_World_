package com.example.arkrecruit;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.app.AlertDialog;

/** Shared native UI palette for the main activity and floating controls. */
public final class UiStyle {
  public static int BACKGROUND, SURFACE, RAISED, BORDER, TEXT, MUTED;
  public static final int ACCENT=0xFF39CDDF, TRACK=0xFF397B94;
  public static final Typeface FONT=Typeface.create("sans-serif-condensed",Typeface.NORMAL);
  private static final java.util.Map<android.view.View,Boolean> roots=new java.util.WeakHashMap<>();
  private static boolean light;
  static { palette(false); }
  private static void palette(boolean value){
    light=value;
    BACKGROUND=value?0xFFF0F0ED:0xFF202020;
    SURFACE=value?0xF2E6E6E3:0xF22D2D2D;
    RAISED=value?0xFFE0E0DD:0xFF393939;
    BORDER=value?0xFFB3B3B0:0xFF565656;
    TEXT=value?0xFF242424:0xFFF4F4F1;
    MUTED=value?0xFF595959:0xFFBCBCB9;
  }
  public static void init(Context c){palette(c.getSharedPreferences("settings",0).getBoolean("light_theme",false));}
  public static boolean isLight(){return light;}
  public static void watch(android.view.View root){roots.put(root,true);styleTree(root);}
  public static void theme(Context c,boolean value){
    int text=TEXT,muted=MUTED,bg=BACKGROUND,surface=SURFACE,raised=RAISED,border=BORDER;
    palette(value);
    c.getSharedPreferences("settings",0).edit().putBoolean("light_theme",value).apply();
    for(android.view.View root:new java.util.ArrayList<>(roots.keySet()))
      recolor(root,new int[]{text,muted,bg,surface,raised,border},new int[]{TEXT,MUTED,BACKGROUND,SURFACE,RAISED,BORDER});
  }
  private static void recolor(android.view.View v,int[] old,int[] colors){
    if(v instanceof android.widget.TextView){
      android.widget.TextView t=(android.widget.TextView)v;int color=t.getCurrentTextColor();
      for(int i=0;i<old.length;i++)if(color==old[i]){t.setTextColor(colors[i]);break;}
    }
    if(v.getBackground() instanceof android.graphics.drawable.ColorDrawable){
      int color=((android.graphics.drawable.ColorDrawable)v.getBackground()).getColor();
      for(int i=0;i<old.length;i++)if(color==old[i]){v.setBackgroundColor(colors[i]);break;}
    }
    if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)recolor(g.getChildAt(i),old,colors);}
    styleTreeOne(v);v.invalidate();
  }
  private static void styleTreeOne(android.view.View v){
    if(v instanceof android.widget.TextView){android.widget.TextView t=(android.widget.TextView)v;t.setTypeface(FONT,t.getTypeface()!=null?t.getTypeface().getStyle():0);}
  }
  public static void styleTree(android.view.View v){styleTreeOne(v);if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)styleTree(g.getChildAt(i));}}
  private UiStyle() {}

  public static int dp(Context c, int dp) {
    return (int)(dp*c.getResources().getDisplayMetrics().density+0.5f);
  }
  public static GradientDrawable rounded(Context c, int color, int radius) {
    final int role=color==BACKGROUND?0:color==SURFACE?1:color==RAISED?2:-1;
    GradientDrawable result = new GradientDrawable(){
      @Override public void draw(android.graphics.Canvas canvas){
        setColor(role==0?BACKGROUND:role==1?SURFACE:role==2?RAISED:color);
        setStroke(dp(c,1),BORDER);super.draw(canvas);
      }
    };
    result.setColor(color);
    result.setCornerRadius(0);
    result.setStroke(dp(c,1),BORDER);
    return result;
  }
  public static void button(Button b, boolean primary) {
    Context c=b.getContext();
    int bg=primary?ACCENT:RAISED;
    int ink=primary?0xFF142D36:TEXT;
    b.setAllCaps(false);
    b.setTextColor(ink);
    b.setTextSize(15);
    b.setTypeface(FONT,Typeface.BOLD);
    b.setMinHeight(dp(c,46));
    b.setPadding(dp(c,10),dp(c,8),dp(c,10),dp(c,8));
    GradientDrawable shape=rounded(c,bg,12);
    b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x55223441:0x6656DCC7),
      shape,rounded(c,Color.WHITE,12)));
  }
  public static void toggle(Switch sw) {
    sw.setTextColor(TEXT);
    sw.setTextSize(14);
    int[][] states={{android.R.attr.state_checked},{-android.R.attr.state_checked}};
    sw.setThumbTintList(new ColorStateList(states,new int[]{ACCENT,0xFFAFBEC7}));
    sw.setTrackTintList(new ColorStateList(states,new int[]{TRACK,0xFF435763}));
  }
  public static void slider(SeekBar slider) {
    slider.setProgressTintList(ColorStateList.valueOf(TRACK));
    slider.setThumbTintList(ColorStateList.valueOf(ACCENT));
    slider.setProgressBackgroundTintList(ColorStateList.valueOf(BORDER));
  }
  public static void input(EditText edit) {
    Context c=edit.getContext();
    edit.setTextColor(TEXT);
    edit.setHintTextColor(MUTED);
    edit.setBackground(rounded(c,SURFACE,12));
    edit.setPadding(dp(c,14),dp(c,12),dp(c,14),dp(c,12));
  }
  public static void progress(ProgressBar progress) {
    progress.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
  }
  public static void dialog(AlertDialog dialog) {
    if(dialog.getWindow()!=null)
      dialog.getWindow().setBackgroundDrawable(rounded(dialog.getContext(),SURFACE,18));
    watch(dialog.getWindow().getDecorView());
    Button close=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if(close!=null)button(close,false);
  }
}
