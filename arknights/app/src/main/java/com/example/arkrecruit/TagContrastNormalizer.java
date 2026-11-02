package com.example.arkrecruit;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import java.util.Arrays;

/** Color-independent OCR preparation; original pixels remain available for edges. */
public final class TagContrastNormalizer {
  private TagContrastNormalizer(){}
  private static int light(int c){
    return (Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000;
  }
  public static Bitmap enhance(Bitmap original,Rect[] tiles){
    if(tiles.length==0)return original;
    Bitmap prepared=original.copy(Bitmap.Config.ARGB_8888,true);
    if(prepared==null)return original;
    for(Rect tile:tiles){
      int l=Math.max(0,tile.left+2),t=Math.max(0,tile.top+2);
      int r=Math.min(original.getWidth(),tile.right-2),b=Math.min(original.getHeight(),tile.bottom-2);
      if(r-l<10||b-t<8)continue;
      int dx=Math.max(2,(r-l)/12),dy=Math.max(2,(b-t)/6);
      int[] samples={light(original.getPixel(l+dx,t+dy)),light(original.getPixel(r-dx-1,t+dy)),
        light(original.getPixel(l+dx,b-dy-1)),light(original.getPixel(r-dx-1,b-dy-1))};
      Arrays.sort(samples);int background=(samples[1]+samples[2])/2;
      boolean dark=background<175;
      int w=r-l,h=b-t;int[] pixels=new int[w*h];
      original.getPixels(pixels,0,w,l,t,w,h);
      for(int i=0;i<pixels.length;i++){
        int y=light(pixels[i]);
        boolean ink=dark?y>background+30:y<background-30;
        pixels[i]=ink?0xff000000:0xffffffff;
      }
      prepared.setPixels(pixels,0,w,l,t,w,h);
    }
    return prepared;
  }
}
