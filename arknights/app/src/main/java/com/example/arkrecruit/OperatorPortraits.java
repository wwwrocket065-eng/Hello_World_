package com.example.arkrecruit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Offline thumbnails; loading never blocks frame processing or list scrolling. */
public final class OperatorPortraits implements AutoCloseable {
  private final Context app;
  private final Handler main=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newFixedThreadPool(2);
  private final LruCache<String,Bitmap> cache=new LruCache<>(64);
  private final JSONObject index;
  private volatile boolean closed;
  public OperatorPortraits(Context context){
    app=context.getApplicationContext();
    JSONObject loaded=new JSONObject();
    try(InputStream in=app.getAssets().open("operators/avatars.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
      loaded=new JSONObject(out.toString("UTF-8"));
    }catch(Exception e){android.util.Log.e("RecruitAssist","Portrait index unavailable",e);}
    index=loaded;
  }
  public void bind(ImageView view,String name){
    view.setTag(name);view.setImageDrawable(null);
    Bitmap found=cache.get(name);
    if(found!=null){view.setImageBitmap(found);return;}
    String id=index.optString(name,"");
    if(id.isEmpty()||closed)return;
    worker.execute(()->{
      if(closed)return;
      Bitmap image=cache.get(name);
      if(image==null)try(InputStream in=app.getAssets().open("operators/"+id+".webp")){
        image=BitmapFactory.decodeStream(in);
        if(image!=null)cache.put(name,image);
      }catch(IOException e){android.util.Log.w("RecruitAssist","Portrait missing: "+id,e);}
      final Bitmap result=image;
      if(result!=null&&!closed)main.post(()->{
        if(!closed&&name.equals(view.getTag()))view.setImageBitmap(result);
      });
    });
  }
  @Override public void close(){closed=true;worker.shutdownNow();cache.evictAll();}
}
