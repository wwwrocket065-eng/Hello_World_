package com.example.arkrecruit.translate;
import java.util.*;
/** Admission is per visible source, not the changing OCR transcript of an entire screen. */
public final class TranslationVisibility {
  private Set<String> visible=Collections.emptySet();
  public synchronized void update(Collection<String> sources){visible=new HashSet<>(sources);}
  public synchronized boolean contains(String source){return visible.contains(source);}
  public synchronized void clear(){visible=Collections.emptySet();}
  public static boolean hiddenAfterOcr(boolean enabled,boolean paused,boolean cleanPending){return !enabled||paused||cleanPending;}
}
