package com.example.arkrecruit.translate;
/** Small grayscale patch matching. Only confident shifts are accepted; never invent motion. */
public final class MotionTracker {
  public static final class Shift {public final int dx,dy;public final boolean confident;Shift(int x,int y,boolean c){dx=x;dy=y;confident=c;}}
  public static Shift estimate(int[] before,int[] after,int w,int h,boolean[] mask){
    if(before==null||before.length!=after.length)return new Shift(0,0,false);
    long zero=error(before,after,w,h,0,0,mask),best=zero;int bx=0,by=0;
    for(int dy=-20;dy<=20;dy+=2)for(int dx=-4;dx<=4;dx+=2){long e=error(before,after,w,h,dx,dy,mask);if(e<best){best=e;bx=dx;by=dy;}}
    boolean stationary=zero<7;
    return new Shift(bx,by,stationary||best<zero*.65&&best<22);
  }
  public static long error(int[] a,int[] b,int w,int h,int dx,int dy,boolean[] mask){
    long sum=0;int n=0;for(int y=24;y<h-24;y+=3)for(int x=8;x<w-8;x+=3){int i=y*w+x,j=(y+dy)*w+x+dx;if(mask!=null&&(mask[i]||mask[j]))continue;sum+=Math.abs(a[i]-b[j]);n++;}return n<30?255:sum/n;
  }
}
