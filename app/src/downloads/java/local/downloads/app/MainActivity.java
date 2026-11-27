package local.downloads.app;
import android.app.*;import android.content.*;import android.content.pm.PackageManager;import android.content.res.ColorStateList;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.os.*;import android.provider.MediaStore;import android.view.*;import android.widget.*;import java.io.*;import java.util.*;import java.util.concurrent.*;

public final class MainActivity extends Activity {
 Store store; LinearLayout list; EditText link;TextView summary; Button folderButton;String selectedFolder="",filterFolder="*"; final Handler handler=new Handler(Looper.getMainLooper());
 final ExecutorService exports=Executors.newSingleThreadExecutor();final LinkedHashMap<String,Row> rows=new LinkedHashMap<>();String shape="";
 final Runnable refresh=new Runnable(){public void run(){render();handler.postDelayed(this,500);}};
 int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 static String size(long n){double v=Math.max(0,n);String[] u={"Б","КБ","МБ","ГБ","ТБ"};int i=0;while(v>=1024&&i<u.length-1){v/=1024;i++;}return String.format(Locale.forLanguageTag("ru"),i==0?"%.0f %s":"%.1f %s",v,u[i]);}
 LinearLayout vertical(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
 LinearLayout horizontal(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
 GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 TextView text(String s,int sp,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);return t;}
 Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setMinHeight(dp(40));b.setMinimumHeight(dp(40));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setBackground(bg(0xff252525,22));b.setOnClickListener(v->action.run());return b;}
 void addButton(LinearLayout row,String label,Runnable action){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(40));p.rightMargin=dp(6);row.addView(button(label,action),p);}
 @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);store=Store.get(this);
  LinearLayout root=vertical();root.setPadding(dp(16),dp(12),dp(16),dp(10));root.setBackgroundColor(Color.BLACK);
  TextView title=text("Загрузки",27,Color.WHITE);title.setTypeface(null,Typeface.BOLD);root.addView(title);
  summary=text("Файлы хранятся в приложении",13,0xffaaaaaa);summary.setPadding(0,dp(5),0,dp(15));root.addView(summary);
  link=new EditText(this);link.setTextColor(Color.WHITE);link.setHintTextColor(0xff888888);link.setHint("Вставь ссылку на файл");link.setTextSize(15);link.setSingleLine(true);link.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);link.setPadding(dp(12),dp(9),dp(12),dp(9));link.setBackground(bg(0xff202020,14));root.addView(link,new LinearLayout.LayoutParams(-1,dp(48)));
  LinearLayout controls=horizontal();controls.setPadding(0,dp(10),0,dp(12));addButton(controls,"Вставить",()->{android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(c.hasPrimaryClip()&&c.getPrimaryClip().getItemCount()>0){CharSequence s=c.getPrimaryClip().getItemAt(0).coerceToText(this);if(s!=null)link.setText(s);}});addButton(controls,"Скачать",()->{String url=link.getText().toString().trim();if(url.isEmpty()){toast("Вставь ссылку");return;}chooseFolder("Куда скачать?",folder->{try{Store.Task t=store.add(url,folder);selectedFolder=folder;filterFolder=folder;updateFolder();link.setText("");start(t);}catch(Exception e){toast(e.getMessage());}});});root.addView(controls);
  LinearLayout folderRow=horizontal();folderRow.setPadding(0,0,0,dp(12));folderButton=button("Все файлы",()->chooseFilter());folderRow.addView(folderButton,new LinearLayout.LayoutParams(0,dp(40),1));addButton(folderRow,"+ Папка",()->newFolder(id->{selectedFolder=id;filterFolder=id;updateFolder();render();}));root.addView(folderRow);
  ScrollView sc=new ScrollView(this);list=vertical();sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);
  accept(getIntent());render();
 }
 @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);accept(i);}
 void accept(Intent i){
  if(i==null)return;
  if(Intent.ACTION_VIEW.equals(i.getAction())&&i.getData()!=null&&"downloads".equals(i.getData().getScheme())){
   String url=i.getData().getQueryParameter("url"),folder=i.getData().getQueryParameter("folder");
   if(url!=null)link.setText(url);if(folder!=null){for(Map.Entry<String,String> e:store.folderList().entrySet())if(e.getValue().equalsIgnoreCase(folder)){selectedFolder=e.getKey();filterFolder=selectedFolder;updateFolder();break;}}
   return;
  }
  if(!Intent.ACTION_SEND.equals(i.getAction()))return;Uri u=i.getParcelableExtra(Intent.EXTRA_STREAM);
  if(u!=null&&"content".equals(u.getScheme())){chooseFolder("Куда сохранить файл?",folder->importShared(u,folder));return;}String s=i.getStringExtra(Intent.EXTRA_TEXT);if(s!=null)link.setText(s.trim());
 }
 interface Pick{void picked(String id);}
 void updateFolder(){folderButton.setText("*".equals(filterFolder)?"Все файлы":store.folderName(filterFolder));shape="";}
 void newFolder(Pick callback){EditText name=new EditText(this);name.setSingleLine();name.setHint("Название проекта");new AlertDialog.Builder(this).setTitle("Новая папка").setView(name).setNegativeButton("Отмена",null).setPositiveButton("Создать",(d,w)->{try{callback.picked(store.createFolder(name.getText().toString()));}catch(Exception e){toast(e.getMessage());}}).show();}
 void chooseFolder(String title,Pick callback){ArrayList<String> ids=new ArrayList<>(),labels=new ArrayList<>();ids.add("");labels.add("Без папки");for(Map.Entry<String,String> e:store.folderList().entrySet()){ids.add(e.getKey());labels.add(e.getValue());}labels.add("+ Новая папка");new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(labels.toArray(new String[0]),ids.indexOf(selectedFolder),(dialog,which)->{dialog.dismiss();if(which==ids.size())newFolder(callback);else callback.picked(ids.get(which));}).setNegativeButton("Отмена",null).show();}
 void chooseFilter(){ArrayList<String> ids=new ArrayList<>(),labels=new ArrayList<>();ids.add("*");labels.add("Все файлы");ids.add("");labels.add("Без папки");for(Map.Entry<String,String> e:store.folderList().entrySet()){ids.add(e.getKey());labels.add(e.getValue());}new AlertDialog.Builder(this).setTitle("Папки").setSingleChoiceItems(labels.toArray(new String[0]),ids.indexOf(filterFolder),(d,w)->{d.dismiss();filterFolder=ids.get(w);if(!"*".equals(filterFolder))selectedFolder=filterFolder;updateFolder();render();}).setNegativeButton("Отмена",null).show();}
 void importShared(Uri uri,String folder){try{String name="file";long length=-1;try(android.database.Cursor c=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME,android.provider.OpenableColumns.SIZE},null,null,null)){if(c!=null&&c.moveToFirst()){if(!c.isNull(0))name=c.getString(0);if(!c.isNull(1))length=c.getLong(1);}}catch(Exception ignored){}final long expected=length;final Store.Task task=store.addShared(name,getContentResolver().getType(uri),folder);task.total=expected;selectedFolder=folder;filterFolder=folder;updateFolder();render();exports.execute(()->{try(InputStream in=getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(store.part(task))){if(in==null)throw new IOException("Нет доступа к файлу");byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1){if(!store.running(task,1))throw new IOException("Остановлено");out.write(b,0,n);task.got+=n;}out.getFD().sync();if(expected>=0&&task.got!=expected)throw new IOException("Размер не совпадает");store.finish(task,1);}catch(Exception e){store.fail(task,1,e.getMessage());}finally{store.save();}});}catch(Exception e){toast(e.getMessage());}}
 @Override protected void onResume(){super.onResume();handler.removeCallbacks(refresh);handler.post(refresh);}
 @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
 @Override protected void onDestroy(){handler.removeCallbacks(refresh);exports.shutdown();super.onDestroy();}
 void toast(String s){Toast.makeText(this,s==null?"Ошибка":s,Toast.LENGTH_SHORT).show();}
 void start(Store.Task t){try{startForegroundService(new Intent(this,TransferService.class).putExtra("id",t.id));}catch(Exception e){store.pause(t);toast("Не удалось запустить загрузку: "+e.getMessage());}render();}
 String status(Store.Task t){switch(t.status){case Store.RUNNING:return "Загрузка";case Store.QUEUED:return "В очереди";case Store.PAUSED:return "Пауза";case Store.FAILED:return "Ошибка: "+t.error;case Store.DONE:return "Готово";default:return t.status;}}
 final class Row{TextView title,detail;ProgressBar progress;}
 void render(){
  if(list==null)return;List<Store.Task> all=store.list();if(!"*".equals(filterFolder))all.removeIf(t->!filterFolder.equals(t.folder));StringBuilder k=new StringBuilder();long total=0;int count=0;
  for(Store.Task t:all){k.append(t.id).append(':').append(t.status).append(':').append(t.folder).append(';');if(Store.DONE.equals(t.status)){total+=t.got;count++;}}
  summary.setText(count+" файлов · "+size(total)+" · "+("*".equals(filterFolder)?"все папки":store.folderName(filterFolder)));
  if(!shape.equals(k.toString())||rows.size()!=all.size()){shape=k.toString();list.removeAllViews();rows.clear();
   if(all.isEmpty()){TextView empty=text("Загрузок пока нет",16,0xff888888);empty.setGravity(Gravity.CENTER);empty.setPadding(0,dp(40),0,0);list.addView(empty);}
   for(Store.Task t:all){Row r=new Row();rows.put(t.id,r);LinearLayout card=vertical();card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(bg(0xff161616,16));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);list.addView(card,cp);
    r.title=text(t.name,16,Color.WHITE);r.title.setTypeface(null,Typeface.BOLD);card.addView(r.title);r.detail=text("",13,0xffbbbbbb);r.detail.setPadding(0,dp(5),0,dp(5));card.addView(r.detail);
    if(!Store.DONE.equals(t.status)){r.progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);r.progress.setProgressTintList(ColorStateList.valueOf(Color.WHITE));r.progress.setMax(1000);card.addView(r.progress,new LinearLayout.LayoutParams(-1,dp(8)));}
    LinearLayout buttons=horizontal();buttons.setPadding(0,dp(9),0,0);card.addView(buttons);
    if((Store.RUNNING.equals(t.status)||Store.QUEUED.equals(t.status))&&!t.url.isEmpty())addButton(buttons,"Пауза",()->{store.pause(t);TransferService.interrupt(t.id);render();});
    else if(Store.DONE.equals(t.status)){addButton(buttons,Store.APK_MIME.equals(Store.effectiveMime(t))?"APK":"Открыть",()->open(t,false));addButton(buttons,"Поделиться",()->open(t,true));addButton(buttons,"Экспорт",()->export(t));}
    else if(!t.url.isEmpty())addButton(buttons,"Продолжить",()->{store.resume(t);start(t);});
    if(!Store.RUNNING.equals(t.status)&&!Store.QUEUED.equals(t.status)){
     LinearLayout bottom=horizontal();bottom.setPadding(0,dp(6),0,0);if(Store.DONE.equals(t.status))addButton(bottom,"Переместить",()->chooseFolder("Переместить в папку",folder->{try{store.move(t,folder);render();}catch(Exception e){toast(e.getMessage());}}));addButton(bottom,"Удалить",()->new AlertDialog.Builder(this).setMessage("Удалить "+t.name+"?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{if(!store.remove(t))toast("Не удалось удалить");render();}).show());card.addView(bottom);}
   }
  }
  for(Store.Task t:all){Row r=rows.get(t.id);if(r==null)continue;r.title.setText(t.name);r.detail.setText(store.folderName(t.folder)+" · "+status(t)+" · "+size(t.got)+(t.total>=0&&!Store.DONE.equals(t.status)?" / "+size(t.total):"")+(Store.RUNNING.equals(t.status)?" · "+size(t.speed)+"/с":""));if(r.progress!=null){r.progress.setIndeterminate(Store.RUNNING.equals(t.status)&&t.total<=0);if(t.total>0)r.progress.setProgress((int)Math.min(1000,1000.0*t.got/t.total));}}
 }
 void open(Store.Task t,boolean share){
  if(t==null||!Store.DONE.equals(t.status)||!store.data(t).isFile()){toast("Файл не найден или ещё не скачан");return;}
  Uri u=store.uri(t);String mime=Store.effectiveMime(t);
  boolean apk=Store.APK_MIME.equals(mime);
  if(apk&&!share){
   new AlertDialog.Builder(this).setTitle("Файл APK").setMessage("Загрузки сохраняют APK, но не устанавливают приложения. Экспортируй файл в общую папку Download/Downloads. Проверку безопасности выполняет Android.")
    .setNegativeButton("Закрыть",null).setPositiveButton("Экспорт APK",(d,w)->export(t)).show();return;
  }
  Intent i=share?new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,u):new Intent(Intent.ACTION_VIEW).setDataAndType(u,mime);
  i.setClipData(ClipData.newRawUri(t.name,u));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
  try{startActivity(share?Intent.createChooser(i,"Поделиться файлом"):i);}
  catch(Exception e){new AlertDialog.Builder(this).setTitle("Не удалось открыть файл").setMessage("Тип: "+mime+"\n"+(e instanceof ActivityNotFoundException?"Нет подходящего приложения для открытия.":String.valueOf(e.getMessage()))).setNegativeButton("Закрыть",null).setPositiveButton("Экспорт",(dialog,which)->export(t)).show();}
 }
 void export(Store.Task t){toast("Копирую в общие Загрузки…");exports.execute(()->{Uri u=null;try{
  ContentValues v=new ContentValues();v.put(MediaStore.Downloads.DISPLAY_NAME,t.name);v.put(MediaStore.Downloads.MIME_TYPE,Store.effectiveMime(t));v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/Downloads"+(t.folder.isEmpty()?"":"/"+store.folderName(t.folder)));v.put(MediaStore.Downloads.IS_PENDING,1);
  u=getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(u==null)throw new IOException("Не удалось создать файл");
  try(InputStream in=new FileInputStream(store.data(t));OutputStream out=getContentResolver().openOutputStream(u,"w")){if(out==null)throw new IOException("Нет доступа");byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
  v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);getContentResolver().update(u,v,null,null);runOnUiThread(()->toast("Файл экспортирован"));
 }catch(Exception e){if(u!=null)getContentResolver().delete(u,null,null);runOnUiThread(()->toast("Ошибка экспорта: "+e.getMessage()));}});}
}