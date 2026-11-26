package local.airis.agents;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.net.Uri;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.view.Gravity;
public class MainActivity extends Activity {
    private static final String HOME = "http://127.0.0.1:8765/";
    private WebView web;
    private boolean failed;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); showChat();
    }
    private void showChat() {
        failed=false;
        if(web!=null) web.destroy();
        web=new WebView(this); web.setBackgroundColor(Color.BLACK);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u=r.getUrl(); return !("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost()) && u.getPort()==8765);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError err) {
                if(r.isForMainFrame()&&!failed){failed=true;v.post(()->showOffline());}
            }
        });
        setContentView(web); web.loadUrl(HOME);
    }
    private void showOffline() {
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER);layout.setPadding(48,48,48,48);layout.setBackgroundColor(Color.BLACK);
        TextView title=new TextView(this);title.setText("Konsilium");title.setTextSize(30);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER);layout.addView(title);
        TextView desc=new TextView(this);desc.setText("Локальная среда не запущена.\n\nВ Termux запусти engine/start.sh, затем вернись сюда.");desc.setTextColor(0xffaaaaaa);desc.setTextSize(16);desc.setGravity(Gravity.CENTER);desc.setPadding(0,25,0,25);layout.addView(desc);
        Button retry=new Button(this);retry.setText("Подключиться");retry.setOnClickListener(v->showChat());layout.addView(retry,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));setContentView(layout);
    }
    @Override protected void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
