package com.example.arkrecruit.translate;
import android.graphics.*;
import java.util.*;
/** Invert our own known pass-through compositing instead of blanking translations for OCR. */
public final class OverlayReadback {
  public static final class Patch {
    final int[] pixels;final int width,height;final Matrix inverse=new Matrix();final RectF bounds,clip;
    Patch(Bitmap bitmap,float[] quad,RectF clip){width=bitmap.getWidth();height=bitmap.getHeight();pixels=new int[width*height];bitmap.getPixels(pixels,0,width,0,0,width,height);this.clip=new RectF(clip);
      Matrix forward=new Matrix();forward.setPolyToPoly(new float[]{0,0,width,0,width,height,0,height},0,quad,0,4);forward.invert(inverse);
      float l=quad[0],r=l,t=quad[1],b=t;for(int i=2;i<8;i+=2){l=Math.min(l,quad[i]);r=Math.max(r,quad[i]);t=Math.min(t,quad[i+1]);b=Math.max(b,quad[i+1]);}bounds=new RectF(l,t,r,b);
    }
    Patch(int[] data,int w,int h,Matrix mapping,RectF bounds,RectF clip){pixels=data;width=w;height=h;inverse.set(mapping);this.bounds=bounds;this.clip=clip;}
    int sample(float x,float y,float[] point){if(!bounds.contains(x,y)||!clip.contains(x,y))return 0;point[0]=x;point[1]=y;inverse.mapPoints(point);int px=(int)Math.floor(point[0]),py=(int)Math.floor(point[1]);if(px<0||py<0||px>=width||py>=height)return 0;return pixels[py*width+px];}

  }
  public static final class Snapshot {
    public final long time;final List<Patch> patches;Snapshot(long t,List<Patch> p){time=t;patches=p;}
    public int restore(int captured,float x,float y,float[] work){for(int i=patches.size()-1;i>=0;i--){int own=patches.get(i).sample(x,y,work);if(own!=0)return recover(captured,own,TranslationOverlay.WINDOW_ALPHA);}return captured;}
    public void restore(int[] colors,int w,int h){boolean[] visited=new boolean[colors.length];float[] work=new float[2];for(int i=patches.size()-1;i>=0;i--){Patch p=patches.get(i);int left=Math.max(0,(int)(p.bounds.left*w)),right=Math.min(w,(int)Math.ceil(p.bounds.right*w)),top=Math.max(0,(int)(p.bounds.top*h)),bottom=Math.min(h,(int)Math.ceil(p.bounds.bottom*h));for(int y=top;y<bottom;y++)for(int x=left;x<right;x++){int k=y*w+x;if(visited[k])continue;int own=p.sample((x+.5f)/w,(y+.5f)/h,work);if(own!=0){colors[k]=recover(colors[k],own,TranslationOverlay.WINDOW_ALPHA);visited[k]=true;}}}}
  }
  private final ArrayDeque<Snapshot> history=new ArrayDeque<>();
  public synchronized void publish(List<Patch> patches){history.addLast(new Snapshot(System.nanoTime(),patches));while(history.size()>4)history.removeFirst();}
  public synchronized Snapshot at(long imageTime){Snapshot chosen=null;long now=System.nanoTime();boolean usable=Math.abs(now-imageTime)<2000000000L;for(Snapshot s:history)if(!usable||s.time<=imageTime-8000000L)chosen=s;if(chosen==null)chosen=history.peekFirst();return chosen==null?new Snapshot(0,Collections.emptyList()):chosen;}
  public static int recover(int captured,int overlay,float alpha){alpha*=Color.alpha(overlay)/255f;return Color.rgb(channel(Color.red(captured),Color.red(overlay),alpha),channel(Color.green(captured),Color.green(overlay),alpha),channel(Color.blue(captured),Color.blue(overlay),alpha));}
  private static int channel(int captured,int overlay,float a){return Math.max(0,Math.min(255,Math.round((captured-a*overlay)/(1-a))));}
}
