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
  public static final int BACKGROUND = 0xFF121B23;
  public static final int SURFACE = 0xFF1D2B36;
  public static final int RAISED = 0xFF293B47;
  public static final int BORDER = 0xFF3A5260;
  public static final int ACCENT = 0xFF56DCC7;
  public static final int TEXT = 0xFFF2F7F9;
  public static final int MUTED = 0xFFBACBD2;
  private UiStyle() {}

  public static int dp(Context c, int dp) {
    return (int)(dp*c.getResources().getDisplayMetrics().density+0.5f);
  }
  public static GradientDrawable rounded(Context c, int color, int radius) {
    GradientDrawable result = new GradientDrawable();
    result.setColor(color);
    result.setCornerRadius(dp(c,radius));
    result.setStroke(dp(c,1),BORDER);
    return result;
  }
  public static void button(Button b, boolean primary) {
    Context c=b.getContext();
    int bg=primary?ACCENT:RAISED;
    int ink=primary?BACKGROUND:TEXT;
    b.setAllCaps(false);
    b.setTextColor(ink);
    b.setTextSize(15);
    b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
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
    sw.setTrackTintList(new ColorStateList(states,new int[]{0xAA56DCC7,0xFF435763}));
  }
  public static void slider(SeekBar slider) {
    slider.setProgressTintList(ColorStateList.valueOf(ACCENT));
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
    Button close=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
    if(close!=null)button(close,false);
  }
}
