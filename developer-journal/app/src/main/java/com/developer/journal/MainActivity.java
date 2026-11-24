package com.developer.journal;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.webkit.*;
import android.content.*;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import javax.net.ssl.HttpsURLConnection;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private WebView web;
  private final Object lock = new Object();
  private volatile String status = "Локальная версия";
  private static final String PREF = "journal_prefs";
  private static final String SYNC_URL =
    "https://raw.githubusercontent.com/wwwrocket065-eng/Hello_World_/journal-developer-android/developer-journal/data/sync.json";
  private static final String KEY_ALIAS = "journal_device_rsa";
  private static final String CACHE_ALIAS = "journal_cache_aes";

  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    web = new WebView(this);
    web.setBackgroundColor(Color.BLACK);
    web.getSettings().setJavaScriptEnabled(true);
    web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(true);
    web.getSettings().setAllowFileAccessFromFileURLs(false);
    web.getSettings().setAllowUniversalAccessFromFileURLs(false);
    web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
    if (android.os.Build.VERSION.SDK_INT >= 26) web.getSettings().setSafeBrowsingEnabled(true);
    web.addJavascriptInterface(new Bridge(), "Journal");
    web.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {return true;}
      @Override public void onPageFinished(WebView v, String url) {
        v.evaluateJavascript("window.onNativeStatus && window.onNativeStatus("+JSONObject.quote(status)+");",null);
      }
    });
    setContentView(web);
    web.loadUrl("file:///android_asset/index.html");
  }
  @Override protected void onStart() {
    super.onStart();
    new Thread(this::syncOnce).start();  // Only foreground opens; no background workers.
  }
  @Override public void onBackPressed() {
    web.evaluateJavascript("window.uiBack && window.uiBack();", null);
  }
  private byte[] readAll(InputStream in,int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buf=new byte[8192]; int n;
    while((n=in.read(buf))!=-1) {
      if(n==0) continue;
      if(out.size()+n>limit) throw new IOException("Too large");
      out.write(buf,0,n);
    }
    return out.toByteArray();
  }
  private String asset(String name) {
    try(InputStream in=getAssets().open(name)) {return new String(readAll(in,2_000_000),StandardCharsets.UTF_8);}
    catch(Exception ex) {return "{}";}
  }
  private String fetch(String url) throws IOException {
    URL u=new URL(url);
    if(!"https".equals(u.getProtocol()) || !"raw.githubusercontent.com".equals(u.getHost())) throw new IOException("Host");
    HttpsURLConnection c=(HttpsURLConnection)u.openConnection();
    c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
    try{
      if(c.getResponseCode()!=200) throw new IOException("HTTP "+c.getResponseCode());
      try(InputStream in=c.getInputStream()){return new String(readAll(in,3_000_000),StandardCharsets.UTF_8);}
    } finally {c.disconnect();}
  }
  private KeyStore ks() throws Exception {
    KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);return k;
  }
  private PublicKey pub() throws Exception {
    KeyStore k=ks();
    if(!k.containsAlias(KEY_ALIAS)){
      KeyPairGenerator g=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
      g.initialize(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_DECRYPT)
        .setKeySize(2048)
        .setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA1)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP).build());
      g.generateKeyPair();
      k=ks();
    }
    return k.getCertificate(KEY_ALIAS).getPublicKey();
  }
  private String keyId() throws Exception {
    byte[] sha=MessageDigest.getInstance("SHA-256").digest(pub().getEncoded());
    StringBuilder s=new StringBuilder();
    for(int i=0;i<12;i++) s.append(String.format(java.util.Locale.ROOT,"%02x",sha[i]&255));
    return s.toString();
  }
  private SecretKey localKey() throws Exception {
    KeyStore k=ks();
    if(!k.containsAlias(CACHE_ALIAS)){
      KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
      g.init(new KeyGenParameterSpec.Builder(CACHE_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256).build());
      g.generateKey(); k=ks();
    }
    return ((KeyStore.SecretKeyEntry)k.getEntry(CACHE_ALIAS,null)).getSecretKey();
  }
  private void save(String json) throws Exception {
    Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,localKey());
    JSONObject obj=new JSONObject();
    obj.put("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP));
    obj.put("ct",Base64.encodeToString(c.doFinal(json.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP));
    try(FileOutputStream out=openFileOutput("journal_cache.json",MODE_PRIVATE)){
      out.write(obj.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
    }
  }
  private String cachedData() {
    try(FileInputStream in=openFileInput("journal_cache.json")){
      JSONObject obj=new JSONObject(new String(readAll(in,3_000_000),StandardCharsets.UTF_8));
      Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE,localKey(),
        new GCMParameterSpec(128,Base64.decode(obj.getString("iv"),Base64.DEFAULT)));
      String plain=new String(c.doFinal(Base64.decode(obj.getString("ct"),Base64.DEFAULT)),StandardCharsets.UTF_8);
      JSONObject parsed=new JSONObject(plain);
      if(parsed.optInt("schema")!=1||parsed.optJSONArray("sections")==null)return null;
      return plain;
    }catch(Exception ignored){return null;}
  }
  private String load() {
    String cached=cachedData();
    return cached==null?asset("journal.json"):cached;
  }
  private int articleCount(String json){
    try {
      JSONObject root=new JSONObject(json);
      int count=0;
      JSONArray sections=root.getJSONArray("sections");
      for(int s=0;s<sections.length();s++){
        JSONArray chapters=sections.getJSONObject(s).getJSONArray("chapters");
        for(int c=0;c<chapters.length();c++) count+=chapters.getJSONObject(c).getJSONArray("articles").length();
      }
      return count;
    }catch(Exception ignored){return 0;}
  }
  private String syncInfo(){
    JSONObject o=new JSONObject();
    android.content.SharedPreferences p=getSharedPreferences(PREF,MODE_PRIVATE);
    String cached=cachedData();
    try {
      String appVersion=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
      long localRevision=p.getLong("revision",0);
      long remoteRevision=p.getLong("remote_revision",0);
      o.put("appVersion",appVersion);
      o.put("revision",localRevision);
      o.put("checkedAt",p.getLong("last_checked_at",0));
      o.put("appliedAt",p.getLong("last_applied_at",0));
      o.put("publishedAt",remoteRevision==localRevision?p.getString("remote_published_at",""):"");
      o.put("articleCount",articleCount(cached!=null?cached:asset("journal.json")));
      o.put("source",cached!=null?"cache":"bundle");
    }catch(Exception ignored){}
    return o.toString();
  }
  private void report(String text, boolean updated) {
    status=text;
    runOnUiThread(()->{
      if(web==null) return;
      web.evaluateJavascript("window.onNativeStatus && window.onNativeStatus("+JSONObject.quote(text)+");",null);
      if(updated) web.evaluateJavascript("window.reloadJournal && window.reloadJournal();",null);
    });
  }
  private void syncOnce() {
    try {
      report("Проверяю обновления…",false);
      String kid=keyId();
      JSONObject envelope=new JSONObject(fetch(SYNC_URL));
      long revision=envelope.optLong("revision",0);
      android.content.SharedPreferences prefs=getSharedPreferences(PREF,MODE_PRIVATE);
      long current=prefs.getLong("revision",0);
      prefs.edit().putLong("last_checked_at",System.currentTimeMillis())
        .putLong("remote_revision",revision)
        .putString("remote_published_at",envelope.optString("publishedAt",""))
        .apply();
      int expectedCount=envelope.optInt("articleCount",0);
      String cached=cachedData();
      if(revision<=current && cached!=null &&
        (expectedCount<=0||articleCount(cached)==expectedCount)){
        report("Журнал актуален",false);return;
      }
      if(!"AES-256-GCM".equals(envelope.optString("cipher"))) throw new IOException("Unsupported cipher");
      JSONObject recipients=envelope.getJSONObject("recipients");
      if(!recipients.has(kid)){report("Защищённая синхронизация: требуется привязка ключа",false);return;}
      byte[] wrapped=Base64.decode(recipients.getString(kid),Base64.DEFAULT);
      Cipher unwrap=Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
      unwrap.init(Cipher.DECRYPT_MODE,(PrivateKey)ks().getKey(KEY_ALIAS,null),
        new OAEPParameterSpec("SHA-256","MGF1",MGF1ParameterSpec.SHA1,PSource.PSpecified.DEFAULT));
      byte[] aes=unwrap.doFinal(wrapped);
      if(aes.length!=32) throw new IOException("Key length");
      Cipher dec=Cipher.getInstance("AES/GCM/NoPadding");
      dec.init(Cipher.DECRYPT_MODE,new SecretKeySpec(aes,"AES"),
        new GCMParameterSpec(128,Base64.decode(envelope.getString("nonce"),Base64.DEFAULT)));
      String data=new String(dec.doFinal(Base64.decode(envelope.getString("data"),Base64.DEFAULT)),StandardCharsets.UTF_8);
      JSONObject payload=new JSONObject(data);
      if(payload.optInt("schema")!=1 || payload.optJSONArray("sections")==null || data.length()>2_000_000) throw new IOException("Bad data");
      synchronized(lock) {
        save(data);
        if(!prefs.edit().putLong("revision",revision)
          .putLong("last_applied_at",System.currentTimeMillis()).commit())
          throw new IOException("Cannot persist revision");
      }
      report("Обновлено · версия "+revision,true);
    } catch(Exception e) {report("Офлайн · сохранённая версия",false);}
  }
  public class Bridge {
    @JavascriptInterface public String data(){synchronized(lock){return load();}}
    @JavascriptInterface public String status(){return status;}
    @JavascriptInterface public String syncInfo(){return MainActivity.this.syncInfo();}
    @JavascriptInterface public String key() {
      try{return Base64.encodeToString(pub().getEncoded(),Base64.NO_WRAP);}catch(Exception e){return "";}
    }
    @JavascriptInterface public String keyId() {
      try{return MainActivity.this.keyId();}catch(Exception e){return "";}
    }
    @JavascriptInterface public String favorites() {
      return getSharedPreferences(PREF,MODE_PRIVATE).getString("favorites","[]");
    }
    @JavascriptInterface public void setFavorites(String json) {
      try{JSONArray a=new JSONArray(json);if(a.length()<500)
        getSharedPreferences(PREF,MODE_PRIVATE).edit().putString("favorites",a.toString()).apply();
      }catch(Exception ignored){}
    }
    @JavascriptInterface public void copy(String s) {
      runOnUiThread(()->{
        ClipboardManager c=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        c.setPrimaryClip(ClipData.newPlainText("Public key",s));
      });
    }
  }
}
