package com.example.arkrecruit.translate;
import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
/** DownloadManager owns resumable public Downloads copy; verified import is atomic. */
public final class ModelStore {
  public static final String NAME="Qwen3-4B-Instruct-2507-Q4_K_M.gguf";
  public static final long SIZE=2497281120L;
  private static final String SHA="3605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597";
  private static final ExecutorService IO=Executors.newSingleThreadExecutor();
  private static volatile boolean importing;
  public static volatile String status="";
  public static File file(Context c){return new File(c.getFilesDir(),"models/"+NAME);}
  public static boolean ready(Context c){return file(c).length()==SIZE&&c.getSharedPreferences("translation_model",0).getBoolean("verified",false);}
  public static void download(Context c){
    if(ready(c)){status="Модель готова";return;}
    DownloadManager dm=(DownloadManager)c.getSystemService(Context.DOWNLOAD_SERVICE);
    SharedPreferences p=c.getSharedPreferences("translation_model",0);long id=p.getLong("download",-1);
    if(id!=-1){check(c);return;}
    String url="https://huggingface.co/unsloth/Qwen3-4B-Instruct-2507-GGUF/resolve/a06e946bb6b655725eafa393f4a9745d460374c9/"+NAME;
    DownloadManager.Request r=new DownloadManager.Request(Uri.parse(url));
    r.setTitle("Qwen3 · Arknights Assistant").setDescription("Локальный перевод · 2,50 ГБ")
      .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
      .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,NAME);
    try{p.edit().putLong("download",dm.enqueue(r)).apply();status="Загрузка модели…";}catch(Exception e){status="Загрузка: "+e.getMessage();}
  }
  public static void check(Context c){
    if(importing)return;
    if(ready(c)){status="Qwen3-4B · готова";return;}
    Context app=c.getApplicationContext();DownloadManager dm=(DownloadManager)app.getSystemService(Context.DOWNLOAD_SERVICE);
    SharedPreferences p=app.getSharedPreferences("translation_model",0);long id=p.getLong("download",-1);if(id==-1){if(status.isEmpty())status="Модель не загружена · 2,50 ГБ";return;}
    try(Cursor row=dm.query(new DownloadManager.Query().setFilterById(id))){
      if(!row.moveToFirst()){p.edit().remove("download").apply();status="Загрузка не найдена. Можно импортировать файл из «Загрузок».";return;}
      int state=row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
      if(state==DownloadManager.STATUS_SUCCESSFUL){
        if(!importing){importing=true;IO.execute(()->{try(ParcelFileDescriptor fd=dm.openDownloadedFile(id);InputStream in=new FileInputStream(fd.getFileDescriptor())){install(app,in);}catch(Exception e){status="Импорт: "+e.getMessage();}finally{importing=false;}});}return;
      }
      if(state==DownloadManager.STATUS_FAILED){status="Загрузка не завершена ("+row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))+"). Повторите или импортируйте файл.";p.edit().remove("download").apply();return;}
      long done=row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
      status=(state==DownloadManager.STATUS_PAUSED?"Загрузка приостановлена · ":"Загрузка · ")+(100*done/SIZE)+"%";
    }catch(Exception e){status="Модель: "+e.getMessage();}
  }
  public static void importUri(Context c,Uri uri){
    if(importing)return;importing=true;Context app=c.getApplicationContext();
    IO.execute(()->{try(InputStream in=app.getContentResolver().openInputStream(uri)){install(app,in);}catch(Exception e){status="Импорт: "+e.getMessage();}finally{importing=false;}});
  }
  private static void install(Context c,InputStream in)throws Exception{
    if(in==null)throw new IOException("Файл недоступен");
    File dest=file(c);dest.getParentFile().mkdirs();File part=new File(dest.getPath()+".part");
    try{
      if(dest.getParentFile().getUsableSpace()<SIZE+64*1024*1024L)throw new IOException("Нужно 2,6 ГБ свободной памяти для модели");
      status="Проверка и импорт модели…";MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;byte[] bytes=new byte[1024*1024];
      try(FileOutputStream out=new FileOutputStream(part)){int n;while((n=in.read(bytes))!=-1){count+=n;if(count>SIZE)throw new IOException("Неверный размер модели");digest.update(bytes,0,n);out.write(bytes,0,n);}out.getFD().sync();}
      StringBuilder hex=new StringBuilder();for(byte b:digest.digest())hex.append(String.format("%02x",b&255));
      if(count!=SIZE||!SHA.equals(hex.toString()))throw new IOException("Не совпадает контрольная сумма. Нужна Qwen3-4B-Instruct-2507 Q4_K_M (Unsloth).");
      if(!part.renameTo(dest))throw new IOException("Не удалось сохранить модель");
      c.getSharedPreferences("translation_model",0).edit().putBoolean("verified",true).apply();status="Qwen3-4B · готова";
    }finally{part.delete();}
  }
}
