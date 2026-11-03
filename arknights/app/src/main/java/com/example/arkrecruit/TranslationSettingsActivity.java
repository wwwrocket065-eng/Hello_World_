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
  private final Runnable refresh=new Runnable(){public void run(){ModelStore.check(TranslationSettingsActivity.this);if(status!=null)status.setText(ModelStore.status);handler.postDelayed(this,1000);}};
  public void onCreate(Bundle b){super.onCreate(b);UiStyle.init(this);
    ScrollView sc=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(16),dp(24),dp(16),dp(24));page.setBackgroundColor(UiStyle.BACKGROUND);page.setFitsSystemWindows(true);sc.addView(page);setContentView(sc);
    LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);NavigationButton back=new NavigationButton(this,"",true);heading.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));back.setOnClickListener(v->finish());TextView title=new TextView(this);title.setText("Перевод");title.setTextSize(23);title.setTextColor(UiStyle.TEXT);heading.addView(title);page.addView(heading);
    status=new TextView(this);status.setTextColor(UiStyle.TEXT);status.setPadding(0,dp(16),0,dp(16));page.addView(status);
    add(page,"Скачать Qwen3 · 2,50 ГБ",()->{ModelStore.download(this);status.setText(ModelStore.status);});
    add(page,"Импорт из «Загрузок»",()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("*/*");pick.addCategory(Intent.CATEGORY_OPENABLE);pick.putExtra("android.provider.extra.INITIAL_URI",android.net.Uri.parse("content://com.android.providers.downloads.documents/root/downloads"));startActivityForResult(pick,31);});
    TextView note=new TextView(this);note.setText("Английский → русский\nМодель хранится отдельно от APK. Копия загрузки остаётся в «Загрузках».\n\nПеревод включается кнопкой A/Я между ▶ и настройками. Нажатия проходят в игру.");note.setTextColor(UiStyle.MUTED);note.setPadding(0,dp(16),0,dp(16));page.addView(note);UiStyle.watch(page);
  }
  protected void onResume(){super.onResume();AppVisibility.visible=true;handler.post(refresh);}
  protected void onPause(){handler.removeCallbacks(refresh);AppVisibility.visible=false;super.onPause();}
  protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==31&&result==RESULT_OK&&data!=null&&data.getData()!=null){ModelStore.importUri(this,data.getData());status.setText("Проверка и импорт…");}}
  private int dp(int x){return UiStyle.dp(this,x);}
  private void add(LinearLayout p,String s,Runnable r){Button b=new Button(this);b.setText(s);UiStyle.button(b,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));p.addView(b,lp);b.setOnClickListener(v->r.run());}
}
