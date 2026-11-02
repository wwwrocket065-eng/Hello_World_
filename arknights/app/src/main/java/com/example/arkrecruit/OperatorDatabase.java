package com.example.arkrecruit;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

/** Global recruitment data cache. Source: independent EN API (no user GitHub). */
public final class OperatorDatabase {
  private OperatorDatabase() {}
  private static final int CACHE_SCHEMA=2;
  public interface Listener { void onLoaded(List<RecruitmentEngine.Operator> operators, String status); }
  public interface ProgressListener extends Listener { void onProgress(String status); }
  private static final String POOL="https://awedtan.ca/api/recruitpool";
  private static final String OPERATORS="https://awedtan.ca/api/operator?include=data.name&include=data.rarity&include=data.position&include=data.profession&include=data.tagList";
  public static void load(Context context,boolean force,Listener cb){
    Context app=context.getApplicationContext();
    if(app.getSharedPreferences("settings",Context.MODE_PRIVATE).getBoolean("china",false)){
      loadChina(app,force,cb);return;
    }
    new Thread(()->{
      progress(app,cb,"Проверка локальной базы...");
      SharedPreferences p=app.getSharedPreferences("recruit",Context.MODE_PRIVATE);
      // Old schema conflated Support specialization with Supporter profession.
      // It cannot be repaired without the original profession data.
      List<RecruitmentEngine.Operator> cached=p.getInt("schema",0)==CACHE_SCHEMA
        ?parse(p.getString("operators","")):Collections.emptyList();
      boolean due=System.currentTimeMillis()-p.getLong("updated",0)>24L*3600*1000;
      if(!force&&!due&&cached.size()>=100){post(app,cb,cached,"База Global • локальная");return;}
      try {
        progress(app,cb,"Подключение к источнику Global...");
        JSONArray poolResponse=new JSONArray(fetch(POOL));
        JSONArray ids=poolResponse.getJSONObject(0).getJSONArray("value");
        HashSet<String> allowed=new HashSet<>();
        for(int i=0;i<ids.length();i++)allowed.add(ids.getString(i));
        if(allowed.size()<100||allowed.size()>400)throw new IOException("Неверный размер списка рекрутинга");
        progress(app,cb,"Загрузка данных операторов...");
        JSONArray response=new JSONArray(fetch(OPERATORS));
        JSONArray extracted=new JSONArray();
        for(int i=0;i<response.length();i++){
          JSONObject record=response.optJSONObject(i);
          if(record==null||!allowed.contains(record.optString("canon")))continue;
          JSONObject value=record.optJSONObject("value");if(value==null)continue;
          JSONObject data=value.optJSONObject("data");if(data==null)continue;
          String name=data.optString("name","").trim();
          int rarity=readRarity(data.optString("rarity",""));
          if(name.isEmpty()||rarity<1||rarity>6)continue;
          LinkedHashSet<String> tags=new LinkedHashSet<>();
          addTag(tags,data.optString("position",""));
          String profession=OperatorTagMapper.profession(data.optString("profession",""));
          if(profession==null)continue;
          tags.add(profession);
          JSONArray special=data.optJSONArray("tagList");
          if(special!=null)for(int j=0;j<special.length();j++)addTag(tags,special.optString(j));
          if(tags.size()<2)continue;
          JSONObject x=new JSONObject();x.put("name",name);x.put("rarity",rarity);
          x.put("tags",new JSONArray(tags));extracted.put(x);
        }
        progress(app,cb,"Проверка и сравнение базы...");
        List<RecruitmentEngine.Operator> fresh=parse(extracted.toString());
        // Fail closed: never replace a reliable cache with incomplete data.
        if(fresh.size()<100||fresh.size()>400)throw new IOException("Неполная база: "+fresh.size());
        progress(app,cb,"Сохранение проверенной базы...");
        p.edit().putString("operators",extracted.toString()).putLong("updated",System.currentTimeMillis()).putInt("schema",CACHE_SCHEMA).apply();
        post(app,cb,fresh,"База Global обновлена: "+fresh.size()+" операторов");
      }catch(Exception e){
        post(app,cb,cached,cached.isEmpty()?
          "Нет проверенной базы: нужен интернет ("+e.getClass().getSimpleName()+")":
          "Не удалось обновить — сохранена проверенная база");
      }
    },"GlobalRecruitData").start();
  }
  private static void loadChina(Context app,boolean force,Listener cb){
    new Thread(()->{
      SharedPreferences cache=app.getSharedPreferences("recruit_china",Context.MODE_PRIVATE);
      String bundled="[]";
      try(InputStream in=app.getAssets().open("operators/china.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
        bundled=out.toString("UTF-8");
      }catch(Exception e){android.util.Log.w("RecruitAssist","China bundled data unavailable",e);}
      String previous=cache.getString("operators",bundled);
      List<RecruitmentEngine.Operator> cached=parse(previous);
      if(!cached.isEmpty())new android.os.Handler(app.getMainLooper()).post(()->cb.onLoaded(cached,"База China • локальная"));
      if(!force&&System.currentTimeMillis()-cache.getLong("updated",0)<24L*3600*1000)return;
      try{
        progress(app,cb,"Обновление базы China...");
        String base="https://raw.githubusercontent.com/Kengxxiao/ArknightsGameData/master/zh_CN/gamedata/excel/";
        JSONObject gacha=new JSONObject(fetch(base+"gacha_table.json"));
        JSONObject characters=new JSONObject(fetch(base+"character_table.json"));
        JSONArray fresh=ChinaRecruitment.extract(gacha,characters,new JSONArray(bundled));
        List<RecruitmentEngine.Operator> list=parse(fresh.toString());
        if(list.size()<100||list.size()>400)throw new IOException("Неполная база China");
        cache.edit().putString("operators",fresh.toString()).putLong("updated",System.currentTimeMillis()).apply();
        new android.os.Handler(app.getMainLooper()).post(()->cb.onLoaded(list,"База China обновлена: "+list.size()));
      }catch(Exception e){
        android.util.Log.w("RecruitAssist","China data update failed",e);
        if(cached.isEmpty())new android.os.Handler(app.getMainLooper()).post(()->cb.onLoaded(cached,"Нет проверенной базы China: нужен интернет"));
      }
    },"ChinaRecruitData").start();
  }
  private static String fetch(String url)throws IOException{
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(10000);c.setReadTimeout(18000);
    c.setRequestProperty("Accept","application/json");
    c.setRequestProperty("User-Agent","ArknightsRecruitAssist/0.2");
    try {
      if(c.getResponseCode()!=200)throw new IOException("HTTP "+c.getResponseCode());
      try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){
          out.write(buffer,0,n);
          if(out.size()>32_000_000)throw new IOException("Oversized response");
        }
        return out.toString("UTF-8");
      }
    } finally {c.disconnect();}
  }
  private static void post(Context c,Listener cb,List<RecruitmentEngine.Operator> list,String status){
    long updated=c.getSharedPreferences("recruit",Context.MODE_PRIVATE).getLong("updated",0);
    String suffix=updated>0? "\nПоследнее успешное обновление: "+
      new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale.getDefault())
        .format(new java.util.Date(updated)):"\nУспешных обновлений ещё не было";
    new android.os.Handler(c.getMainLooper()).post(()->cb.onLoaded(list,status+suffix));
  }
  private static void progress(Context c,Listener cb,String state){
    if(cb instanceof ProgressListener)
      new android.os.Handler(c.getMainLooper()).post(()->((ProgressListener)cb).onProgress(state));
  }
  private static List<RecruitmentEngine.Operator> parse(String s){
    List<RecruitmentEngine.Operator> out=new ArrayList<>();
    try {
      JSONArray arr=new JSONArray(s);
      for(int i=0;i<arr.length();i++){
        JSONObject o=arr.getJSONObject(i);
        JSONArray tags=o.getJSONArray("tags");
        List<String> t=new ArrayList<>();
        for(int j=0;j<tags.length();j++)t.add(tags.getString(j));
        out.add(new RecruitmentEngine.Operator(o.getString("name"),o.getInt("rarity"),t));
      }
    }catch(Exception ignored){}
    return out;
  }
  private static int readRarity(String s){
    try {
      String digits=s.replaceAll("[^0-9]","");
      int r=Integer.parseInt(digits);return r;
    }catch(Exception e){return 0;}
  }
  private static void addTag(Set<String> tags,String original){
    String canon=OperatorTagMapper.specialization(original);
    if(canon!=null)tags.add(canon);
  }
}
