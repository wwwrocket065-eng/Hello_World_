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
  private SharedPreferences prefs;
  private List<RecruitmentEngine.Operator> operators=new ArrayList<>();
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs=getSharedPreferences("settings",MODE_PRIVATE);
    ScrollView sc=new ScrollView(this); LinearLayout root=new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32,36,32,40);
    root.setBackgroundColor(Color.rgb(18,24,31));sc.addView(root);setContentView(sc);
    text(root,"Arknights Recruitment Assistant",24,Color.WHITE);
    text(root,"Global • анализ и подсветка поверх игры",15,0xFFBFD8E3);
    permissions=text(root,"",15,Color.WHITE);
    button(root,"Разрешение",()->{
      Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
      startActivity(i);
    });
    button(root,"Уведомления (Android 13+)",()->{
      if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},REQUEST_NOTIFICATIONS);
    });
    button(root,"▶ Начать автоматический анализ",()->{
      if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Сначала разрешите оверлей",Toast.LENGTH_LONG).show();return;}
      MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
      startActivityForResult(m.createScreenCaptureIntent(),REQUEST_CAPTURE);
    });
    button(root,"■ Остановить анализ",()->{
      Intent i=new Intent(this,ScreenService.class);i.setAction("STOP");startService(i);
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
    OperatorDatabase.load(this,force,(items,status)->{operators=items;if(dbStatus!=null)dbStatus.setText(status);});
  }
  @Override public void onResume(){super.onResume();if(permissions!=null)permissions.setText(
    "Оверлей: "+(Settings.canDrawOverlays(this)?"разрешён":"не разрешён")+
    "\nЗахват экрана: запрос при старте");}
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==REQUEST_CAPTURE&&result==RESULT_OK&&data!=null){
      Intent service=new Intent(this,ScreenService.class);
      service.setAction("START");service.putExtra("captureCode",result);service.putExtra("captureData",data);
      if(Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
      Toast.makeText(this,"Анализ включается. Откройте рекрутинг.",Toast.LENGTH_LONG).show();
    }
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
