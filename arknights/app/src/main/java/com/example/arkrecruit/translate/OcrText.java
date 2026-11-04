package com.example.arkrecruit.translate;
import android.graphics.*;
import com.google.mlkit.vision.text.Text;
import java.util.*;
/** OCR regions in original clean-frame coordinates, independent of a zoomed crop. */
final class OcrText {
  final String text;final Rect bounds;final Point[] corners;final float lineHeight;
  OcrText(String s,Rect b,Point[] p,float line){text=s;bounds=b;corners=p;lineHeight=line;}
  static List<OcrText> collect(Text result,float scale,int left,int top){
    List<OcrText> out=new ArrayList<>();
    for(Text.TextBlock b:result.getTextBlocks()){
      boolean paragraph=b.getText().length()>110&&b.getLines().size()>1;
      if(paragraph){float sum=0;for(Text.Line l:b.getLines())if(l.getBoundingBox()!=null)sum+=l.getBoundingBox().height()/scale;
        add(out,b.getText(),b.getBoundingBox(),b.getCornerPoints(),sum/Math.max(1,b.getLines().size()),scale,left,top);
      }else for(Text.Line l:b.getLines())add(out,l.getText(),l.getBoundingBox(),l.getCornerPoints(),l.getBoundingBox()==null?14:l.getBoundingBox().height()/scale,scale,left,top);
    }return out;
  }
  private static void add(List<OcrText> out,String s,Rect b,Point[] p,float line,float scale,int left,int top){
    if(b==null)return;Rect mapped=new Rect(Math.round(b.left/scale)+left,Math.round(b.top/scale)+top,Math.round(b.right/scale)+left,Math.round(b.bottom/scale)+top);
    Point[] points=null;if(p!=null){points=new Point[p.length];for(int i=0;i<p.length;i++)points[i]=new Point(Math.round(p[i].x/scale)+left,Math.round(p[i].y/scale)+top);}
    out.add(new OcrText(s,mapped,points,line));
  }
  static void merge(List<OcrText> all,List<OcrText> extra){
    for(OcrText n:extra){boolean duplicate=false;String key=TranslationPolicy.normalize(n.text).toLowerCase(Locale.ROOT);
      for(int i=0;i<all.size();i++){OcrText old=all.get(i);Rect intersection=new Rect(n.bounds);if(!intersection.intersect(old.bounds))continue;
        float overlap=intersection.width()*(float)intersection.height()/Math.max(1,Math.min(n.bounds.width()*(float)n.bounds.height(),old.bounds.width()*(float)old.bounds.height()));
        String oldKey=TranslationPolicy.normalize(old.text).toLowerCase(Locale.ROOT);
        if(overlap>.7f&&Math.abs(oldKey.length()-key.length())<=2&&distance(oldKey,key)<=Math.max(1,key.length()/10)){if(!oldKey.equals(key))all.set(i,n);duplicate=true;break;}
        if(overlap>.55f&&(TranslationPolicy.normalize(old.text).toLowerCase(Locale.ROOT).contains(key)||key.contains(TranslationPolicy.normalize(old.text).toLowerCase(Locale.ROOT)))){duplicate=true;break;}
      }if(!duplicate)all.add(n);
    }
  }
  private static int distance(String a,String b){int[] row=new int[b.length()+1];for(int j=0;j<row.length;j++)row[j]=j;for(int i=1;i<=a.length();i++){int diagonal=row[0];row[0]=i;for(int j=1;j<=b.length();j++){int above=row[j];row[j]=Math.min(Math.min(row[j]+1,row[j-1]+1),diagonal+(a.charAt(i-1)==b.charAt(j-1)?0:1));diagonal=above;}}return row[b.length()];}

}
