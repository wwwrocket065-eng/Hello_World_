package local.airis.agents;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.*;
import android.net.Uri;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;
import org.mozilla.javascript.*;

public class MainActivity extends Activity {
    private WebView web;
    private Engine engine;
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        engine=new Engine();
        web=new WebView(this); web.setBackgroundColor(Color.BLACK);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                Uri u=r.getUrl();
                if("https".equals(u.getScheme())&&"app.konsilium.local".equals(u.getHost())&&"/index.html".equals(u.getPath())){
                    try{return new WebResourceResponse("text/html","UTF-8",getAssets().open("index.html"));}catch(Exception ignored){}
                }
                return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(engine,"Native");
        setContentView(web);web.loadUrl("https://app.konsilium.local/index.html");
        engine.restore();
    }
    @Override public void onDestroy(){if(engine!=null)engine.close();if(web!=null){web.removeJavascriptInterface("Native");web.destroy();}super.onDestroy();}
    @Override public void onBackPressed(){if(web!=null)web.evaluateJavascript("back()",null);}
    static JSONObject obj(Object... items){
        JSONObject j=new JSONObject();try{for(int i=0;i<items.length;i+=2)j.put((String)items[i],items[i+1]);}catch(Exception e){throw new RuntimeException(e);}return j;
    }
    static String read(InputStream in,int max) throws IOException{
        try(InputStream x=in;ByteArrayOutputStream b=new ByteArrayOutputStream()){
            byte[] buf=new byte[8192];int n;while((n=x.read(buf))!=-1){if(b.size()+n>max)throw new IOException("Слишком большой ответ");b.write(buf,0,n);}
            return b.toString("UTF-8");
        }
    }
    static final String[] IDS={"qwen35-4b","qwen-coder-3b"};
    static final String[] TITLES={"Qwen3.5-4B · Q4_K_M","Qwen2.5-Coder-3B · Q4_K_M"};
    static final String[] URLS={"https://huggingface.co/ggml-org/Qwen3.5-4B-GGUF/resolve/main/Qwen3.5-4B-Q4_K_M.gguf","https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf"};
    static final String[] NAMES={"Айрис","Пикси","Вега","Астра","Нова","Лира","Орион","Сириус","Аврора","Эхо"};
    static final String[] ROLES={"Координатор: план и критерии успеха","Программист: реализуй и исправь","Тестировщик: проверь и исправь ошибки","Архитектор: продумай структуру","Ревьюер: проверь качество","Исследователь: анализируй доступные данные","Безопасность: проверь риски","Оптимизатор: улучшай производительность","Дизайнер: улучшай интерфейс","Документатор: опиши результат"};
    final class Engine {
        final SharedPreferences prefs=getSharedPreferences("konsilium",MODE_PRIVATE);
        final File models=new File(getFilesDir(),"models"),projects=new File(getFilesDir(),"projects");
        final ExecutorService worker=Executors.newSingleThreadExecutor(), downloader=Executors.newSingleThreadExecutor();
        final ConcurrentLinkedQueue<JSONObject> events=new ConcurrentLinkedQueue<>();
        volatile boolean busy=false,loading=false,ready=false,downloading=false,closed=false,stop=false;
        volatile int active=-1,downloadIndex=-1,port;
        volatile long got=0,total=0;
        volatile String error="",downloadError="",key="";
        volatile java.lang.Process process;
        volatile HttpURLConnection request;
        Engine(){models.mkdirs();projects.mkdirs();}
        File file(int i){return new File(models,IDS[i]+".gguf");}
        File part(int i){return new File(models,IDS[i]+".part");}
        boolean valid(int i){return i>=0&&i<IDS.length;}
        void emit(String type,Object... values){JSONObject j=obj(values);try{j.put("type",type);}catch(Exception ignored){}events.add(j);}
        void restore(){int a=prefs.getInt("active",-1);if(valid(a)&&file(a).isFile())select(a);}
        @JavascriptInterface public String drain(){JSONArray a=new JSONArray();JSONObject j;while((j=events.poll())!=null)a.put(j);return a.toString();}
        @JavascriptInterface public String state(){
            JSONArray m=new JSONArray(),p=new JSONArray();for(int i=0;i<IDS.length;i++)m.put(obj("name",TITLES[i],"installed",file(i).isFile(),"bytes",file(i).length(),"partial",part(i).length(),"prompt",prefs.getString("m"+i,"")));
            for(int i=0;i<NAMES.length;i++)p.put(obj("name",NAMES[i],"role",ROLES[i],"prompt",prefs.getString("a"+i,"")));
            return obj("models",m,"agents",p,"count",prefs.getInt("count",5),"active",active,"ready",ready,"loading",loading,"busy",busy,"downloading",downloading,"downloadIndex",downloadIndex,"got",got,"total",total,"error",error,"downloadError",downloadError,"allow",prefs.getBoolean("allow",false)).toString();
        }
        @JavascriptInterface public void setCount(int n){if(n>=1&&n<=10)prefs.edit().putInt("count",n).apply();}
        @JavascriptInterface public void setAllow(boolean b){prefs.edit().putBoolean("allow",b).apply();}
        @JavascriptInterface public void savePrompt(String scope,int i,String text){if(text!=null&&text.length()<=4000&&(("m".equals(scope)&&valid(i))||("a".equals(scope)&&i>=0&&i<NAMES.length)))prefs.edit().putString(scope+i,text.trim()).apply();}
        @JavascriptInterface public synchronized String download(int i){
            if(!valid(i)||file(i).isFile()||downloading)return "Загрузка недоступна";
            downloading=true;downloadIndex=i;got=part(i).length();total=0;downloadError="";
            downloader.execute(()->{
                HttpURLConnection c=null;try{
                    long off=part(i).length();
                    c=(HttpURLConnection)new URL(URLS[i]).openConnection();c.setConnectTimeout(30000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);
                    if(off>0)c.setRequestProperty("Range","bytes="+off+"-");
                    int code=c.getResponseCode();
                    if(code==206){String range=c.getHeaderField("Content-Range");if(range==null||!range.startsWith("bytes "+off+"-"))throw new IOException("Неверный диапазон");total=Long.parseLong(range.substring(range.lastIndexOf('/')+1));}
                    else if(code==200){off=0;total=c.getContentLengthLong();}else throw new IOException("HTTP "+code);
                    if(total>0&&models.getUsableSpace()<total-off+64*1024*1024)throw new IOException("Недостаточно места");
                    got=off;try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part(i),off>0)){byte[] b=new byte[256*1024];int n;while(!closed&&(n=in.read(b))!=-1){out.write(b,0,n);got+=n;}}
                    if(closed)throw new IOException("Загрузка остановлена");
                    if(total<=0||got!=total)throw new IOException("Загрузка не завершена");
                    try(FileInputStream in=new FileInputStream(part(i))){byte[] h=new byte[4];if(in.read(h)!=4||!"GGUF".equals(new String(h,StandardCharsets.US_ASCII)))throw new IOException("Неверный GGUF");}
                    if(!part(i).renameTo(file(i)))throw new IOException("Не удалось сохранить модель");
                }catch(Exception e){downloadError=e.toString();}finally{if(c!=null)c.disconnect();downloading=false;}
            });return "";
        }
        void stopProcess(){ready=false;java.lang.Process p=process;process=null;if(p!=null){p.destroy();try{if(!p.waitFor(2,TimeUnit.SECONDS))p.destroyForcibly();}catch(Exception ignored){p.destroyForcibly();}}}
        @JavascriptInterface public synchronized String delete(int i){
            if(!valid(i)||busy||loading||(downloading&&downloadIndex==i))return "Операция недоступна";
            if(active==i){stopProcess();active=-1;prefs.edit().putInt("active",-1).apply();}
            if(file(i).exists()&&!file(i).delete())return "Не удалось удалить";
            part(i).delete();return "";
        }
        @JavascriptInterface public synchronized String select(int i){
            if(!valid(i)||!file(i).isFile()||busy||loading)return "Сначала скачай модель или дождись окончания операции";
            loading=true;error="";worker.execute(()->{
                try{
                    stopProcess();active=-1;
                    File lib=new File(getApplicationInfo().nativeLibraryDir,"libllamaserver.so");
                    if(!lib.isFile())throw new IOException("Встроенный движок не найден");
                    try(ServerSocket socket=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=socket.getLocalPort();}
                    key=UUID.randomUUID().toString();
                    ProcessBuilder pb=new ProcessBuilder(lib.getAbsolutePath(),"-m",file(i).getAbsolutePath(),"--host","127.0.0.1","--port",""+port,"--api-key",key,"-c","4096","-t","4","-np","1","--jinja");
                    pb.environment().put("LD_LIBRARY_PATH",getApplicationInfo().nativeLibraryDir);pb.environment().put("HOME",getFilesDir().getAbsolutePath());pb.environment().put("TMPDIR",getCacheDir().getAbsolutePath());
                    pb.directory(getFilesDir());pb.redirectErrorStream(true);pb.redirectOutput(new File(getFilesDir(),"llama.log"));process=pb.start();
                    for(int n=0;n<240&&!closed;n++){
                        if(process==null||!process.isAlive())throw new IOException("Движок завершился: "+logTail());
                        try{JSONObject health=call("/health",null,1500);if("ok".equals(health.optString("status"))){active=i;ready=true;prefs.edit().putInt("active",i).apply();return;}}catch(Exception ignored){}
                        Thread.sleep(1000);
                    }throw new IOException("Модель не загрузилась вовремя");
                }catch(Exception e){error=e.toString();stopProcess();}finally{loading=false;}
            });return "";
        }
        String logTail(){try(RandomAccessFile f=new RandomAccessFile(new File(getFilesDir(),"llama.log"),"r")){f.seek(Math.max(0,f.length()-2500));byte[] b=new byte[(int)(f.length()-f.getFilePointer())];f.readFully(b);return new String(b,StandardCharsets.UTF_8);}catch(Exception e){return "";}}
        @JavascriptInterface public String diagnostics(){return "Konsilium 0.5.0\nABI: "+Arrays.toString(android.os.Build.SUPPORTED_ABIS)+"\nNative: "+getApplicationInfo().nativeLibraryDir+"\n"+state()+"\n"+logTail();}
        JSONObject call(String path,JSONObject body,int timeout)throws Exception{
            HttpURLConnection c=(HttpURLConnection)new URL("http://127.0.0.1:"+port+path).openConnection();c.setConnectTimeout(3000);c.setReadTimeout(timeout);c.setRequestProperty("Authorization","Bearer "+key);
            if(body!=null)request=c;
            try{
                if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
                int code=c.getResponseCode();if(code!=200)throw new IOException("HTTP "+code+" "+read(c.getErrorStream()==null?new ByteArrayInputStream(new byte[0]):c.getErrorStream(),65536));
                return new JSONObject(read(c.getInputStream(),1024*1024));
            }finally{c.disconnect();if(body!=null&&request==c)request=null;}
        }
        String ask(String system,String user)throws Exception{
            if(stop||closed)throw new IOException("Остановлено");
            JSONArray msgs=new JSONArray().put(obj("role","system","content",system)).put(obj("role","user","content",user));
            JSONObject result=call("/v1/chat/completions",obj("model","local","messages",msgs,"temperature",0.4,"max_tokens",1100,"stream",false,"chat_template_kwargs",obj("enable_thinking",false)),300000);
            String content=result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");
            if(content.trim().isEmpty())throw new IOException("Пустой ответ модели");return content;
        }
        @JavascriptInterface public synchronized String improve(String scope,int i,String text){
            if(!ready||busy||loading)return "Сначала запусти модель и дождись освобождения";
            if(text==null||text.trim().isEmpty()||text.length()>4000)return "Введи промпт";
            if("m".equals(scope)&&i!=active)return "Сначала активируй эту модель";
            if(!("m".equals(scope)&&valid(i))&&!("a".equals(scope)&&i>=0&&i<NAMES.length))return "Неизвестный редактор";
            busy=true;stop=false;worker.execute(()->{try{String answer=ask("Улучши системный промпт на русском языке, сохрани намерение автора. Верни только готовый промпт, без пояснений.",text);emit("improved","scope",scope,"i",i,"text",answer);}catch(Exception e){emit("error","text",e.toString());}finally{busy=false;}});return "";
        }
        File project(String id)throws IOException{if(id==null||!id.matches("[a-zA-Z0-9_-]{1,80}"))throw new IOException("Неверный проект");File f=new File(projects,id);f.mkdirs();return f;}
        File safe(File base,String path)throws IOException{File f=new File(base,path).getCanonicalFile();if(!f.getPath().startsWith(base.getCanonicalPath()+File.separator))throw new IOException("Путь вне проекта");return f;}
        String list(File base,File dir,int depth)throws IOException{if(depth>4)return "";StringBuilder b=new StringBuilder();File[] ff=dir.listFiles();if(ff!=null)for(File f:ff){if(b.length()>8000)break;if(f.isDirectory())b.append(list(base,f,depth+1));else b.append(base.toURI().relativize(f.toURI()).getPath()).append('\n');}return b.toString();}
        @JavascriptInterface public String files(String id){try{return list(project(id),project(id),0);}catch(Exception e){return e.toString();}}
        @JavascriptInterface public String readFile(String id,String path){try{return read(new FileInputStream(safe(project(id),path)),200000);}catch(Exception e){return e.toString();}}
        String tool(File base,JSONObject t,boolean allow)throws Exception{
            String op=t.optString("tool"),path=t.optString("path");
            if("list".equals(op))return list(base,base,0);
            if("read".equals(op))return read(new FileInputStream(safe(base,path)),16000);
            if("write".equals(op)){String content=t.getString("content");if(content.length()>100000)throw new IOException("Файл слишком большой");File f=safe(base,path);f.getParentFile().mkdirs();try(FileOutputStream o=new FileOutputStream(f)){o.write(content.getBytes(StandardCharsets.UTF_8));}return "Записан "+path;}
            if("run_js".equals(op)){
                if(!allow)return "Запуск запрещён пользователем";
                String code=read(new FileInputStream(safe(base,path)),100000);
                return new JsSandbox().run(code);
            }
            return "Неизвестный инструмент";
        }
        @JavascriptInterface public synchronized String send(String id,String text,String history){
            if(!ready||loading||busy)return "Сначала скачай и запусти модель";
            if(text==null||text.trim().isEmpty()||text.length()>16000)return "Некорректная задача";
            busy=true;stop=false;final int count=prefs.getInt("count",5),model=active;
            final boolean allow=prefs.getBoolean("allow",false);
            final String common=prefs.getString("m"+model,"");final String[] prompts=new String[count];for(int i=0;i<count;i++)prompts[i]=prefs.getString("a"+i,"");
            worker.execute(()->{try{
                File base=project(id);String discussion=history==null?"":history;discussion=tail(discussion,6500);
                int[] order=count>=4?new int[count+1]:new int[count+1];order[0]=0;int p=1;if(count>=4)order[p++]=3;for(int i=1;i<count;i++)if(i!=3)order[p++]=i;order[p]=0;
                int rounds=count==1?1:order.length;
                for(int k=0;k<rounds&&!stop;k++){
                    int a=order[k];String role=k==rounds-1&&count>1?"Координатор: подведи итог, не утверждай непроверенное":ROLES[a];
                    String system="Ты участник команды "+NAMES[a]+". "+role+".\n"+common+"\n"+prompts[a]+"\nОтвечай на русском. Для работы с проектом верни JSON: {\"message\":\"текст\",\"tools\":[{\"tool\":\"list\"},{\"tool\":\"read\",\"path\":\"main.js\"},{\"tool\":\"write\",\"path\":\"main.js\",\"content\":\"...\"},{\"tool\":\"run_js\",\"path\":\"main.js\"}]}. Используй только нужные инструменты, иначе tools пуст. Встроенный исполнитель поддерживает только JavaScript, не выдумывай компиляторы. Не утверждай успех без вывода.";
                    emit("thinking","agent",a);
                    String info="";
                    for(int attempt=0;attempt<3&&!stop;attempt++){
                        String answer=ask(system,"Задача: "+tail(text,6000)+"\nОбсуждение:\n"+tail(discussion,6500)+"\nИнструменты:\n"+tail(info,3000));
                        if(stop)break;
                        JSONObject parsed=null;try{int l=answer.indexOf('{'),r=answer.lastIndexOf('}');if(l>=0&&r>l)parsed=new JSONObject(answer.substring(l,r+1));}catch(Exception ignored){}
                        String msg=parsed==null?answer:parsed.optString("message","");
                        if(!msg.isEmpty()){emit("message","agent",a,"text",msg);discussion=tail(discussion+"\n"+NAMES[a]+": "+msg,10000);}
                        JSONArray actions=parsed==null?null:parsed.optJSONArray("tools");if(actions==null||actions.length()==0)break;
                        info="";for(int z=0;z<Math.min(8,actions.length())&&!stop;z++){JSONObject action=actions.getJSONObject(z);String result;try{result=tool(base,action,allow);}catch(Exception e){result="Ошибка: "+e.getMessage();}info+=action.optString("tool")+": "+tail(result,2500)+"\n";emit("tool","agent",a,"text",action.optString("tool")+": "+tail(result,2500));}
                        discussion=tail(discussion+"\n"+info,10000);
                    }
                }
            }catch(Exception e){if(!stop)emit("error","text",e.toString());}finally{busy=false;emit("done");}});return "";
        }
        String tail(String s,int n){return s.length()>n?s.substring(s.length()-n):s;}
        @JavascriptInterface public void cancel(){stop=true;HttpURLConnection c=request;if(c!=null)c.disconnect();}
        void close(){closed=true;cancel();stopProcess();worker.shutdownNow();downloader.shutdownNow();}
    }
    static final class JsSandbox extends ContextFactory {
        long deadline;
        @Override protected Context makeContext(){Context cx=super.makeContext();cx.setOptimizationLevel(-1);cx.setInstructionObserverThreshold(1000);cx.setClassShutter(name->false);return cx;}
        @Override protected void observeInstructionCount(Context cx,int count){if(System.nanoTime()>deadline)throw new RuntimeException("Превышено время выполнения JS");}
        String run(String code){
            deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
            return call(cx->{Scriptable scope=cx.initSafeStandardObjects();StringBuilder out=new StringBuilder();
                BaseFunction print=new BaseFunction(){@Override public Object call(Context c,Scriptable s,Scriptable t,Object[] args){for(Object a:args){if(out.length()<12000)out.append(Context.toString(a)).append(' ');}out.append('\n');return Undefined.instance;}};
                ScriptableObject.putProperty(scope,"print",print);Scriptable console=cx.newObject(scope);ScriptableObject.putProperty(console,"log",print);ScriptableObject.putProperty(scope,"console",console);
                Script script=cx.compileString(code,"project.js",1,null);Object result=script.exec(cx,scope);return "JS: код выхода 0\n"+out+(result==Undefined.instance?"":Context.toString(result));});
        }
    }
}