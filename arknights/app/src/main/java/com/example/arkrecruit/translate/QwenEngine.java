package com.example.arkrecruit.translate;
import java.io.File;
/** Serial worker owns model/context; cancellation may be called from the UI thread. */
public final class QwenEngine implements AutoCloseable {
  static {System.loadLibrary("arkqwen");}
  private long handle;
  public void load(File file){if(handle==0){handle=open(file.getAbsolutePath());if(handle==0)throw new IllegalStateException("Не удалось загрузить Qwen. Проверьте модель и свободную память.");}}
  public String translate(String prompt){if(handle==0)throw new IllegalStateException("Модель не загружена");return generate(handle,prompt);}
  public void cancel(){cancelNative();}
  @Override public void close(){if(handle!=0){release(handle);handle=0;}}
  private static native long open(String path);
  private static native String generate(long h,String prompt);
  private static native void release(long h);
  private static native void cancelNative();
}
