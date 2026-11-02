package com.example.arkrecruit;
/** Stratified coverage and continuous twinkling, independent of Android rendering. */
public final class HighlightPattern {
  public static final int COUNT=18;
  private HighlightPattern(){}
  private static float random(int seed,int i,int salt){
    int x=seed^i*0x9E3779B9^salt;x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;
    return (x&0xFFFFFF)/16777215f;
  }
  public static float x(int seed,int i){return .06f+((i%6+.5f)+(random(seed,i,37)-.5f)*.42f)/6f*.88f;}
  public static float y(int seed,int i){return .10f+((i/6+.5f)+(random(seed,i,113)-.5f)*.3f)/3f*.80f;}
  public static float pulse(long time,int seed,int i){return .72f+.28f*(.5f+.5f*(float)Math.sin(time*.00135+random(seed,i,229)*Math.PI*2));}
}
