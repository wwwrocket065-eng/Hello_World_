package com.example.arkrecruit;

import android.app.*;
import android.content.*;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
  private final int REQUEST_CAPTURE=1901;
  private final int REQUEST_NOTIFICATIONS=1902;
  private TextView dbStatus;
  private ProgressBar dbProgress;
  private SharedPreferences prefs;
  private List<RecruitmentEngine.Operator> operators=new ArrayList<>();
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs=getSharedPreferences("settings",MODE_PRIVATE);
    ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,36,32,40);
    root.setBackgroundColor(UiStyle.BACKGROUND);sc.addView(root);setContentView(sc);
    setupSystemBars(root);
    // Main screen has only one compact settings entry point.
    LinearLayout header=new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(header);
    TextView title=text(header,"Arknights Recruitment Assistant",24,Color.WHITE);
    title.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
    Button gear=new Button(this);
    gear.setText("⚙");
    UiStyle.button(gear,false);
    gear.setTextSize(23);
    gear.setTextColor(UiStyle.MUTED);
    gear.setContentDescription("Настройки");
    header.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(52)));
    gear.setOnClickListener(v->openMainSettings());
    text(root,"Global",15,0xFFBFD8E3);
    button(root,"Запустить",()->{
      if(!Settings.canDrawOverlays(this)){
        
        openOverlayPermission();return;
      }
      Intent i=new Intent(this,ScreenService.class);i.setAction("SHOW");
      if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
      Toast.makeText(this,"Плавающие кнопки включены. В игре нажмите ▶.",Toast.LENGTH_LONG).show();
    });
    text(root,"Рекомендации",19,Color.WHITE);
    RecruitSettings.addRegion(root,prefs,()->loadDb(false));
    Switch panel=new Switch(this);panel.setText("Показывать панель операторов");
    UiStyle.toggle(panel);panel.setChecked(prefs.getBoolean("panel",true));
    panel.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("panel",on).apply());root.addView(panel);
    RecruitSettings.addFilter(root,prefs,null);
    text(root,"Подсветка",19,Color.WHITE);
    root.addView(new HighlightColorControl(this,prefs,null));
    slider(root,"Непрозрачность подсветки",10,70,prefs.getInt("fillAlpha",35),
      v->prefs.edit().putInt("fillAlpha",v).apply());
    button(root,"Сбросить подсветку",()->{
      prefs.edit().remove("paletteBaseColor").remove("paletteBrightness")
        .putInt("red",0).putInt("green",255).putInt("blue",90).putInt("fillAlpha",35).apply();
      recreate();
    });
    text(root,"База операторов Global",19,Color.WHITE);
    dbStatus=text(root,"Проверка базы...",15,0xFFBFD8E3);
    dbProgress=new ProgressBar(this);dbProgress.setIndeterminate(true);
    dbProgress.setVisibility(View.GONE);UiStyle.progress(dbProgress);root.addView(dbProgress);
    button(root,"Обновить базу",()->loadDb(true));
    text(root,"Проверка комбинаций вручную (через запятую)",17,Color.WHITE);
    EditText input=new EditText(this);input.setSingleLine(false);input.setTextColor(Color.WHITE);
    input.setHint("Top Operator, Summon, Ranged, AoE, Elemental");UiStyle.input(input);
    LinearLayout.LayoutParams inputLayout=new LinearLayout.LayoutParams(-1,-2);
    inputLayout.setMargins(0,dp(8),0,dp(8));root.addView(input,inputLayout);
    TextView answer=text(root,"",15,Color.WHITE);
    button(root,"Рассчитать",()->{
      RecruitmentEngine.Result r=RecruitmentEngine.best(Arrays.asList(input.getText().toString().split(",")),operators,true,9);
      answer.setText(r==null?"Нет подходящей комбинации или не загружена база":String.join(" + ",r.tags)+"\n"+r.description());
    });
    loadDb(false);
  }
  private void loadDb(boolean force) {
    if(dbProgress!=null)dbProgress.setVisibility(View.VISIBLE);
    boolean requestedChina=prefs.getBoolean("china",false);
    OperatorDatabase.load(this,force,new OperatorDatabase.ProgressListener(){
      @Override public void onProgress(String status){if(dbStatus!=null)dbStatus.setText(status);}
      @Override public void onLoaded(List<RecruitmentEngine.Operator> items,String status){
        if(requestedChina!=prefs.getBoolean("china",false))return;
        operators=items;if(dbStatus!=null)dbStatus.setText(status);
        if(dbProgress!=null)dbProgress.setVisibility(View.GONE);
      }
    });
  }
  @Override public void onResume(){super.onResume();hideNavigation();}
  private void openMainSettings(){
    LinearLayout items=new LinearLayout(this);
    items.setOrientation(LinearLayout.VERTICAL);
    int inset=dp(16);
    items.setPadding(inset,inset,inset,inset);
    LinearLayout heading=new LinearLayout(this);
    heading.setOrientation(LinearLayout.HORIZONTAL);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    TextView name=new TextView(this);
    name.setText("Настройки");
    name.setTextSize(20);
    name.setTextColor(UiStyle.TEXT);
    name.setTypeface(null,android.graphics.Typeface.BOLD);
    heading.addView(name,new LinearLayout.LayoutParams(0,dp(48),1f));
    Button close=new Button(this);
    close.setText("✕");
    UiStyle.button(close,false);
    close.setTextSize(19);
    close.setTextColor(UiStyle.MUTED);
    heading.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));
    items.addView(heading);
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setView(items)
      .create();
    close.setOnClickListener(v->dialog.dismiss());
    button(items,"Разрешение",()->{
      dialog.dismiss();
      openOverlayPermission();
    });
    button(items,"Уведомления",()->{
      dialog.dismiss();
      if(Build.VERSION.SDK_INT>=33)
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},REQUEST_NOTIFICATIONS);
      else
        Toast.makeText(this,"Уведомления доступны без отдельного разрешения",Toast.LENGTH_SHORT).show();
    });
    dialog.show();
    UiStyle.dialog(dialog);
  }
  private void openOverlayPermission(){
    OverlayPermissionNavigator.open(this);
  }
  private int dp(int value){
    return (int)(value*getResources().getDisplayMetrics().density+0.5f);
  }
  private void setupSystemBars(View root){
    if(Build.VERSION.SDK_INT>=30){
      getWindow().setDecorFitsSystemWindows(false);
      getWindow().setStatusBarColor(Color.TRANSPARENT);
      root.setOnApplyWindowInsetsListener((v,insets)->{
        android.graphics.Insets top=insets.getInsets(WindowInsets.Type.statusBars()|WindowInsets.Type.displayCutout());
        android.graphics.Insets bottom=insets.getInsets(WindowInsets.Type.systemGestures());
        int d=(int)(getResources().getDisplayMetrics().density*16);
        root.setPadding(d,top.top+d,d,bottom.bottom+d);
        return insets;
      });
    }else root.setFitsSystemWindows(true);
    hideNavigation();
  }
  private void hideNavigation(){
    if(Build.VERSION.SDK_INT>=30){
      WindowInsetsController c=getWindow().getInsetsController();
      if(c!=null){
        c.hide(WindowInsets.Type.navigationBars());
        c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    }else getWindow().getDecorView().setSystemUiVisibility(
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }
  private TextView text(LinearLayout parent,String value,int size,int color){
    TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,14,0,12);parent.addView(t);return t;
  }
  private void button(LinearLayout parent,String label,Runnable action){
    Button b=new Button(this);b.setText(label);UiStyle.button(b,"Запустить".equals(label));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
    params.setMargins(0,dp(5),0,dp(5));parent.addView(b,params);
    b.setOnClickListener(v->action.run());
  }
  private interface OnNumber { void change(int v); }
  private void slider(LinearLayout parent,String label,int min,int max,int start,OnNumber on){
    TextView t=text(parent,label+": "+start,14,0xFFDEEDF2);
    SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(start-min);
    UiStyle.slider(bar);parent.addView(bar);
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int p,boolean user){if(user){int value=p+min;t.setText(label+": "+value);on.change(value);}}
      public void onStartTrackingTouch(SeekBar b){}
      public void onStopTrackingTouch(SeekBar b){}
    });
  }
}

