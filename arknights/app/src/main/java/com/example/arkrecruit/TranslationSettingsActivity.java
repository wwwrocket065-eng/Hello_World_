package com.example.arkrecruit;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.example.arkrecruit.translate.ModelStore;
/** Model management is outside the frozen recruitment settings. */
public final class TranslationSettingsActivity extends Activity {
  private final Handler handler=new Handler(Looper.getMainLooper());private TextView status;
  private final Runnable refresh=new Runnable(){public void run(){ModelStore.check(TranslationSettingsActivity.this);if(status!=null)status.setText(ModelStore.status+"\n\n"+com.example.arkrecruit.translate.TranslationController.liveDetails);handler.postDelayed(this,1000);}};
  public void onCreate(Bundle b){super.onCreate(b);UiStyle.init(this);
    ScrollView sc=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(16),dp(24),dp(16),dp(24));page.setBackgroundColor(UiStyle.BACKGROUND);page.setFitsSystemWindows(true);sc.addView(page);setContentView(sc);
    LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);NavigationButton back=new NavigationButton(this,"",true);heading.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));back.setOnClickListener(v->finish());TextView title=new TextView(this);title.setText("Перевод");title.setTextSize(23);title.setTextColor(UiStyle.TEXT);heading.addView(title);page.addView(heading);
    TextView source=new TextView(this);source.setText("Исходный язык: автоопределение");source.setTextColor(UiStyle.TEXT);source.setPadding(0,dp(16),0,dp(8));page.addView(source);
    android.content.SharedPreferences language=getSharedPreferences("translation_preferences",MODE_PRIVATE);
    Button target=new Button(this);UiStyle.button(target,false);page.addView(target,new LinearLayout.LayoutParams(-1,-2));
    Runnable label=()->target.setText("Язык перевода: "+("uk".equals(language.getString("target","ru"))?"Українська":"Русский"));label.run();
    target.setOnClickListener(v->{AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Язык перевода").setSingleChoiceItems(new String[]{"Русский","Українська"},"uk".equals(language.getString("target","ru"))?1:0,(d,which)->{language.edit().putString("target",which==1?"uk":"ru").apply();label.run();d.dismiss();}).create();dialog.show();UiStyle.dialog(dialog);});
    status=new TextView(this);status.setTextColor(UiStyle.TEXT);status.setPadding(0,dp(16),0,dp(16));page.addView(status);
    add(page,"Скопировать диагностику",()->{
      String details=ModelStore.status+"\n"+com.example.arkrecruit.translate.TranslationController.liveDetails+"\n"+getSharedPreferences("translation_model",0).getString("last_error","");
      ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Диагностика перевода",details));
      Toast.makeText(this,"Диагностика скопирована",Toast.LENGTH_SHORT).show();
    });
    add(page,"Скачать Qwen3 · 2,50 ГБ",()->{ModelStore.download(this);status.setText(ModelStore.status);});
    add(page,"Импорт из «Загрузок»",()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);pick.putExtra("android.provider.extra.INITIAL_URI",android.net.Uri.parse("content://com.android.providers.downloads.documents/root/downloads"));startActivityForResult(pick,31);});
    TextView note=new TextView(this);note.setText("Автоопределение исходного языка → выбранный язык\nМодель хранится отдельно от APK. Копия загрузки остаётся в «Загрузках».\n\nПеревод включается кнопкой со значком перевода между ▶ и настройками. Нажатия проходят в игру.");note.setTextColor(UiStyle.MUTED);note.setPadding(0,dp(16),0,dp(16));page.addView(note);UiStyle.watch(page);
  }
  protected void onResume(){super.onResume();AppVisibility.visible=true;handler.post(refresh);}
  protected void onPause(){handler.removeCallbacks(refresh);AppVisibility.visible=false;super.onPause();}
  protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==31&&result==RESULT_OK&&data!=null&&data.getData()!=null){ModelStore.importUri(this,data.getData());status.setText("Проверка и импорт…");}}
  private int dp(int x){return UiStyle.dp(this,x);}
  private void add(LinearLayout p,String s,Runnable r){Button b=new Button(this);b.setText(s);UiStyle.button(b,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));p.addView(b,lp);b.setOnClickListener(v->r.run());}
}

