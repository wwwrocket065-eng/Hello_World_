package com.example.arkrecruit;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * Finds the actual painted button edges around a recognized recruitment label.
 *
 * ML Kit reports TEXT rectangles, which are not tile/button rectangles. The
 * native Arknights recruitment UI uses high-contrast, mostly rectangular tiles.
 * Search for paired left/right/top/bottom contrast transitions in the screenshot,
 * then reject weak, wrongly sized or asymmetric candidates.
 *
 * This is deliberately lightweight: only a few thousand pixels per label are
 * sampled from the already-authorized frame, without extra screenshot storage.
 */
public final class TagBoundsDetector {
  private TagBoundsDetector(){}

  public static final class Detection {
    public final Rect tile;
    public final float confidence;
    Detection(Rect tile,float confidence){this.tile=tile;this.confidence=confidence;}
  }

  private static final class Edge {
    final int position;
    final float score;
    Edge(int position,float score){this.position=position;this.score=score;}
  }

  private static int luminance(Bitmap bitmap,int x,int y) {
    x=Math.max(0,Math.min(bitmap.getWidth()-1,x));
    y=Math.max(0,Math.min(bitmap.getHeight()-1,y));
    int c=bitmap.getPixel(x,y);
    return (Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000;
  }

  private static float horizontalTransition(Bitmap image,int x,int cy,int fontHeight) {
    float total=0;
    float[] offsets={-.85f,-.70f,.70f,.85f};
    int delta=Math.max(2,Math.round(fontHeight*.15f));
    for(float offset:offsets) {
      int y=cy+Math.round(fontHeight*offset);
      total+=luminance(image,x+delta,y)-luminance(image,x-delta,y);
    }
    return total/offsets.length;
  }

  private static float verticalTransition(Bitmap image,int y,int cx,int fontHeight) {
    float total=0;
    float[] offsets={-1f,-.52f,.52f,1f};
    int delta=Math.max(2,Math.round(fontHeight*.15f));
    for(float offset:offsets) {
      int x=cx+Math.round(fontHeight*offset);
      total+=luminance(image,x,y+delta)-luminance(image,x,y-delta);
    }
    return total/offsets.length;
  }

  private static Edge horizontalEdge(Bitmap image,Rect text,int side,int polarity) {
    int h=Math.max(8,text.height());
    int cx=text.centerX(),cy=text.centerY();
    int min=Math.max(Math.round(h*.95f),text.width()/2+1);
    int max=Math.max(Math.round(h*6.5f),text.width()/2+2*h);
    Edge best=new Edge(cx,Float.NEGATIVE_INFINITY);
    for(int distance=min;distance<=max;distance++) {
      int x=cx+side*distance;
      if(x<3||x>=image.getWidth()-3)continue;
      float score=horizontalTransition(image,x,cy,h)*side*polarity;
      if(score>=45){
        // Take the nearest sustained border, not a stronger neighbouring tile.
        int end=distance;
        while(end<max&&horizontalTransition(image,cx+side*(end+1),cy,h)*side*polarity>=45)end++;
        return new Edge(cx+side*((distance+end)/2),score);
      }
    }
    return best;
  }

  private static Edge verticalEdge(Bitmap image,Rect text,int side,int polarity) {
    int h=Math.max(8,text.height());
    int cx=text.centerX(),cy=text.centerY();
    Edge best=new Edge(cy,Float.NEGATIVE_INFINITY);
    for(int distance=Math.max(3,Math.round(h*.70f));
        distance<=Math.round(h*2.0f);distance++) {
      int y=cy+side*distance;
      if(y<3||y>=image.getHeight()-3)continue;
      float score=verticalTransition(image,y,cx,h)*side*polarity;
      if(score>=45){
        int end=distance;
        while(end<Math.round(h*2.0f)&&verticalTransition(image,cy+side*(end+1),cx,h)*side*polarity>=45)end++;
        return new Edge(cy+side*((distance+end)/2),score);
      }
    }
    return best;
  }

  /** Returns null when button edges are invisible/obscured by another overlay. */
  public static Detection detect(Bitmap image,Rect text) {
    if(image==null||image.isRecycled()||text==null||text.isEmpty())return null;
    int h=Math.max(8,text.height());
    Detection best=null;
    for(int polarity:new int[]{1,-1}) {
      Edge left=horizontalEdge(image,text,-1,polarity);
      Edge right=horizontalEdge(image,text,1,polarity);
      if(left.score<26||right.score<26)continue;
      int width=right.position-left.position;
      if(width<h*3.1f||width>h*12.0f)continue;
      if(Math.abs((left.position+right.position)/2f-text.exactCenterX())>h*.65f)continue;

      Edge top=verticalEdge(image,text,-1,polarity);
      Edge bottom=verticalEdge(image,text,1,polarity);
      if(top.score<22||bottom.score<22)continue;
      int height=bottom.position-top.position;
      if(height<h*1.4f||height>h*3.6f)continue;
      if(Math.abs((top.position+bottom.position)/2f-text.exactCenterY())>h*.5f)continue;
      if(left.position>text.left||right.position<text.right||
          top.position>text.top||bottom.position<text.bottom)continue;
      // Require plausible wide, squat recruitment buttons, not panels or text boxes.
      float aspect=width/(float)height;
      if(aspect<1.9f||aspect>6.5f)continue;
      float confidence=Math.min(Math.min(left.score,right.score),
                                Math.min(top.score,bottom.score));
      if(best==null||confidence>best.confidence) {
        best=new Detection(new Rect(left.position,top.position,
                                    right.position,bottom.position),confidence);
      }
    }
    return best;
  }

  public static Rect estimate(Rect text,int width,int height) {
    int cx=text.centerX(),cy=text.centerY();
    return new Rect(cx-width/2,cy-height/2,cx+(width+1)/2,cy+(height+1)/2);
  }
}

