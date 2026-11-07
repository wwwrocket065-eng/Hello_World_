package com.example.arkrecruit.translate;
import java.util.*;
/** Inverted anchor index: expected neighbors first, then matching anchors, not a full screen scan. */
final class ScreenMemory {
  private final TranslationStore store;
  private final Map<Long,Set<String>> templates=new LinkedHashMap<>();
  private final Map<String,Set<Long>> index=new HashMap<>();
  private long current;private String zone="visual";private long touchedAt;private Set<String> pending=Collections.emptySet();private long pendingAt;private int pendingFrame;
  ScreenMemory(TranslationStore s){store=s;for(Map.Entry<Long,String> e:s.screens().entrySet())add(e.getKey(),words(e.getValue()));}
  static Set<String> words(String s){Set<String> r=new HashSet<>();for(String w:s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))if(w.length()>2)r.add(w);return r;}
  private void add(long id,Set<String> words){if(templates.size()>=256){long oldest=templates.keySet().iterator().next();Set<String> removed=templates.remove(oldest);for(String w:removed){Set<Long> ids=index.get(w);if(ids!=null){ids.remove(oldest);if(ids.isEmpty())index.remove(w);}}}templates.put(id,words);for(String w:words)index.computeIfAbsent(w,k->new HashSet<>()).add(id);}
  void touch(float x,float y){zone=(int)(x*8)+":"+(int)(y*8);touchedAt=android.os.SystemClock.elapsedRealtime();}
  long identify(String anchorText,int frameId){
    Set<String> a=words(anchorText);if(a.size()<2)return 0;
    Set<Long> candidates=new LinkedHashSet<>(store.destinations(current,zone));if(current>0)candidates.add(current);
    for(String w:a){Set<Long> ids=index.get(w);if(ids!=null)candidates.addAll(ids);}
    long match=0;double best=.55;
    for(long id:candidates){Set<String> b=templates.get(id);if(b==null)continue;int same=0;for(String w:a)if(b.contains(w))same++;double score=same/(double)Math.max(a.size(),b.size());if(score>best){match=id;best=score;}}
    long now=android.os.SystemClock.elapsedRealtime();
    if(match!=current||current==0){
      int same=0;for(String w:a)if(pending.contains(w))same++;
      if(pending.isEmpty()||same/(double)Math.max(a.size(),pending.size())<.7){pending=a;pendingAt=now;pendingFrame=frameId;return 0;}
      if(now-pendingAt<500||pendingFrame==frameId)return 0;
    }
    pending=Collections.emptySet();
    if(match==0){match=store.addScreen(String.join(" ",a));add(match,a);}
    if(match!=current){if(now-touchedAt<4000&&!zone.equals("visual"))store.route(current,zone,match);current=match;zone="visual";}
    return match;
  }
}
