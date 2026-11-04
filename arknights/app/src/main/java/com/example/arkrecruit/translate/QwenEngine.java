package com.example.arkrecruit.translate;
import java.io.File;
/** Serial worker owns model/context; cancellation may be called from the UI thread. */
public final class QwenEngine implements AutoCloseable {
  static {System.loadLibrary("arkqwen");}
  private long handle;
  public void load(File file){if(handle==0){handle=open(file.getAbsolutePath());if(handle==0)throw new IllegalStateException("Не удалось загрузить Qwen. Проверьте модель и свободную память.");}}
  public String translate(String prompt){if(handle==0)throw new IllegalStateException("Модель не загружена");return translate(prompt,480);}
  public String translate(String prompt,int limit){if(handle==0)throw new IllegalStateException("Модель не загружена");return generate(handle,prompt,limit);}
  public String progress(){long[] p=progressNative();String[] phases={"ожидание","загрузка модели","обработка входа","генерация","готово","отменено","ошибка","превышено время"};return phases[(int)Math.max(0,Math.min(phases.length-1,p[0]))]+" · вход "+p[1]+"/"+p[2]+" ток. · ответ "+p[3]+" ток.";}
  private static native long[] progressNative();
  public void cancel(){cancelNative();}
  @Override public void close(){if(handle!=0){release(handle);handle=0;}}
  private static native long open(String path);
  private static native String generate(long h,String prompt,int limit);
  private static native void release(long h);
  private static native void cancelNative();
}
