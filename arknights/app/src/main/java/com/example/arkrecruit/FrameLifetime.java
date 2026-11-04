package com.example.arkrecruit;
import java.util.concurrent.Executor;
/** Reader retirement shares the frame-consumer queue: no close races with an acquired frame. */
public final class FrameLifetime {
  private FrameLifetime(){}
  public static void retire(Executor owner,Runnable close){owner.execute(close);}
}
