package com.example.arkrecruit;

import android.graphics.*;

/** Enlarge each confirmed tile separately; OCR no longer scans unrelated UI. */
public final class TagOcrInput {
  private static final int W=460,H=144,PAD=18;
  public final Bitmap bitmap;
  private final Rect[] tiles;
  private TagOcrInput(Bitmap bitmap,Rect[] tiles){this.bitmap=bitmap;this.tiles=tiles;}
  public static TagOcrInput prepare(Bitmap frame,Rect[] confirmed){
    if(confirmed.length!=5)return new TagOcrInput(frame,new Rect[0]);
    Bitmap enhanced=TagContrastNormalizer.enhance(frame,confirmed);
    Bitmap strip=Bitmap.createBitmap(W,H*confirmed.length,Bitmap.Config.ARGB_8888);
    Canvas canvas=new Canvas(strip);canvas.drawColor(Color.WHITE);
    Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    for(int i=0;i<confirmed.length;i++){
      Rect source=confirmed[i];
      canvas.drawBitmap(enhanced,source,new Rect(PAD,i*H+PAD,W-PAD,(i+1)*H-PAD),paint);
    }
    if(enhanced!=frame)enhanced.recycle();
    return new TagOcrInput(strip,confirmed);
  }
  public Rect tileFor(Rect box){
    if(box==null||tiles.length==0)return null;
    int index=box.centerY()/H;
    return index>=0&&index<tiles.length?tiles[index]:null;
  }
  public Rect map(Rect box){
    Rect tile=tileFor(box);
    if(tile==null)return box;
    int index=box.centerY()/H;
    float sx=tile.width()/(float)(W-2*PAD),sy=tile.height()/(float)(H-2*PAD);
    return new Rect(Math.round(tile.left+(box.left-PAD)*sx),
      Math.round(tile.top+(box.top-index*H-PAD)*sy),
      Math.round(tile.left+(box.right-PAD)*sx),
      Math.round(tile.top+(box.bottom-index*H-PAD)*sy));
  }
}
