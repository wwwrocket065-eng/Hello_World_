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
    if(good.size()<4||Math.hypot(a,b)<.97||Math.hypot(a,b)>1.03){a=1;b=0;}
    if(Math.abs(b)<.004f&&Math.abs(a-1)<.004f){a=1;b=0;}
    return new Pose(a,b,u-a*x+b*y,v-b*x-a*y,true);
  }
  private static float sub(float left,float center,float right){float d=left-2*center+right;return d>.01f?Math.max(-.5f,Math.min(.5f,(left-right)/(2*d))):0;}
  private static float cost(int[] a,int[] b,int w,int h,int x,int y,int dx,int dy){if(x+dx<2||y+dy<2||x+dx>=w-2||y+dy>=h-2)return 1000;int sum=0;for(int yy=-1;yy<=1;yy++)for(int xx=-1;xx<=1;xx++)sum+=Math.abs(a[(y+yy)*w+x+xx]-b[(y+yy+dy)*w+x+xx+dx]);return sum/9f;}
  /** Learn co-motion over several frames; apply one fitted transform to confirmed peers. */
  public static final class Groups {
    private final Map<String,Integer> votes=new HashMap<>();
    public static final class Sample {
      public final String id;public final float x,y;public final Pose pose;
      public Sample(String id,float x,float y,Pose pose){this.id=id;this.x=x;this.y=y;this.pose=pose;}
    }
    public void clear(){votes.clear();}
    private String key(String a,String b){return a.compareTo(b)<0?a+"\u0001"+b:b+"\u0001"+a;}
    public Map<String,Pose> resolve(List<Sample> items){
      int n=items.size();int[] parent=new int[n];for(int i=0;i<n;i++)parent[i]=i;
      Set<String> live=new HashSet<>();
      for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
        Sample a=items.get(i),b=items.get(j);String key=key(a.id,b.id);live.add(key);int score=votes.getOrDefault(key,0);
        if(a.pose.reliable&&b.pose.reliable){
          Pose fit=fit(Arrays.asList(a,b));
          boolean agrees=Math.hypot(a.x-b.x,a.y-b.y)>12&&error(a,fit)<.65f&&error(b,fit)<.65f;
          score=agrees?Math.min(6,score+1):0;votes.put(key,score);
        }
        if(score>=3){int x=root(parent,i),y=root(parent,j);parent[y]=x;}
      }
      votes.keySet().retainAll(live);Map<Integer,List<Sample>> clusters=new HashMap<>();
      for(int i=0;i<n;i++)clusters.computeIfAbsent(root(parent,i),k->new ArrayList<>()).add(items.get(i));
      Map<String,Pose> out=new HashMap<>();
      for(List<Sample> cluster:clusters.values()){
        List<Sample> reliable=new ArrayList<>();for(Sample s:cluster)if(s.pose.reliable)reliable.add(s);
        Pose common=reliable.size()>=2?fit(reliable):null;boolean consistent=common!=null;
        if(consistent)for(Sample s:reliable)if(error(s,common)>.85f)consistent=false;
        for(Sample s:cluster)out.put(s.id,consistent?common:s.pose);
      }return out;
    }
    private static int root(int[] p,int i){while(p[i]!=i){p[i]=p[p[i]];i=p[i];}return i;}
    // Include local axes as well as centers: different translations must not be
    // misclassified as a shared rotation just because two centers happen to fit.
    private static Pose fit(List<Sample> samples){
      double x=0,y=0,u=0,v=0;int count=0;
      for(Sample s:samples)for(int k=0;k<3;k++){float px=s.x+(k==1?12:0),py=s.y+(k==2?12:0);x+=px;y+=py;u+=s.pose.x(px,py);v+=s.pose.y(px,py);count++;}
      x/=count;y/=count;u/=count;v/=count;double den=0,aa=0,bb=0;
      for(Sample s:samples)for(int k=0;k<3;k++){double px=s.x+(k==1?12:0),py=s.y+(k==2?12:0),xx=px-x,yy=py-y,uu=s.pose.x((float)px,(float)py)-u,vv=s.pose.y((float)px,(float)py)-v;den+=xx*xx+yy*yy;aa+=xx*uu+yy*vv;bb+=xx*vv-yy*uu;}
      float a=den>1?(float)(aa/den):1,b=den>1?(float)(bb/den):0;
      return new Pose(a,b,(float)(u-a*x+b*y),(float)(v-b*x-a*y),true);
    }
    private static float error(Sample s,Pose p){float worst=0;for(int k=0;k<3;k++){float x=s.x+(k==1?12:0),y=s.y+(k==2?12:0);worst=Math.max(worst,(float)Math.hypot(s.pose.x(x,y)-p.x(x,y),s.pose.y(x,y)-p.y(x,y)));}return worst;}
  }

}
