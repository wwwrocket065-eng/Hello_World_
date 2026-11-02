package com.example.arkrecruit;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Full-width entry into a nested highlight page; never an expanding palette. */
public final class HighlightColorControl extends LinearLayout {
  private static final Map<LinearLayout,java.lang.ref.WeakReference<Runnable>> BACK=new WeakHashMap<>();
  private final SharedPreferences prefs;
  private final Runnable changed;
  public HighlightColorControl(Context context,SharedPreferences prefs,Runnable changed){
    super(context);this.prefs=prefs;this.changed=changed;HighlightStyle.ensure(prefs);
    setOrientation(VERTICAL);
    Button entry=new Button(context);UiStyle.button(entry,false);entry.setText("Подсветка");entry.setTextColor(Color.WHITE);
    entry.setBackground(new Drawable(){
      final HighlightRenderer renderer=new HighlightRenderer();
      public void draw(Canvas c){Rect b=getBounds();renderer.draw(c,new RectF(b),prefs,0,13,null);}
      public void setAlpha(int a){}
      public void setColorFilter(ColorFilter filter){}
      public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    });
    FrameLayout entryRow=new FrameLayout(context);
    entryRow.addView(entry,new FrameLayout.LayoutParams(-1,-1));
    TextView arrow=new TextView(context);arrow.setText("›");arrow.setTextColor(Color.WHITE);arrow.setTextSize(26);arrow.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams arrowLayout=new FrameLayout.LayoutParams(UiStyle.dp(context,42),-1,Gravity.RIGHT|Gravity.CENTER_VERTICAL);
    entryRow.addView(arrow,arrowLayout);arrow.setClickable(false);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(entryRow,new LayoutParams(-1,UiStyle.dp(context,50)));
    entry.setOnClickListener(v->open());
  }
  public static boolean back(LinearLayout host){java.lang.ref.WeakReference<Runnable> ref=BACK.get(host);Runnable back=ref==null?null:ref.get();if(back==null)return false;back.run();return true;}
  private void open(){
    if(!(getParent() instanceof LinearLayout))return;
    LinearLayout host=(LinearLayout)getParent();
    LinearLayout page=new LinearLayout(getContext());page.setOrientation(VERTICAL);
    final SettingsPageTransition.Session[] session=new SettingsPageTransition.Session[1];
    Runnable restore=()->{BACK.remove(host);if(session[0]!=null)session[0].close(null);};
    BACK.put(host,new java.lang.ref.WeakReference<>(restore));
    LinearLayout heading=new LinearLayout(getContext());heading.setGravity(Gravity.CENTER_VERTICAL);
    Button back=new Button(getContext());back.setText("‹");UiStyle.button(back,false);back.setTextSize(24);back.setBackgroundColor(Color.TRANSPARENT);back.setContentDescription("Назад");
    heading.addView(back,new LayoutParams(UiStyle.dp(getContext(),44),UiStyle.dp(getContext(),44)));back.setOnClickListener(v->restore.run());
    TextView title=new TextView(getContext());title.setText("Подсветка");title.setTextColor(Color.WHITE);title.setTextSize(20);title.setPadding(UiStyle.dp(getContext(),10),0,0,0);
    heading.addView(title,new LayoutParams(0,-2,1));page.addView(heading);
    final View preview=new View(getContext()){
      final HighlightRenderer renderer=new HighlightRenderer();
      protected void onDraw(Canvas c){
        RectF r=new RectF(8,10,getWidth()-8,getHeight()-10);
        renderer.draw(c,r,prefs,android.os.SystemClock.uptimeMillis(),13,null);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(UiStyle.dp(getContext(),14));text.setTextAlign(Paint.Align.CENTER);
        c.drawText("Пример тега",getWidth()/2f,getHeight()/2f+text.getTextSize()/3,text);
        if(isShown())postInvalidateDelayed(50);
      }
    };
    page.addView(preview,new LayoutParams(-1,UiStyle.dp(getContext(),70)));
    float[] hsv=new float[3];Color.colorToHSV(HighlightStyle.color(prefs),hsv);
    ColorWheel wheel=new ColorWheel(getContext(),HighlightStyle.color(prefs),(h,s)->{
      hsv[0]=h;hsv[1]=s;saveColor(hsv);preview.invalidate();
    });
    page.addView(wheel,new LayoutParams(-1,UiStyle.dp(getContext(),200)));
    SeekBar brightness=slider(page,"Яркость",Math.round(hsv[2]*100),value->{hsv[2]=value/100f;saveColor(hsv);preview.invalidate();});
    SeekBar transparency=slider(page,"Прозрачность подсветки",prefs.getInt("highlightTransparency",20),value->{prefs.edit().putInt("highlightTransparency",value).apply();notifyChange();preview.invalidate();});
    ArrayList<Runnable> toggles=new ArrayList<>();
    component(page,"Рамка","showFrame",preview,toggles);
    component(page,"Заливка","showFill",preview,toggles);
    component(page,"Звёзды","showStars",preview,toggles);
    Button reset=new Button(getContext());reset.setText("Сбросить настройки подсветки");UiStyle.button(reset,false);page.addView(reset,new LayoutParams(-1,-2));
    reset.setOnClickListener(v->{
      HighlightStyle.reset(prefs);Color.colorToHSV(HighlightStyle.GOLD,hsv);wheel.setColor(HighlightStyle.GOLD);
      brightness.setProgress(100);transparency.setProgress(20);for(Runnable update:toggles)update.run();
      notifyChange();preview.invalidate();
    });
    session[0]=SettingsPageTransition.open(host,page);
  }
  private void component(LinearLayout page,String name,String key,View preview,ArrayList<Runnable> updates){
    LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);
    TextView label=new TextView(getContext());label.setText(name);label.setTextColor(Color.WHITE);label.setTextSize(14);
    row.addView(label,new LayoutParams(0,-2,1));
    CycleToggle toggle=new CycleToggle(getContext(),2);row.addView(toggle,new LayoutParams(UiStyle.dp(getContext(),40),UiStyle.dp(getContext(),42)));
    Runnable update=()->{boolean on=prefs.getBoolean(key,true);toggle.setState(on?1:0,UiStyle.ACCENT,on);toggle.setContentDescription(name+": "+(on?"включено":"выключено"));};
    update.run();updates.add(update);
    toggle.setOnClickListener(v->{prefs.edit().putBoolean(key,!prefs.getBoolean(key,true)).apply();update.run();notifyChange();preview.invalidate();});
    page.addView(row);
  }
  private interface Value{void set(int value);}
  private SeekBar slider(LinearLayout page,String name,int value,Value change){
    TextView label=new TextView(getContext());label.setTextColor(Color.WHITE);label.setTextSize(14);label.setText(name+": "+value+"%");page.addView(label);
    SeekBar slider=new SeekBar(getContext());slider.setMax(100);slider.setProgress(value);UiStyle.slider(slider);page.addView(slider,new LayoutParams(-1,-2));
    slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar bar,int progress,boolean user){{label.setText(name+": "+progress+"%");if(user)change.set(progress);}}
      public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}
    });
    return slider;
  }
  private void saveColor(float[] hsv){int color=Color.HSVToColor(hsv);prefs.edit().putInt("red",Color.red(color)).putInt("green",Color.green(color)).putInt("blue",Color.blue(color)).apply();notifyChange();}
  private void notifyChange(){invalidate();if(changed!=null)changed.run();}
}
