package com.example.arkrecruit.translate;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
/** Small durable SQLite store, separate from model files and recruitment settings. */
final class TranslationStore extends SQLiteOpenHelper {
  TranslationStore(Context c){super(c,"translation.db",null,2);setWriteAheadLoggingEnabled(true);}
  public void onCreate(SQLiteDatabase db){
    createVisuals(db);
    db.execSQL("CREATE TABLE translations(k TEXT PRIMARY KEY,v TEXT NOT NULL,t INTEGER NOT NULL)");
    db.execSQL("CREATE TABLE screens(id INTEGER PRIMARY KEY AUTOINCREMENT,anchors TEXT NOT NULL)");
    db.execSQL("CREATE TABLE routes(origin INTEGER,zone TEXT,dest INTEGER,hits INTEGER,PRIMARY KEY(origin,zone,dest))");
  }
  public void onUpgrade(SQLiteDatabase db,int a,int b){if(a<2)createVisuals(db);}
  private void createVisuals(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS screen_visuals(id INTEGER PRIMARY KEY,jpeg BLOB,labels TEXT,target TEXT,updated INTEGER)");}
  synchronized void visual(long id,byte[] jpeg,String labels,String target){
    if(id<=0)return;ContentValues v=new ContentValues();v.put("id",id);v.put("jpeg",jpeg);v.put("labels",labels);v.put("target",target);v.put("updated",System.currentTimeMillis());
    getWritableDatabase().insertWithOnConflict("screen_visuals",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    getWritableDatabase().execSQL("DELETE FROM screen_visuals WHERE id NOT IN (SELECT id FROM screens)");
    getWritableDatabase().execSQL("DELETE FROM routes WHERE origin NOT IN (SELECT id FROM screens) OR dest NOT IN (SELECT id FROM screens)");
  }
  synchronized void visualTranslation(long id,String source,String translated,String target){
    try(Cursor c=getReadableDatabase().rawQuery("SELECT labels,target FROM screen_visuals WHERE id=?",new String[]{""+id})){
      if(!c.moveToFirst()||!target.equals(c.getString(1)))return;
      org.json.JSONArray labels=new org.json.JSONArray(c.getString(0));for(int i=0;i<labels.length();i++){org.json.JSONObject item=labels.getJSONObject(i);if(source.equals(item.getString("source")))item.put("translated",translated);}
      ContentValues v=new ContentValues();v.put("labels",labels.toString());getWritableDatabase().update("screen_visuals",v,"id=?",new String[]{""+id});
    }catch(org.json.JSONException e){android.util.Log.w("ScreenMap","Invalid stored labels",e);}
  }
  String get(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT v FROM translations WHERE k=?",new String[]{key})){return c.moveToFirst()?c.getString(0):null;}}
  void put(String key,String value){
    ContentValues c=new ContentValues();c.put("k",key);c.put("v",value);c.put("t",System.currentTimeMillis());getWritableDatabase().insertWithOnConflict("translations",null,c,SQLiteDatabase.CONFLICT_REPLACE);
    getWritableDatabase().execSQL("DELETE FROM translations WHERE k IN (SELECT k FROM translations ORDER BY t DESC LIMIT -1 OFFSET 20000)");
  }
  LinkedHashMap<Long,String> screens(){LinkedHashMap<Long,String> r=new LinkedHashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,anchors FROM screens ORDER BY id DESC LIMIT 256",null)){while(c.moveToNext())r.put(c.getLong(0),c.getString(1));}return r;}
  long addScreen(String anchors){ContentValues c=new ContentValues();c.put("anchors",anchors);long id=getWritableDatabase().insert("screens",null,c);getWritableDatabase().execSQL("DELETE FROM screens WHERE id NOT IN (SELECT id FROM screens ORDER BY id DESC LIMIT 256)");return id;}
  List<Long> destinations(long origin,String zone){List<Long> r=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT dest FROM routes WHERE origin=? AND zone=? ORDER BY hits DESC LIMIT 8",new String[]{""+origin,zone})){while(c.moveToNext())r.add(c.getLong(0));}return r;}
  void route(long origin,String zone,long dest){if(origin<=0||dest<=0||origin==dest)return;getWritableDatabase().execSQL("INSERT OR IGNORE INTO routes(origin,zone,dest,hits) VALUES(?,?,?,0)",new Object[]{origin,zone,dest});getWritableDatabase().execSQL("UPDATE routes SET hits=hits+1 WHERE origin=? AND zone=? AND dest=?",new Object[]{origin,zone,dest});}
}
