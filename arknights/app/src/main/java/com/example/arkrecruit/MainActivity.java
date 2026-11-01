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
    root.setBackgroundColor(Color.rgb(18,24,31));sc.addView(root);setContentView(sc);
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
    gear.setTextSize(23);
    gear.setAllCaps(false);
    gear.setContentDescription("Настройки");
    header.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(52)));
    gear.setOnClickListener(v->openMainSettings());
    text(root,"Global",15,0xFFBFD8E3);
    button(root,"Запустить",()->{
      if(!Settings.canDrawOverlays(this)){
        Toast.makeText(this,"Сначала предоставьте разрешение",Toast.LENGTH_LONG).show();
        openOverlayPermission();return;
      }
      Intent i=new Intent(this,ScreenService.class);i.setAction("SHOW");
      if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
      Toast.makeText(this,"Плавающие кнопки включены. В игре нажмите ▶.",Toast.LENGTH_LONG).show();
    });
    text(root,"Рекомендации",19,Color.WHITE);
    Switch chance=new Switch(this);chance.setText("Шанс на редкого вместо гарантии");
    chance.setTextColor(Color.WHITE);chance.setChecked(prefs.getBoolean("chance",false));
    chance.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("chance",on).apply());root.addView(chance);
    Switch panel=new Switch(this);panel.setText("Показывать панель операторов");
    panel.setTextColor(Color.WHITE);panel.setChecked(prefs.getBoolean("panel",true));
    panel.setOnCheckedChangeListener((v,on)->prefs.edit().putBoolean("panel",on).apply());root.addView(panel);
    text(root,"Подсветка",19,Color.WHITE);
    slider(root,"Красный",0,255,prefs.getInt("red",0),v->prefs.edit().putInt("red",v).apply());
    slider(root,"Зелёный",0,255,prefs.getInt("green",255),v->prefs.edit().putInt("green",v).apply());
    slider(root,"Синий",0,255,prefs.getInt("blue",90),v->prefs.edit().putInt("blue",v).apply());
    slider(root,"Прозрачность",20,100,prefs.getInt("alpha",85),v->prefs.edit().putInt("alpha",v).apply());
    slider(root,"Толщина рамки",1,12,prefs.getInt("stroke",4),v->prefs.edit().putInt("stroke",v).apply());
    slider(root,"Скругление",0,30,prefs.getInt("corner",12),v->prefs.edit().putInt("corner",v).apply());
    button(root,"Сбросить подсветку",()->{
      prefs.edit().putInt("red",0).putInt("green",255).putInt("blue",90).putInt("alpha",85).putInt("stroke",4).putInt("corner",12).apply();
      recreate();
    });
    text(root,"База операторов Global",19,Color.WHITE);
    dbStatus=text(root,"Проверка базы...",15,0xFFBFD8E3);
    dbProgress=new ProgressBar(this);dbProgress.setIndeterminate(true);
    dbProgress.setVisibility(View.GONE);root.addView(dbProgress);
    button(root,"Обновить базу",()->loadDb(true));
    text(root,"Проверка комбинаций вручную (через запятую)",17,Color.WHITE);
    EditText input=new EditText(this);input.setSingleLine(false);input.setTextColor(Color.WHITE);
    input.setHintTextColor(0xFF9A9A9A);input.setHint("Top Operator, Summon, Ranged, AoE, Elemental");root.addView(input);
    TextView answer=text(root,"",15,Color.WHITE);
    button(root,"Рассчитать",()->{
      RecruitmentEngine.Result r=RecruitmentEngine.best(Arrays.asList(input.getText().toString().split(",")),operators,prefs.getBoolean("chance",false),9);
      answer.setText(r==null?"Нет подходящей комбинации или не загружена база":String.join(" + ",r.tags)+"\n"+r.description());
    });
    loadDb(false);
  }
  private void loadDb(boolean force) {
    if(dbProgress!=null)dbProgress.setVisibility(View.VISIBLE);
    OperatorDatabase.load(this,force,new OperatorDatabase.ProgressListener(){
      @Override public void onProgress(String status){if(dbStatus!=null)dbStatus.setText(status);}
      @Override public void onLoaded(List<RecruitmentEngine.Operator> items,String status){
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
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setTitle("Настройки")
      .setView(items)
      .setNegativeButton("Закрыть",null)
      .create();
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
  }
  private void openOverlayPermission(){
    // Open the relevant *overlay* settings, not Xiaomi's unrelated "Other permissions".
    // Android 11+ ignores the package URI and may show a list by design.
    try {
      Intent overlay=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
      if(Build.VERSION.SDK_INT<30)
        overlay.setData(Uri.parse("package:"+getPackageName()));
      startActivity(overlay);
      return;
    }catch(Exception ignored){}
    try{
      startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:"+getPackageName())));
    }catch(Exception noSettings){
      Toast.makeText(this,"Не удалось открыть настройки разрешения",Toast.LENGTH_LONG).show();
    }
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
    Button b=new Button(this);b.setText(label);b.setAllCaps(false);parent.addView(b);b.setOnClickListener(v->action.run());
  }
  private interface OnNumber { void change(int v); }
  private void slider(LinearLayout parent,String label,int min,int max,int start,OnNumber on){
    TextView t=text(parent,label+": "+start,14,0xFFDEEDF2);
    SeekBar bar=new SeekBar(this);bar.setMax(max-min);bar.setProgress(start-min);parent.addView(bar);
    bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
      public void onProgressChanged(SeekBar b,int p,boolean user){if(user){int value=p+min;t.setText(label+": "+value);on.change(value);}}
      public void onStartTrackingTouch(SeekBar b){}
      public void onStopTrackingTouch(SeekBar b){}
    });
  }
}
