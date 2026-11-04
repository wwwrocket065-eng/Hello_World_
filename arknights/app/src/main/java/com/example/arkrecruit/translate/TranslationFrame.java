package com.example.arkrecruit.translate;
import android.graphics.*;
/** Mutable OCR working frame; source capture and source pixel array are never modified. */
public final class TranslationFrame {
  private TranslationFrame(){}
  public static Bitmap create(int[] colors,int width,int height,Rect[] exclusions,int captureWidth,int captureHeight){
    // Bitmap.createBitmap(colors,...) is immutable and cannot back a Canvas.
    Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
    try{
      bitmap.setPixels(colors,0,width,0,0,width,height);
      Canvas canvas=new Canvas(bitmap);Paint mask=new Paint();mask.setColor(Color.BLACK);
      for(Rect r:exclusions)canvas.drawRect(r.left*width/(float)captureWidth,r.top*height/(float)captureHeight,
        r.right*width/(float)captureWidth,r.bottom*height/(float)captureHeight,mask);
      return bitmap;
    }catch(RuntimeException error){bitmap.recycle();throw error;}
  }
}
