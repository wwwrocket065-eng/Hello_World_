package com.cloudlab.raymarch;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean running=new AtomicBoolean(false);
    private static final int SAMPLES_PER_BATCH=80*45*22*2;
    private static boolean libsOk=false;
    private static String loadError="";
    static {
        try {
            System.loadLibrary("cloud_goose");
            System.loadLibrary("cloud_rust");
            System.loadLibrary("cloud_cpp");
            libsOk=true;
        } catch (Throwable e) {
            loadError=e.toString();
        }
    }
    private static native double gooseBatch(double cloud, long tick);
    private static native double rustBatch(double cloud, long tick);
    private static native double cppBatch(double cloud, long tick);
    private double compute(int index,double cloud,long tick){
        if(index==0) return gooseBatch(cloud,tick);
        if(index==1) return rustBatch(cloud,tick);
        return cppBatch(cloud,tick);
    }
    private void send(JSONObject data) {
        String js="window.CloudLabNativeEvent && window.CloudLabNativeEvent("+data.toString()+")";
        runOnUiThread(()->{if(web!=null) web.evaluateJavascript(js,null);});
    }
    private JSONObject event(String kind) {
        JSONObject o=new JSONObject();
        try {o.put("kind",kind);}catch(Exception ignored){}
        return o;
    }
    private void error(String s) {
        try {JSONObject o=event("error");o.put("message",s);send(o);}catch(Exception ignored){}
    }
    private void runBenchmark(double percent,int seconds) {
        try{
            if(!libsOk){error("Загрузка модулей: "+loadError);return;}
            final double cloud=Math.max(0,Math.min(1,percent/100.0));
            final int length=(seconds==30||seconds==60)?seconds:15;
            String[] languages={"Goose","Rust","C++"};
            JSONObject begin=event("validating");send(begin);
            double[] covers={0.0,.25,.54,.77,1.0};
            long[] ticks={0,37,512,4095};
            for(double c:covers)for(long t:ticks){
                double reference=cppBatch(c,t);
                for(int i=0;i<2;i++){
                    double v=compute(i,c,t);
                    if(!Double.isFinite(v)||Math.abs(v-reference)>1e-6*Math.max(1,Math.abs(reference))){
                        error("Проверка расчётов не пройдена: "+languages[i]+" ("+c+", "+t+")");return;
                    }
                }
            }
            JSONObject ok=event("validated");send(ok);
            for(int index=0;index<3;index++){
                JSONObject starting=event("start");
                starting.put("language",languages[index]);starting.put("index",index);
                starting.put("seconds",length);send(starting);
                // One dedicated worker thread, one language at a time; no rendering during benchmark.
                double checksum=0.0;
                long tick=0;
                long wall0=System.nanoTime(), cpu0=Debug.threadCpuTimeNanos();
                long nextUpdate=wall0+1_000_000_000L;
                long end=wall0+(long)length*1_000_000_000L;
                do {
                    checksum+=compute(index,cloud,tick++);
                    long now=System.nanoTime();
                    if(now>=nextUpdate){
                        JSONObject progress=event("progress");
                        progress.put("index",index);
                        progress.put("elapsed",Math.min(length,(now-wall0)*1e-9));
                        progress.put("seconds",length);send(progress);
                        nextUpdate=now+1_000_000_000L;
                    }
                }while(System.nanoTime()<end);
                long wall1=System.nanoTime(), cpu1=Debug.threadCpuTimeNanos();
                double wall=(wall1-wall0)*1e-9,cpu=(cpu1-cpu0)*1e-9;
                JSONObject result=event("result");
                result.put("index",index);result.put("language",languages[index]);
                result.put("cloud",percent);result.put("elapsed",wall);
                result.put("batches",tick);
                result.put("batchesPerSec",tick/wall);
                result.put("msPerBatch",wall*1000.0/tick);
                result.put("samplesMPerSec",(tick/wall)*SAMPLES_PER_BATCH/1_000_000.0);
                result.put("cpuPercent",Math.max(0,Math.min(100.0,cpu/wall*100.0)));
                result.put("checksum",checksum);send(result);
            }
            send(event("done"));
        }catch(Throwable t){
            error("Ошибка теста: "+t.getClass().getSimpleName()+": "+t.getMessage());
        }finally{
            running.set(false);
        }
    }
    public final class Bridge {
        @JavascriptInterface public void start(double cloudPercent,int seconds){
            if(!running.compareAndSet(false,true))return;
            executor.execute(()->runBenchmark(cloudPercent,seconds));
        }
        @JavascriptInterface public boolean copyReport(String report) {
            if (report == null || report.isEmpty()) return false;
            try {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard == null) return false;
                clipboard.setPrimaryClip(ClipData.newPlainText("CloudLab report", report));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        @JavascriptInterface public boolean available(){return libsOk;}
        @JavascriptInterface public String version(){return "CloudLab 3Lang JNI v1";}
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        web=new WebView(this);
        web.setBackgroundColor(0xff080d1b);
        WebSettings w=web.getSettings();w.setJavaScriptEnabled(true);w.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new Bridge(),"CloudLabNative");
        setContentView(web);
        // Android 15+ enforces edge-to-edge for apps targeting API 35.
        // Keep the cloud scene and controls clear of the system bars.
        if (android.os.Build.VERSION.SDK_INT >= 35) {
            web.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                    android.view.WindowInsets.Type.systemBars() |
                    android.view.WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
            web.requestApplyInsets();
        }
        web.loadUrl("file:///android_asset/index.html");
    }
    @Override public void onDestroy(){executor.shutdownNow();if(web!=null){web.destroy();web=null;}super.onDestroy();}
}