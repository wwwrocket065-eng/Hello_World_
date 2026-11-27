package local.downloads.app;
import android.content.*;import android.net.Uri;import android.util.AtomicFile;import org.json.*;import java.io.*;import java.util.*;import java.net.*;import java.nio.charset.StandardCharsets;

final class Store {
 static final String QUEUED="queued",RUNNING="running",PAUSED="paused",FAILED="failed",DONE="done";
 static Store singleton;
 static synchronized Store get(Context c){if(singleton==null)singleton=new Store(c.getApplicationContext());return singleton;}
 static final class Task{
  final String id,url; volatile String name,mime,status=QUEUED,error="",etag="",modified=""; volatile long got=0,total=-1,speed=0;volatile int epoch=0;
  Task(String id,String url,String name){this.id=id;this.url=url;this.name=name;this.mime="application/octet-stream";}
  JSONObject json()throws JSONException{return new JSONObject().put("id",id).put("url",url).put("name",name).put("mime",mime).put("status",status).put("error",error).put("etag",etag).put("modified",modified).put("got",got).put("total",total);}
 }
 final File dir; final AtomicFile index; final LinkedHashMap<String,Task> tasks=new LinkedHashMap<>();
 Store(Context c){dir=new File(c.getFilesDir(),"downloads");dir.mkdirs();index=new AtomicFile(new File(c.getFilesDir(),"downloads.json"));
  try{JSONArray a=new JSONArray(new String(index.readFully(),StandardCharsets.UTF_8));for(int i=0;i<a.length();i++){JSONObject j=a.getJSONObject(i);String id=j.getString("id");if(!id.matches("[a-f0-9-]{36}"))continue;
   Task t=new Task(id,j.getString("url"),clean(j.optString("name","file")));t.mime=j.optString("mime","application/octet-stream");t.etag=j.optString("etag");t.modified=j.optString("modified");t.total=j.optLong("total",-1);
   t.status=j.optString("status",PAUSED);t.error=j.optString("error");if(data(t).isFile()){t.status=DONE;t.got=data(t).length();t.total=t.got;}
   else{t.got=part(t).length();if(DONE.equals(t.status)||RUNNING.equals(t.status)||QUEUED.equals(t.status)){t.status=PAUSED;t.error="";}}
   tasks.put(id,t);
  }}catch(Exception ignored){}save();
 }
 synchronized void save(){FileOutputStream o=null;try{JSONArray a=new JSONArray();for(Task t:tasks.values())a.put(t.json());o=index.startWrite();o.write(a.toString().getBytes(StandardCharsets.UTF_8));index.finishWrite(o);}catch(Exception e){if(o!=null)index.failWrite(o);android.util.Log.e("Downloads","Cannot persist index",e);}}
 static URL url(String s)throws Exception{URI u=new URI(s.trim());if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null)throw new IOException("Нужна прямая HTTPS-ссылка");return u.toURL();}
 static String clean(String s){s=s.replaceAll("[\\\\/\\p{Cntrl}:*?\"<>|]","_").trim();if(s.isEmpty()||s.equals(".")||s.equals(".."))s="file";if(s.length()>160){int p=s.lastIndexOf('.');String ext=p>0&&s.length()-p<16?s.substring(p):"";s=s.substring(0,144)+ext;}return s;}
 static String guess(URL u){String p=u.getPath();String name=p==null?"":p.substring(p.lastIndexOf('/')+1);try{name=URLDecoder.decode(name.replace("+","%2B"),"UTF-8");}catch(Exception ignored){}return clean(name);}
 synchronized Task add(String link)throws Exception{URL u=url(link);Task t=new Task(UUID.randomUUID().toString(),u.toString(),guess(u));tasks.put(t.id,t);save();return t;}
 synchronized Task get(String id){return tasks.get(id);}
 synchronized List<Task> list(){ArrayList<Task> l=new ArrayList<>(tasks.values());Collections.reverse(l);return l;}
 File part(Task t){return new File(dir,t.id+".part");} File data(Task t){return new File(dir,t.id+".data");}
 synchronized int begin(Task t){if(t==null||tasks.get(t.id)!=t||!QUEUED.equals(t.status))return -1;t.status=RUNNING;t.error="";t.speed=0;t.got=part(t).length();save();return ++t.epoch;}
 synchronized boolean running(Task t,int epoch){return tasks.get(t.id)==t&&RUNNING.equals(t.status)&&t.epoch==epoch;}
 synchronized void pause(Task t){if(t==null||DONE.equals(t.status))return;t.epoch++;t.status=PAUSED;t.speed=0;t.error="";save();}
 synchronized void resume(Task t){if(t==null||DONE.equals(t.status)||RUNNING.equals(t.status)||QUEUED.equals(t.status))return;t.epoch++;t.status=QUEUED;t.error="";save();}
 synchronized void fail(Task t,int epoch,String e){if(running(t,epoch)){t.status=FAILED;t.speed=0;t.error=e;save();}}
 synchronized void finish(Task t,int epoch)throws IOException{if(!running(t,epoch))return;if(!part(t).renameTo(data(t)))throw new IOException("Не удалось сохранить файл");t.got=data(t).length();t.total=t.got;t.speed=0;t.status=DONE;save();}
 synchronized boolean remove(Task t){if(t==null||RUNNING.equals(t.status)||QUEUED.equals(t.status))return false;if(part(t).exists()&&!part(t).delete())return false;if(data(t).exists()&&!data(t).delete())return false;tasks.remove(t.id);save();return true;}
 Uri uri(Task t){return new Uri.Builder().scheme("content").authority("local.downloads.app.files").appendPath(t.id).appendPath(t.name).build();}
}
