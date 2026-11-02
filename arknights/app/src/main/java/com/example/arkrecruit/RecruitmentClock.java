package com.example.arkrecruit;
import android.graphics.Rect;
import java.util.regex.*;
public final class RecruitmentClock {
  private RecruitmentClock(){}
  private static final Pattern TIME=Pattern.compile("(?<![0-9])([0-9]{1,2})\\s*[:：]\\s*([0-9]{2})\\s*[:：]\\s*([0-9]{2})(?![0-9])");
  public static int parse(String raw){
    Matcher m=TIME.matcher(raw==null?"":raw);
    if(!m.find())return -1;
    int h=Integer.parseInt(m.group(1)),min=Integer.parseInt(m.group(2)),sec=Integer.parseInt(m.group(3));
    int value=h*60+min;
    return min<60&&sec==0&&value>=60&&value<=540&&value%10==0?value:-1;
  }
  public static Rect region(Rect[] tiles,int width,int height){
    if(tiles.length!=5)return null;
    int left=width,right=0,top=height;for(Rect r:tiles){left=Math.min(left,r.left);right=Math.max(right,r.right);top=Math.min(top,r.top);}
    int h=tiles[0].height(),w=tiles[0].width();
    Rect r=new Rect(Math.max(0,left+w/4),Math.max(0,Math.round(top-h*3.9f)),Math.min(width,right),Math.max(0,Math.round(top-h*1.8f)));
    return r.width()>0&&r.height()>0?r:null;
  }
}
