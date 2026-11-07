package com.example.arkrecruit.translate;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,manifest=Config.NONE)
public class ScreenMemoryTest {
  Context context;TranslationStore store;
  @Before public void setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("translation.db");store=new TranslationStore(context);}
  @After public void cleanup(){store.close();context.deleteDatabase("translation.db");}
  @Test public void independentFramesConfirmScreensAndTouchedRoutes(){
    ScreenMemory m=new ScreenMemory(store);assertEquals(0,m.identify("terminal squads operators",1));
    org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
    assertEquals(0,m.identify("terminal squads operators",1));
    long home=m.identify("terminal squads operators",2);assertTrue(home>0);
    m.touch(.2f,.3f);assertEquals(0,m.identify("recruitment tags timer",3));
    org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
    long recruit=m.identify("recruitment tags timer",4);assertTrue(recruit>0);assertNotEquals(home,recruit);
    assertTrue(store.destinations(home,"1:2").contains(recruit));
    assertEquals(0,m.identify("",5));
  }
  @Test public void upgradeKeepsTranslationsAndTargetIsolation(){
    store.close();context.deleteDatabase("translation.db");
    SQLiteDatabase old=context.openOrCreateDatabase("translation.db",0,null);
    old.execSQL("CREATE TABLE translations(k TEXT PRIMARY KEY,v TEXT NOT NULL,t INTEGER NOT NULL)");
    old.execSQL("CREATE TABLE screens(id INTEGER PRIMARY KEY AUTOINCREMENT,anchors TEXT NOT NULL)");
    old.execSQL("CREATE TABLE routes(origin INTEGER,zone TEXT,dest INTEGER,hits INTEGER,PRIMARY KEY(origin,zone,dest))");
    old.execSQL("INSERT INTO translations VALUES('saved','Сохранено',1)");old.setVersion(1);old.close();
    store=new TranslationStore(context);assertEquals("Сохранено",store.get("saved"));long id=store.addScreen("terminal squads");
    store.visual(id,new byte[]{1},"[{\"source\":\"Squads\",\"translated\":\"\"}]","ru");
    store.visualTranslation(id,"Squads","Загони","uk");
    try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT labels FROM screen_visuals",null)){assertTrue(c.moveToFirst());assertFalse(c.getString(0).contains("Загони"));}
    store.visualTranslation(id,"Squads","Отряды","ru");
    try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT labels FROM screen_visuals",null)){assertTrue(c.moveToFirst());assertTrue(c.getString(0).contains("Отряды"));}
  }
}
