package com.example.arkrecruit;
import java.util.LinkedHashMap;
import java.util.Map;
/** Stable, irregular best-candidate scatter with continuous independent light pulses. */
public final class HighlightPattern {
  public static final int COUNT=18;
  private HighlightPattern(){}
  private static final Map<Integer,float[][]> CACHE=new LinkedHashMap<Integer,float[][]>(){
    protected boolean removeEldestEntry(Map.Entry<Integer,float[][]> e){return size()>32;}
  };
  private static float random(int seed,int i,int salt){
    int x=seed^i*0x9E3779B9^salt;x^=x>>>16;x*=0x7feb352d;x^=x>>>15;x*=0x846ca68b;x^=x>>>16;
    return (x&0xFFFFFF)/16777215f;
  }
  private static synchronized float[][] points(int seed){
    float[][] points=CACHE.get(seed);if(points!=null)return points;
    points=new float[COUNT][2];
    for(int i=0;i<COUNT;i++){
      float best=-1;
      for(int candidate=0;candidate<48;candidate++){
        float x=.06f+.88f*random(seed,i*48+candidate,37);
        float y=.13f+.74f*random(seed,i*48+candidate,113);
        float nearest=Float.MAX_VALUE;
        for(int j=0;j<i;j++){
          float dx=(x-points[j][0])*3.2f,dy=y-points[j][1];
          nearest=Math.min(nearest,dx*dx+dy*dy);
        }
        if(nearest>best){best=nearest;points[i][0]=x;points[i][1]=y;}
      }
    }
    CACHE.put(seed,points);return points;
  }
  public static float x(int seed,int i){return points(seed)[i][0];}
  public static float y(int seed,int i){return points(seed)[i][1];}
  public static float size(int seed,int i){return 1.7f+1.6f*random(seed,i,419);}
  public static float pulse(long time,int seed,int i){
    double t=time/1000.0;
    // Incommensurate periods, phases and depths keep stars independent.
    double phase=random(seed,i,229)*Math.PI*2;
    double depth=.15+.10*random(seed,i,317);
    double slow=Math.sin(t*(.65+.75*random(seed,i,227))+phase);
    double detail=Math.sin(t*(1.35+.60*random(seed,i,331))+random(seed,i,337)*Math.PI*2);
    return (float)(.74+depth*slow+.055*detail);
  }
}
