package local.downloads.app;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;import java.util.regex.*;

public final class TransferService extends Service {
 static final String CHANNEL="transfers";static final int NOTIF=10;
 static volatile HttpURLConnection connection;static volatile String activeId="";
 final ExecutorService worker=Executors.newSingleThreadExecutor();Store store;volatile boolean closing=false;
 static void interrupt(String id){if(id.equals(activeId)){HttpURLConnection c=connection;if(c!=null)c.disconnect();}}
 @Override public void onCreate(){super.onCreate();store=Store.get(this);getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"Загрузки",NotificationManager.IMPORTANCE_LOW));}
 Notification notification(Store.Task t){
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle(t==null?"Загрузки":t.name).setContentText(t==null?"Подключение…":MainActivity.size(t.got)+(t.total>0?" / "+MainActivity.size(t.total):"")+" · "+MainActivity.size(t.speed)+"/с").setContentIntent(pi).setOnlyAlertOnce(true).setOngoing(true);
  if(t!=null&&t.total>0)n.setProgress(100,(int)Math.min(100,100.0*t.got/t.total),false);else n.setProgress(0,0,true);
  return n.build();
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  startForeground(NOTIF,notification(null));String id=intent==null?null:intent.getStringExtra("id");
  worker.execute(()->{try{if(id!=null&&!closing){Store.Task t=store.get(id);int e=store.begin(t);if(e>=0)transfer(t,e);}}finally{stopSelf(startId);}});return START_NOT_STICKY;
 }
 @Override public IBinder onBind(Intent i){return null;}
 void check(Store.Task t,int epoch)throws IOException{if(closing||!store.running(t,epoch))throw new IOException("Приостановлено");}
 static String name(HttpURLConnection c,URL url,String fallback){
  String cd=c.getHeaderField("Content-Disposition"),n=null;
  if(cd!=null){Matcher ext=Pattern.compile("(?i)filename\\*\\s*=\\s*UTF-8''([^;]+)").matcher(cd);if(ext.find())try{n=URLDecoder.decode(ext.group(1).trim().replace("+","%2B"),"UTF-8");}catch(Exception ignored){}
   if(n==null){Matcher m=Pattern.compile("(?i)filename\\s*=\\s*(?:\"([^\"]+)\"|([^;]+))").matcher(cd);if(m.find())n=m.group(1)!=null?m.group(1):m.group(2).trim();}}
  if(n==null)n=Store.guess(url);if("file".equals(n)&&fallback!=null)n=fallback;
  String type=c.getContentType();if(!n.contains(".")&&type!=null){String ext=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(type.split(";")[0].trim());if(ext!=null)n+="."+ext;}
  return Store.clean(n);
 }
 void transfer(Store.Task t,int epoch){
  activeId=t.id;HttpURLConnection c=null;
  try{
   File part=store.part(t);long offset=part.length();
   // Only resume bytes when server supplied an entity validator. Otherwise restart safely.
   if(offset>0&&t.etag.isEmpty()&&t.modified.isEmpty()){try(FileOutputStream o=new FileOutputStream(part,false)){}offset=0;}
   URL u=Store.url(t.url);int redirects=0;boolean retried=false;
   while(true){
    check(t,epoch);c=(HttpURLConnection)u.openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("Accept-Encoding","identity");
    if(offset>0){c.setRequestProperty("Range","bytes="+offset+"-");c.setRequestProperty("If-Range",!t.etag.isEmpty()?t.etag:t.modified);}
    int code=c.getResponseCode();check(t,epoch);
    if(code>=300&&code<400){String loc=c.getHeaderField("Location");if(loc==null||++redirects>8)throw new IOException("Некорректное перенаправление");u=Store.url(new URL(u,loc).toString());c.disconnect();continue;}
    if(code==416&&offset>0&&!retried){retried=true;offset=0;c.disconnect();continue;}
    if(code!=200&&code!=206)throw new IOException("HTTP "+code);
    long len=c.getContentLengthLong(),total=len;String etag=c.getHeaderField("ETag"),modified=c.getHeaderField("Last-Modified");if(etag==null||etag.startsWith("W/"))etag="";if(modified==null)modified="";
    if(code==206){
     Matcher m=Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matcher(Objects.toString(c.getHeaderField("Content-Range"),""));
     if(!m.matches()||Long.parseLong(m.group(1))!=offset)throw new IOException("Сервер вернул неправильный диапазон");
     if(offset>0&&((!t.etag.isEmpty()&&!t.etag.equals(etag))||(t.etag.isEmpty()&&!t.modified.isEmpty()&&!t.modified.equals(modified)))){if(retried)throw new IOException("Файл изменился");retried=true;offset=0;c.disconnect();continue;}
     total=m.group(3).equals("*")?(len<0?-1:offset+len):Long.parseLong(m.group(3));
    }else offset=0;
    if(total>=0&&store.dir.getUsableSpace()<Math.max(0,total-offset)+1024*1024)throw new IOException("Недостаточно места");
    check(t,epoch);
    t.name=name(c,u,t.name);String type=c.getContentType();t.mime=type==null?"application/octet-stream":type.split(";")[0].trim();t.etag=etag;t.modified=modified;t.total=total;t.got=offset;store.save();
    long start=SystemClock.elapsedRealtime(),last=start,previous=offset;
    try(InputStream in=new BufferedInputStream(c.getInputStream());FileOutputStream out=new FileOutputStream(part,offset>0)){
     byte[] buf=new byte[128*1024];int n;
     while((n=in.read(buf))!=-1){check(t,epoch);out.write(buf,0,n);t.got+=n;long now=SystemClock.elapsedRealtime();if(now-last>=750){t.speed=(t.got-previous)*1000/Math.max(1,now-last);previous=t.got;last=now;store.save();getSystemService(NotificationManager.class).notify(NOTIF,notification(t));}}
     out.getFD().sync();
    }
    check(t,epoch);if(t.total>=0&&t.got!=t.total)throw new IOException("Загрузка не завершена: неверный размер");store.finish(t,epoch);return;
   }
  }catch(Exception e){store.fail(t,epoch,e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
  finally{if(c!=null)c.disconnect();connection=null;activeId="";store.save();}
 }
 @Override public void onDestroy(){closing=true;Store.Task t=store==null?null:store.get(activeId);if(t!=null&&Store.RUNNING.equals(t.status)){store.pause(t);interrupt(t.id);}worker.shutdownNow();super.onDestroy();}
 @Override public void onTimeout(int startId,int fgsType){Store.Task t=store.get(activeId);if(t!=null){store.pause(t);interrupt(t.id);}stopSelf();}
}
