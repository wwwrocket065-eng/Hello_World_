package com.example.arkrecruit.translate;
import java.util.*;
/** Bounded local report. Keeps failures/returned text distinct from queued and displayed results. */
public final class TranslationTrace {
  private static final class Row {String source,state="распознано",output="",error="",geometry="";long start,finish;int attempts;}
  private final LinkedHashMap<String,Row> rows=new LinkedHashMap<>();
  private Row row(String source){Row r=rows.get(source);if(r==null){if(rows.size()>=96)rows.remove(rows.keySet().iterator().next());r=new Row();r.source=source;rows.put(source,r);}return r;}
  public synchronized void seen(String source,String geometry){row(source).geometry=geometry;}
  public synchronized void start(String source,long now){Row r=row(source);r.start=now;r.finish=0;r.attempts++;r.state="Qwen обрабатывает";r.error="";}
  public synchronized void finish(String source,String output,long now){Row r=row(source);r.output=output;r.finish=now;r.state=output.isEmpty()?"Qwen вернул пустой ответ":"ответ получен";}
  public synchronized void error(String source,String error,long now){Row r=row(source);r.error=error;r.finish=now;r.state="ошибка/прерывание";}
  public synchronized void skip(String source,String reason){Row r=row(source);r.state="пропущено";r.error=reason;}
  public synchronized void shown(String source,String value,boolean cached){Row r=row(source);r.output=value;r.state=cached?"готово из кэша/словаря":"готово к показу";}
  public synchronized String report(long now){StringBuilder out=new StringBuilder();for(Row r:rows.values()){
    out.append("\nOCR: ").append(r.source).append("\n  ").append(r.state);
    if(r.start>0)out.append(" · ").append(Math.max(0,(r.finish>0?r.finish:now)-r.start)/1000).append(" с · попыток ").append(r.attempts);
    if(!r.output.isEmpty())out.append("\n  Ответ: ").append(r.output);
    if(!r.error.isEmpty())out.append("\n  Причина: ").append(r.error);
    out.append("\n  ").append(r.geometry);
  }return out.toString();}
}
