package com.example.arkrecruit;

import android.graphics.Rect;

/** Sparse pixel checks, independent of OCR and of the selected button colour. */
public final class RecruitmentScreenProbe {
  private RecruitmentScreenProbe(){}
  public interface Pixels { int at(int x,int y); }
  private static int r(int c){return c>>16&255;}
  private static int g(int c){return c>>8&255;}
  private static int b(int c){return c&255;}
  private static int light(int c){return (r(c)*299+g(c)*587+b(c)*114)/1000;}
  private static int distance(int a,int c){
    return Math.max(Math.abs(r(a)-r(c)),Math.max(Math.abs(g(a)-g(c)),Math.abs(b(a)-b(c))));
  }
  public static boolean tilePresent(Pixels pixels,Rect tile,int width,int height){
    int gap=Math.max(2,tile.height()/12),ix=Math.max(3,tile.width()/10),iy=Math.max(3,tile.height()/5);
    if(tile.left-gap<0||tile.top-gap<0||tile.right+gap>=width||tile.bottom+gap>=height)return false;
    int[] xs={tile.left+ix,tile.right-ix},ys={tile.top+iy,tile.bottom-iy};
    int eligible=0,edges=0;
    for(int x:xs)for(int y:ys){
      int inside=pixels.at(x,y);
      // Own green fill over a white dialog is light; it is not a game button.
      if(light(inside)<185||(r(inside)>180&&g(inside)>140&&b(inside)<110))eligible++;
    }
    for(int x:xs){
      if(distance(pixels.at(x,tile.top+iy),pixels.at(x,tile.top-gap))>=45)edges++;
      if(distance(pixels.at(x,tile.bottom-iy),pixels.at(x,tile.bottom+gap))>=45)edges++;
    }
    for(int y:ys){
      if(distance(pixels.at(tile.left+ix,y),pixels.at(tile.left-gap,y))>=45)edges++;
      if(distance(pixels.at(tile.right-ix,y),pixels.at(tile.right+gap,y))>=45)edges++;
    }
    return eligible>=3&&edges>=6;
  }
  public static boolean present(Pixels pixels,Rect[] tiles,int width,int height){
    if(tiles.length!=5)return false;
    int visible=0;
    for(Rect tile:tiles)if(tilePresent(pixels,tile,width,height))visible++;
    return visible>=4;
  }
}
