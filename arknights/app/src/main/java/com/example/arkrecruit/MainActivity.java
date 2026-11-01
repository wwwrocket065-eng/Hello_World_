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
  private TextView permissions,dbStatus;
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
    text(root,"Arknights Recruitment Assistant",24,Color.WHITE);
    text(root,"Global • анализ и подсветка поверх игры",15,0xFFBFD8E3);
    permissions=text(root,"",15,Color.WHITE);
    button(root,"Разрешение",this::openOverlayPermission);
    button(root,"Уведомления (Android 13+)",()->{
      if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},REQUEST_NOTIFICATIONS);
    });
    button(root,"Запустить",()->{
      if(!Settings.canDrawOverlays(this)){
        Toast.makeText(this,"Сначала предоставьте разрешение",Toast.LENGTH_LONG).show();
        openOverlayPermission();return;
      }
      Intent i=new Intent(this,ScreenService.class);i.setAction("SHOW");startService(i);
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
    text(root,"Захват запускается только после системного согласия. Изображения обрабатываются на устройстве.",13,0xFFBFD8E3);
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
  @Override public void onResume(){super.onResume();hideNavigation();if(permissions!=null)permissions.setText(
    "Оверлей: "+(Settings.canDrawOverlays(this)?"разрешён":"не разрешён")+
    "\nЗахват экрана: запрос при нажатии ▶");}
  private void openOverlayPermission(){
    Intent xiaomi=new Intent("miui.intent.action.APP_PERM_EDITOR");
    xiaomi.setClassName("com.miui.securitycenter","com.miui.permcenter.permissions.PermissionsEditorActivity");
    xiaomi.putExtra("extra_pkgname",getPackageName());
    try {startActivity(xiaomi);return;}catch(Exception ignored){}
    Intent generic=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:"+getPackageName()));
    try {startActivity(generic);return;}catch(Exception ignored){}
    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:"+getPackageName())));
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
