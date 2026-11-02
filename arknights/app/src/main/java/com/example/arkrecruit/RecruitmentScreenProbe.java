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
  public static boolean controlsPresent(Pixels pixels,Rect[] tiles,int width,int height){
    if(tiles.length!=5)return false;
    int right=0,top=height;for(Rect tile:tiles){right=Math.max(right,tile.right);top=Math.min(top,tile.top);}
    int w=tiles[0].width(),h=tiles[0].height();int blue=0,dark=0;
    for(float dx:new float[]{.4f,.7f,1.2f}){
      int x=Math.round(right+w*dx),y=Math.round(top+h*4.8f),y2=Math.round(top+h*6.15f);
      if(x>=width||y2>=height)return false;
      int a=pixels.at(x,y),c=pixels.at(x,y2);
      if(b(a)>130&&g(a)>85&&r(a)<120)blue++;
      if(light(c)<120)dark++;
    }
    return blue>=2&&dark>=2;
  }
  private static boolean covered(Rect area,Rect[] exclusions){
    for(Rect exclusion:exclusions)if(area.left<exclusion.right&&exclusion.left<area.right&&area.top<exclusion.bottom&&exclusion.top<area.bottom)return true;
    return false;
  }
  /** -1 means obscured by our own UI: retain prior state, never infer an exit. */
  public static int screenEvidence(Pixels pixels,Rect[] tiles,int width,int height,Rect[] exclusions){
    if(tiles.length!=5)return -1;
    int visible=0,observed=0;
    for(Rect tile:tiles){
      int gap=Math.max(2,tile.height()/12);
      Rect area=new Rect(tile.left-gap,tile.top-gap,tile.right+gap,tile.bottom+gap);
      if(covered(area,exclusions))continue;
      observed++;
      if(tilePresent(pixels,tile,width,height))visible++;
    }
    if(observed<2)return -1;
    return visible>=Math.min(4,observed)?1:0;
  }
  public static int controlEvidence(Pixels pixels,Rect[] tiles,int width,int height,Rect[] exclusions){
    if(tiles.length!=5)return -1;
    int right=0,top=height;
    for(Rect tile:tiles){right=Math.max(right,tile.right);top=Math.min(top,tile.top);}
    int w=tiles[0].width(),h=tiles[0].height();
    Rect area=new Rect(right,top+Math.round(h*4.3f),right+Math.round(w*1.5f),top+Math.round(h*6.7f));
    if(covered(area,exclusions))return -1;
    return controlsPresent(pixels,tiles,width,height)?1:0;
  }
  public static boolean present(Pixels pixels,Rect[] tiles,int width,int height){
    if(tiles.length!=5)return false;
    int visible=0;
    for(Rect tile:tiles)if(tilePresent(pixels,tile,width,height))visible++;
    return visible>=4;
  }
}
