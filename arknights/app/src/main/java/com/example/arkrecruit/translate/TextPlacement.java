package com.example.arkrecruit.translate;
import android.graphics.*;
import java.util.*;
/** Quadrilateral text plane and background estimated inside its ink region, not above the label. */
public final class TextPlacement {
  public final float[] quad;public final float width,height,angle;public final RectF bounds;
  private TextPlacement(float[] q){quad=q;
    width=(distance(q,0,2)+distance(q,6,4))*.5f;height=(distance(q,0,6)+distance(q,2,4))*.5f;
    angle=(float)Math.toDegrees(Math.atan2(q[3]-q[1],q[2]-q[0]));
    float l=q[0],t=q[1],r=l,b=t;for(int i=2;i<8;i+=2){l=Math.min(l,q[i]);r=Math.max(r,q[i]);t=Math.min(t,q[i+1]);b=Math.max(b,q[i+1]);}bounds=new RectF(l,t,r,b);
  }
  private static float distance(float[] q,int a,int b){return (float)Math.hypot(q[a]-q[b],q[a+1]-q[b+1]);}
  public static TextPlacement from(Rect fallback,Point[] corners){
    float[] q={fallback.left,fallback.top,fallback.right,fallback.top,fallback.right,fallback.bottom,fallback.left,fallback.bottom};
    if(corners!=null&&corners.length==4){float[] candidate=new float[8];boolean valid=true;for(int i=0;i<4;i++){if(corners[i]==null){valid=false;break;}candidate[i*2]=corners[i].x;candidate[i*2+1]=corners[i].y;}
      if(valid&&distance(candidate,0,2)>2&&distance(candidate,0,6)>2)q=candidate;
    }
    // Tiny padding follows the plane, so it cannot expand toward a nearby decoration.
    float cx=0,cy=0;for(int i=0;i<8;i+=2){cx+=q[i]/4;cy+=q[i+1]/4;}
    float w=Math.max(1,distance(q,0,2)),h=Math.max(1,distance(q,0,6));
    for(int i=0;i<8;i+=2){q[i]=cx+(q[i]-cx)*(1+2/w);q[i+1]=cy+(q[i+1]-cy)*(1+2/h);}
    return new TextPlacement(q);
  }
  public float[] normalized(int w,int h){float[] out=quad.clone();for(int i=0;i<8;i+=2){out[i]/=w;out[i+1]/=h;}return out;}
  public int background(Bitmap frame){
    Map<Integer,List<Integer>> buckets=new HashMap<>();
    for(int row=0;row<7;row++)for(int col=0;col<13;col++){
      float u=.06f+.88f*col/12f,v=.08f+.84f*row/6f;
      float x=(1-v)*((1-u)*quad[0]+u*quad[2])+v*((1-u)*quad[6]+u*quad[4]);
      float y=(1-v)*((1-u)*quad[1]+u*quad[3])+v*((1-u)*quad[7]+u*quad[5]);
      int c=frame.getPixel(Math.max(0,Math.min(frame.getWidth()-1,Math.round(x))),Math.max(0,Math.min(frame.getHeight()-1,Math.round(y))));
      int key=(Color.red(c)>>4)<<8|(Color.green(c)>>4)<<4|(Color.blue(c)>>4);buckets.computeIfAbsent(key,k->new ArrayList<>()).add(c);
    }
    List<Integer> dominant=Collections.emptyList();for(List<Integer> bucket:buckets.values())if(bucket.size()>dominant.size())dominant=bucket;
    int[] r=new int[dominant.size()],g=r.clone(),b=r.clone();for(int i=0;i<r.length;i++){int c=dominant.get(i);r[i]=Color.red(c);g[i]=Color.green(c);b[i]=Color.blue(c);}Arrays.sort(r);Arrays.sort(g);Arrays.sort(b);return Color.rgb(r[r.length/2],g[g.length/2],b[b.length/2]);
  }
  /** Compensate the window's pass-through alpha so old glyphs do not shine through a flat mask. */
  public Bitmap mask(Bitmap source,int background,float alpha){
    int w=Math.max(1,Math.min(1600,Math.round(width))),h=Math.max(1,Math.min(900,Math.round(height)));
    float[] dst={0,0,w,0,w,h,0,h};Matrix projection=new Matrix();if(!projection.setPolyToPoly(dst,0,quad,0,4))return null;
    float[] sample=new float[2];int[] pixels=new int[w*h];
    for(int y=0;y<h;y++)for(int x=0;x<w;x++){
      sample[0]=x+.5f;sample[1]=y+.5f;projection.mapPoints(sample);int original=source.getPixel(Math.max(0,Math.min(source.getWidth()-1,(int)sample[0])),Math.max(0,Math.min(source.getHeight()-1,(int)sample[1])));
      pixels[y*w+x]=Color.rgb(compensate(Color.red(background),Color.red(original),alpha),compensate(Color.green(background),Color.green(original),alpha),compensate(Color.blue(background),Color.blue(original),alpha));
    }
    return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
  }
  public static int compensate(int bg,int source,float alpha){float floor=(1-alpha)*255,ceiling=alpha*255;float target=Math.max(floor,Math.min(ceiling,bg));return Math.max(0,Math.min(255,Math.round((target-(1-alpha)*source)/alpha)));}
}
