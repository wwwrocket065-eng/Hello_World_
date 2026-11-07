package com.example.arkrecruit.translate;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.content.*;
import android.database.Cursor;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Read-only view of the translator's durable screen memory. No overlay or new permissions. */
public final class ScreenMapActivity extends Activity {
  private final ExecutorService io=Executors.newSingleThreadExecutor();
  private final Handler main=new Handler(Looper.getMainLooper());
  private TranslationStore store;private MapView map;private TextView status;private boolean destroyed;
  private static final int BG=0xff111b2b, CARD=0xff20334b, TEXT=0xffe6edf5, BLUE=0xff77baff;
  static final class Entry {long id;String anchors,labels,target;Bitmap thumb;float x,y;}
  static final class Route {long from,to;String zone;int hits;}
  public void onCreate(Bundle saved){super.onCreate(saved);store=new TranslationStore(this);
    LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(BG);page.setFitsSystemWindows(true);
    LinearLayout bar=new LinearLayout(this);Button back=button("‹",()->finish());bar.addView(back);
    TextView title=new TextView(this);title.setText("Карта экранов");title.setTextColor(TEXT);title.setTextSize(21);title.setGravity(Gravity.CENTER_VERTICAL);bar.addView(title,new LinearLayout.LayoutParams(0,-1,1));
    bar.addView(button("⟳",()->load()));bar.addView(button("⌂",()->map.reset()));page.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));
    status=new TextView(this);status.setTextColor(TEXT);status.setPadding(dp(12),dp(6),dp(12),dp(6));status.setText("Загрузка карты…");page.addView(status);
    map=new MapView(this);page.addView(map,new LinearLayout.LayoutParams(-1,0,1));setContentView(page);load();
  }
  private Button button(String text,Runnable action){Button b=new Button(this);b.setText(text);b.setTextColor(TEXT);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CARD));b.setOnClickListener(v->action.run());return b;}
  private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
  private void load(){io.execute(()->{
    ArrayList<Entry> entries=new ArrayList<>();ArrayList<Route> routes=new ArrayList<>();
    try{
      try(Cursor c=store.getReadableDatabase().rawQuery("SELECT s.id,s.anchors,v.jpeg,v.labels,v.target FROM screens s LEFT JOIN screen_visuals v ON v.id=s.id ORDER BY s.id",null)){
        while(c.moveToNext()){Entry e=new Entry();e.id=c.getLong(0);e.anchors=c.getString(1);e.labels=c.isNull(3)?"[]":c.getString(3);e.target=c.isNull(4)?"":c.getString(4);
          if(!c.isNull(2)){byte[] data=c.getBlob(2);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=4;e.thumb=BitmapFactory.decodeByteArray(data,0,data.length,options);}int i=entries.size();e.x=24+(i%3)*266;e.y=24+(i/3)*210;entries.add(e);}
      }
      try(Cursor c=store.getReadableDatabase().rawQuery("SELECT origin,dest,zone,hits FROM routes WHERE zone!='visual' AND origin IN (SELECT id FROM screen_visuals) AND dest IN (SELECT id FROM screen_visuals)",null)){
        while(c.moveToNext()){Route r=new Route();r.from=c.getLong(0);r.to=c.getLong(1);r.zone=c.getString(2);r.hits=c.getInt(3);routes.add(r);}
      }
      main.post(()->{if(destroyed)return;map.entries=entries;map.routes=routes;status.setText(entries.isEmpty()?"Карта пока пуста. Включите перевод и откройте экраны игры.":"Экранов: "+entries.size()+" · переходов: "+routes.size()+"\nДва пальца — масштаб; перетаскивание — обзор; тап — открыть.");map.invalidate();});
    }catch(RuntimeException error){main.post(()->{if(!destroyed)status.setText("Не удалось открыть карту: "+error.getClass().getSimpleName());});}
  });}
  private void open(Entry e){io.execute(()->{
    Bitmap image=null;try(Cursor c=store.getReadableDatabase().rawQuery("SELECT jpeg FROM screen_visuals WHERE id=?",new String[]{""+e.id})){if(c.moveToFirst()&&!c.isNull(0)){byte[] b=c.getBlob(0);image=BitmapFactory.decodeByteArray(b,0,b.length);}}
    final Bitmap screenshot=image;main.post(()->{if(destroyed)return;
      ScrollView scroll=new ScrollView(this);LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(12),dp(12),dp(12),dp(12));body.setBackgroundColor(BG);scroll.addView(body);
      if(screenshot!=null)body.addView(new Preview(this,screenshot,e.labels),new LinearLayout.LayoutParams(-1,dp(240)));
      TextView text=new TextView(this);text.setTextColor(TEXT);text.setTextSize(15);text.setTextIsSelectable(true);StringBuilder out=new StringBuilder();
      if(screenshot==null)out.append("Для этого ранее сохранённого экрана пока нет снимка. Откройте его в игре с включённым переводом.\n\n");
      out.append("Язык: ").append(e.target.equals("uk")?"Українська":e.target.equals("ru")?"Русский":"—").append('\n');
      try{JSONArray labels=new JSONArray(e.labels);for(int i=0;i<labels.length();i++){JSONObject label=labels.getJSONObject(i);out.append("\n").append(i+1).append(". ").append(label.optString("source")).append("\n→ ").append(label.optString("translated").isEmpty()?"Ожидает перевода":label.optString("translated"));JSONArray q=label.optJSONArray("quad");if(q!=null&&q.length()==8){double dx=q.getDouble(2)-q.getDouble(0),dy=q.getDouble(3)-q.getDouble(1);double angle=screenshot==null?label.optDouble("angle"):Math.toDegrees(Math.atan2(dy*screenshot.getHeight(),dx*screenshot.getWidth()));out.append(String.format(Locale.ROOT,"\nНаклон: %.1f° · фон #%06X\n",angle,label.optInt("background")&0xffffff));}}}catch(JSONException error){out.append("\nДанные рамок недоступны");}
      text.setText(out);body.addView(text);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Экран "+e.id).setView(scroll).setPositiveButton("Закрыть",null).create();dialog.setOnDismissListener(d->{if(screenshot!=null)screenshot.recycle();});dialog.show();
    });
  });}
  protected void onResume(){super.onResume();com.example.arkrecruit.AppVisibility.visible=true;}
  protected void onPause(){com.example.arkrecruit.AppVisibility.visible=false;super.onPause();}
  protected void onDestroy(){destroyed=true;io.execute(()->store.close());io.shutdown();super.onDestroy();}
  private final class MapView extends View {
    ArrayList<Entry> entries=new ArrayList<>();ArrayList<Route> routes=new ArrayList<>();
    float scale=1,panX,panY,lastX,lastY,downX,downY;boolean moved;final Paint p=new Paint(3);final ScaleGestureDetector zoom;
    MapView(Context c){super(c);scale=getResources().getDisplayMetrics().density;zoom=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){float next=Math.max(.25f,Math.min(5f,scale*d.getScaleFactor())),factor=next/scale;panX=d.getFocusX()-(d.getFocusX()-panX)*factor;panY=d.getFocusY()-(d.getFocusY()-panY)*factor;scale=next;moved=true;invalidate();return true;}});}
    void reset(){scale=getResources().getDisplayMetrics().density;panX=panY=0;invalidate();}
    protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(BG);c.save();c.translate(panX,panY);c.scale(scale,scale);
      HashMap<Long,Entry> byId=new HashMap<>();for(Entry e:entries)byId.put(e.id,e);
      p.setColor(BLUE);p.setStrokeWidth(2);for(Route r:routes){Entry a=byId.get(r.from),b=byId.get(r.to);if(a==null||b==null)continue;float ax=a.x+120,ay=a.y+80,bx=b.x+120,by=b.y+80,dx=bx-ax,dy=by-ay;float trim=Math.max(Math.abs(dx)/126,Math.abs(dy)/86);if(trim>1){bx-=dx/trim;by-=dy/trim;}c.drawLine(ax,ay,bx,by,p);double angle=Math.atan2(dy,dx);Path arrow=new Path();arrow.moveTo(bx,by);arrow.lineTo(bx-(float)Math.cos(angle-.5)*13,by-(float)Math.sin(angle-.5)*13);arrow.lineTo(bx-(float)Math.cos(angle+.5)*13,by-(float)Math.sin(angle+.5)*13);arrow.close();c.drawPath(arrow,p);}
      for(Entry e:entries){p.setColor(CARD);c.drawRoundRect(e.x,e.y,e.x+240,e.y+170,12,12,p);p.setColor(TEXT);p.setTextSize(15);c.drawText("Экран "+e.id,e.x+10,e.y+23,p);if(e.thumb!=null)c.drawBitmap(e.thumb,null,new RectF(e.x+8,e.y+32,e.x+232,e.y+137),p);else{p.setTextSize(12);c.drawText("Снимок появится при переводе",e.x+10,e.y+85,p);}p.setTextSize(12);String label=e.anchors==null?"":e.anchors;if(label.length()>30)label=label.substring(0,30)+"…";c.drawText(label,e.x+10,e.y+158,p);}
      c.restore();
    }
    public boolean performClick(){super.performClick();return true;}
    public boolean onTouchEvent(android.view.MotionEvent e){zoom.onTouchEvent(e);switch(e.getActionMasked()){
      case MotionEvent.ACTION_DOWN:lastX=downX=e.getX();lastY=downY=e.getY();moved=false;return true;
      case MotionEvent.ACTION_POINTER_DOWN:moved=true;return true;
      case MotionEvent.ACTION_MOVE:if(!zoom.isInProgress()&&e.getPointerCount()==1){float dx=e.getX()-lastX,dy=e.getY()-lastY;if(Math.hypot(e.getX()-downX,e.getY()-downY)>dp(6))moved=true;panX+=dx;panY+=dy;invalidate();}lastX=e.getX();lastY=e.getY();return true;
      case MotionEvent.ACTION_POINTER_UP:moved=true;int index=e.getActionIndex()==0?1:0;if(index<e.getPointerCount()){lastX=e.getX(index);lastY=e.getY(index);}return true;
      case MotionEvent.ACTION_UP:if(!moved){performClick();float x=(e.getX()-panX)/scale,y=(e.getY()-panY)/scale;for(Entry entry:entries)if(x>=entry.x&&x<=entry.x+240&&y>=entry.y&&y<=entry.y+170){open(entry);break;}}return true;
      case MotionEvent.ACTION_CANCEL:moved=true;return true;
    }return true;}
  }
  static final class Preview extends View {
    final Bitmap bitmap;final String labels;final Paint p=new Paint(3);
    Preview(Context c,Bitmap bitmap,String labels){super(c);this.bitmap=bitmap;this.labels=labels;}
    protected void onDraw(Canvas c){super.onDraw(c);if(bitmap.isRecycled())return;float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight()),w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale,l=(getWidth()-w)/2,t=(getHeight()-h)/2;c.drawBitmap(bitmap,null,new RectF(l,t,l+w,t+h),p);
      try{JSONArray a=new JSONArray(labels);for(int i=0;i<a.length();i++){JSONArray q=a.getJSONObject(i).getJSONArray("quad");Path path=new Path();for(int j=0;j<8;j+=2){float x=l+(float)q.getDouble(j)*w,y=t+(float)q.getDouble(j+1)*h;if(j==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();p.setStyle(Paint.Style.STROKE);p.setColor(BLUE);p.setStrokeWidth(2);c.drawPath(path,p);p.setStyle(Paint.Style.FILL);p.setTextSize(16);c.drawText(""+(i+1),l+(float)q.getDouble(0)*w,t+(float)q.getDouble(1)*h-3,p);}}catch(JSONException ignored){}
    }
  }
}
