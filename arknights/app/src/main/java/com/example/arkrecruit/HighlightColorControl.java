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
    EntryButton entry=new EntryButton(context);
    FrameLayout entryRow=new FrameLayout(context);
    entryRow.addView(entry,new FrameLayout.LayoutParams(-1,-2));
    LayoutParams entryLayout=new LayoutParams(-1,-2);
    entryLayout.setMargins(0,UiStyle.dp(context,5),0,UiStyle.dp(context,5));
    addView(entryRow,entryLayout);
    entry.setOnClickListener(v->open());
  }
  private final class EntryButton extends Button {
    private final Paint arrow=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path chevron=new Path();
    EntryButton(Context context){super(context);UiStyle.button(this,true);setText("Подсветка");updateColor();}
    private void updateColor(){
      int color=HighlightStyle.color(prefs);
      int ink=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000>=150?UiStyle.BACKGROUND:Color.WHITE;
      setTextColor(ink);arrow.setColor(ink);
      setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x44777777),UiStyle.rounded(getContext(),color,12),null));
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();updateColor();}
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);float d=getResources().getDisplayMetrics().density;
      float x=getWidth()-22*d,y=getHeight()/2f;
      arrow.setStyle(Paint.Style.STROKE);arrow.setStrokeWidth(2*d);arrow.setStrokeCap(Paint.Cap.ROUND);arrow.setStrokeJoin(Paint.Join.ROUND);
      chevron.reset();chevron.moveTo(x-3*d,y-6*d);chevron.lineTo(x+3*d,y);chevron.lineTo(x-3*d,y+6*d);canvas.drawPath(chevron,arrow);
    }
  }
  public static boolean back(LinearLayout host){java.lang.ref.WeakReference<Runnable> ref=BACK.get(host);Runnable back=ref==null?null:ref.get();if(back==null)return false;back.run();return true;}
  private void open(){
    if(!(getParent() instanceof LinearLayout))return;
    LinearLayout host=(LinearLayout)getParent();
    if(BACK.containsKey(host))return;
    LinearLayout page=new LinearLayout(getContext());page.setOrientation(VERTICAL);
    final SettingsPageTransition.Session[] session=new SettingsPageTransition.Session[1];
    Runnable restore=()->{if(session[0]!=null)session[0].close(()->BACK.remove(host));};
    BACK.put(host,new java.lang.ref.WeakReference<>(restore));
    LinearLayout heading=new LinearLayout(getContext());heading.setGravity(Gravity.CENTER_VERTICAL);
    Button back=new Button(getContext());back.setText("‹");UiStyle.button(back,false);back.setTextSize(24);back.setBackgroundColor(Color.TRANSPARENT);back.setContentDescription("Назад");
    heading.addView(back,new LayoutParams(UiStyle.dp(getContext(),44),UiStyle.dp(getContext(),44)));back.setOnClickListener(v->restore.run());
    TextView title=new TextView(getContext());title.setText("Подсветка");title.setTextColor(Color.WHITE);title.setTextSize(20);title.setPadding(UiStyle.dp(getContext(),10),0,0,0);
    heading.addView(title,new LayoutParams(0,-2,1));page.addView(heading);
    final View preview=new View(getContext()){
      final HighlightRenderer renderer=new HighlightRenderer();
      protected void onDraw(Canvas c){
        float margin=UiStyle.dp(getContext(),12);
        RectF r=new RectF(margin,margin,getWidth()-margin,getHeight()-margin);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(UiStyle.dp(getContext(),14));text.setTextAlign(Paint.Align.CENTER);
        String label="Пример";float baseline=getHeight()/2f-(text.ascent()+text.descent())/2;
        float half=text.measureText(label)/2;
        RectF letters=new RectF(getWidth()/2f-half,baseline+text.ascent(),getWidth()/2f+half,baseline+text.descent());
        renderer.draw(c,r,prefs,android.os.SystemClock.uptimeMillis(),13,letters);
        c.drawText(label,getWidth()/2f,baseline,text);
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
    ArrayList<Runnable> toggles=new ArrayList<>();
    component(page,"Рамка","showFrame",preview,toggles);
    component(page,"Заливка","showFill",preview,toggles);
    component(page,"Звёзды","showStars",preview,toggles);
    Button reset=new Button(getContext());reset.setText("Сбросить настройки подсветки");UiStyle.button(reset,false);page.addView(reset,new LayoutParams(-1,-2));
    reset.setOnClickListener(v->{
      HighlightStyle.reset(prefs);Color.colorToHSV(HighlightStyle.GOLD,hsv);wheel.setColor(HighlightStyle.GOLD);
      brightness.setProgress(100);for(Runnable update:toggles)update.run();
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
