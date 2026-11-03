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
  private TextView footer;
  private ProgressBar dbProgress;
  private SharedPreferences prefs;
  private LinearLayout mainRoot;
  private List<RecruitmentEngine.Operator> operators=new ArrayList<>();
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);UiStyle.init(this);
    prefs=getSharedPreferences("settings",MODE_PRIVATE);HighlightStyle.ensure(prefs);
    ScrollView sc=new ScrollView(this); sc.setFillViewport(true); LinearLayout root=new LinearLayout(this);mainRoot=root;
    root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,36,32,40);
    root.setBackgroundColor(UiStyle.BACKGROUND);sc.addView(root);setContentView(sc);
    setupSystemBars(root);
    // Main screen has only one compact settings entry point.
    LinearLayout header=new LinearLayout(this);
    header.setOrientation(LinearLayout.HORIZONTAL);
    header.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(header);
    TextView title=text(header,"Arknights Assistant",24,UiStyle.TEXT);
    title.setLayoutParams(new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
    Button gear=new Button(this);
    gear.setText("⚙");
    UiStyle.button(gear,false);
    gear.setTextSize(23);
    gear.setTextColor(UiStyle.MUTED);
    gear.setContentDescription("Настройки");
    header.addView(gear,new LinearLayout.LayoutParams(dp(52),dp(52)));
    gear.setOnClickListener(v->openMainSettings());
    button(root,"Запустить",()->{
      if(!Settings.canDrawOverlays(this)){
        
        openOverlayPermission();return;
      }
      Intent i=new Intent(this,ScreenService.class);i.setAction("SHOW");
      if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
      Toast.makeText(this,"Плавающие кнопки включены. В игре нажмите ▶.",Toast.LENGTH_LONG).show();
    });
    button(root,"Перевод",()->startActivity(new Intent(this,TranslationSettingsActivity.class)));
    root.addView(new HighlightColorControl(this,prefs,null));
    text(root,"Рекомендации",19,UiStyle.TEXT);
    RecruitSettings.addRegion(root,prefs,()->loadDb(false));
    RecruitSettings.addPanel(root,prefs,null);
    RecruitSettings.addFilter(root,prefs,null);
    root.addView(new View(this),new LinearLayout.LayoutParams(1,0,1f));
    footer=text(root,"",13,UiStyle.MUTED);
    updateFooter();
    loadDb(false);UiStyle.watch(root);
  }
  @Override public void onBackPressed(){if(mainRoot!=null&&HighlightColorControl.back(mainRoot))return;super.onBackPressed();}
  private void loadDb(boolean force) {
    if(dbProgress!=null)dbProgress.setVisibility(View.VISIBLE);
    boolean requestedChina=prefs.getBoolean("china",false);
    OperatorDatabase.load(this,force,new OperatorDatabase.ProgressListener(){
      @Override public void onProgress(String status){if(dbStatus!=null)dbStatus.setText(status);}
      @Override public void onLoaded(List<RecruitmentEngine.Operator> items,String status){
        if(requestedChina!=prefs.getBoolean("china",false))return;
        operators=items;updateFooter();if(dbStatus!=null)dbStatus.setText(status);
        if(dbProgress!=null)dbProgress.setVisibility(View.GONE);
      }
    });
  }
  private void updateFooter(){
    if(footer==null)return;
    String version="";
    try{version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
    long updated=getSharedPreferences(prefs.getBoolean("china",false)?"recruit_china":"recruit",MODE_PRIVATE).getLong("updated",0);
    String date=updated>0?new java.text.SimpleDateFormat("dd.MM.yyyy",Locale.getDefault()).format(new Date(updated)):"не обновлена";
    footer.setText("v"+version+" · База: "+date);
  }
  @Override public void onResume(){super.onResume();AppVisibility.visible=true;hideNavigation();}
  @Override public void onPause(){AppVisibility.visible=false;super.onPause();}
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
    name.setTypeface(UiStyle.FONT,android.graphics.Typeface.BOLD);
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
    ThemeControl.add(items,()->{});
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
    button(items,"Обновить базу",()->loadDb(true));
    dbStatus=text(items,"",13,UiStyle.MUTED);
    dbProgress=new ProgressBar(this);dbProgress.setIndeterminate(true);
    dbProgress.setVisibility(View.GONE);UiStyle.progress(dbProgress);items.addView(dbProgress);
    dialog.setOnDismissListener(d->{dbStatus=null;dbProgress=null;});
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

