package com.example.arkrecruit;

import android.graphics.Rect;
import java.util.*;

/** Reject partial frames and unrelated OCR boxes before replacing a snapshot. */
public final class TagGrid {
  private TagGrid(){}
  public static boolean isRecruitmentGrid(List<Rect> tiles){
    if(tiles.size()!=5)return false;
    ArrayList<Rect> sorted=new ArrayList<>(tiles);
    sorted.sort(Comparator.comparingInt(Rect::centerY));
    Rect first=sorted.get(0);
    int w=first.width(),h=first.height();
    if(w<=0||h<=0)return false;
    ArrayList<Integer> rows=new ArrayList<>(),cols=new ArrayList<>();
    for(Rect r:sorted){
      if(Math.abs(r.width()-w)>w*.22f||Math.abs(r.height()-h)>h*.25f)return false;
      addCluster(rows,r.centerY(),h*.35f);
      addCluster(cols,r.centerX(),w*.25f);
    }
    if(rows.size()!=2||cols.size()!=3)return false;
    Collections.sort(rows);Collections.sort(cols);
    int gap=rows.get(1)-rows.get(0);
    if(gap<h*1.1f||gap>h*2.5f)return false;
    int dx=cols.get(1)-cols.get(0),dx2=cols.get(2)-cols.get(1);
    if(dx<w*1.02f||dx>w*1.8f||Math.abs(dx-dx2)>w*.25f)return false;
    HashSet<String> occupied=new HashSet<>();
    int upper=0;
    for(Rect r:sorted){
      int row=Math.abs(r.centerY()-rows.get(0))<h*.35f?0:1;
      int col=0;
      for(int i=1;i<3;i++)if(Math.abs(r.centerX()-cols.get(i))<Math.abs(r.centerX()-cols.get(col)))col=i;
      if(!occupied.add(row+":"+col))return false;
      if(row==0)upper++;
    }
    return upper==3;
  }
  private static void addCluster(List<Integer> centers,int value,float tolerance){
    for(int center:centers)if(Math.abs(value-center)<tolerance)return;
    centers.add(value);
  }
}
