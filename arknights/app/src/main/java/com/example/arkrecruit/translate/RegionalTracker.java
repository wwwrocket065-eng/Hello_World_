package com.example.arkrecruit.translate;
import java.util.*;
/** Independent sparse patch flow per label. Static labels are probed slowly; moving ones every fresh frame. */
public final class RegionalTracker {
  public static final class Pose {public final float a,b,tx,ty;public final boolean reliable;Pose(float a,float b,float x,float y,boolean ok){this.a=a;this.b=b;tx=x;ty=y;reliable=ok;}public float x(float x,float y){return a*x-b*y+tx;}public float y(float x,float y){return b*x+a*y+ty;}}
  public static Pose track(int[] before,int[] after,int w,int h,float l,float t,float r,float bottom){
    if(before==null||before.length!=after.length)return new Pose(1,0,0,0,false);
    List<float[]> matched=new ArrayList<>();float minX=Math.max(4,l),maxX=Math.min(w-5,r),minY=Math.max(4,t),maxY=Math.min(h-5,bottom);
    if(maxX-minX<5||maxY-minY<5)return new Pose(1,0,0,0,false);
    for(int gy=0;gy<2;gy++)for(int gx=0;gx<3;gx++){
      int px=0,py=0,gradient=0;
      for(int y=(int)(minY+(maxY-minY)*gy/2);y<minY+(maxY-minY)*(gy+1)/2;y++)for(int x=(int)(minX+(maxX-minX)*gx/3);x<minX+(maxX-minX)*(gx+1)/3;x++){
        int i=y*w+x,g=Math.abs(before[i+1]-before[i-1])+Math.abs(before[i+w]-before[i-w]);if(g>gradient){gradient=g;px=x;py=y;}}
      if(gradient<25)continue;
      float zero=cost(before,after,w,h,px,py,0,0),best=zero;int dx=0,dy=0;
      for(int y=-6;y<=6;y++)for(int x=-6;x<=6;x++){float c=cost(before,after,w,h,px,py,x,y);if(c<best){best=c;dx=x;dy=y;}}
      int cx=dx,cy=dy;for(int y=cy-1;y<=cy+1;y++)for(int x=cx-1;x<=cx+1;x++){float c=cost(before,after,w,h,px,py,x,y);if(c<best){best=c;dx=x;dy=y;}}
      if(best>25||best>zero*.85f&&zero>5)continue;
      float fx=dx,fy=dy;
      if(zero>=3){fx+=sub(cost(before,after,w,h,px,py,dx-1,dy),best,cost(before,after,w,h,px,py,dx+1,dy));fy+=sub(cost(before,after,w,h,px,py,dx,dy-1),best,cost(before,after,w,h,px,py,dx,dy+1));}else{fx=fy=0;}
      matched.add(new float[]{px,py,fx,fy});
    }
    if(matched.size()<2)return new Pose(1,0,0,0,false);
    float[] dx=new float[matched.size()],dy=dx.clone();for(int i=0;i<dx.length;i++){dx[i]=matched.get(i)[2];dy[i]=matched.get(i)[3];}Arrays.sort(dx);Arrays.sort(dy);float mx=dx[dx.length/2],my=dy[dy.length/2];
    List<float[]> good=new ArrayList<>();for(float[] p:matched)if(Math.hypot(p[2]-mx,p[3]-my)<1.2)good.add(p);
    if(good.size()<2)return new Pose(1,0,0,0,false);
    float x=0,y=0,u=0,v=0;for(float[] p:good){x+=p[0];y+=p[1];u+=p[0]+p[2];v+=p[1]+p[3];}int n=good.size();x/=n;y/=n;u/=n;v/=n;
    float den=0,aa=0,bb=0;for(float[] p:good){float xx=p[0]-x,yy=p[1]-y,uu=p[0]+p[2]-u,vv=p[1]+p[3]-v;den+=xx*xx+yy*yy;aa+=xx*uu+yy*vv;bb+=xx*vv-yy*uu;}
    float a=den>1?aa/den:1,b=den>1?bb/den:0;
    if(good.size()<4||Math.abs(Math.atan2(b,a))<.004||Math.hypot(a,b)<.97||Math.hypot(a,b)>1.03){a=1;b=0;}
    return new Pose(a,b,u-a*x+b*y,v-b*x-a*y,true);
  }
  private static float sub(float left,float center,float right){float d=left-2*center+right;return d>.01f?Math.max(-.5f,Math.min(.5f,(left-right)/(2*d))):0;}
  private static float cost(int[] a,int[] b,int w,int h,int x,int y,int dx,int dy){if(x+dx<2||y+dy<2||x+dx>=w-2||y+dy>=h-2)return 1000;int sum=0;for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++)sum+=Math.abs(a[(y+yy)*w+x+xx]-b[(y+yy+dy)*w+x+xx+dx]);return sum/9f;}
}
