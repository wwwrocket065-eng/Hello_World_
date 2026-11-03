package com.example.arkrecruit.translate;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;
/** Small durable SQLite store, separate from model files and recruitment settings. */
final class TranslationStore extends SQLiteOpenHelper {
  TranslationStore(Context c){super(c,"translation.db",null,1);setWriteAheadLoggingEnabled(true);}
  public void onCreate(SQLiteDatabase db){
    db.execSQL("CREATE TABLE translations(k TEXT PRIMARY KEY,v TEXT NOT NULL,t INTEGER NOT NULL)");
    db.execSQL("CREATE TABLE screens(id INTEGER PRIMARY KEY AUTOINCREMENT,anchors TEXT NOT NULL)");
    db.execSQL("CREATE TABLE routes(origin INTEGER,zone TEXT,dest INTEGER,hits INTEGER,PRIMARY KEY(origin,zone,dest))");
  }
  public void onUpgrade(SQLiteDatabase db,int a,int b){}
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
