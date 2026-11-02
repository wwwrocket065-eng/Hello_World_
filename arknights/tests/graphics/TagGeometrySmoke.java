package com.example.arkrecruit;
import android.graphics.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import java.util.*;
import javax.imageio.ImageIO;

public final class TagGeometrySmoke {
  private static void near(Rect got,Rect expected,int tolerance){
    if(got==null||Math.abs(got.left-expected.left)>tolerance||
      Math.abs(got.top-expected.top)>tolerance||Math.abs(got.right-expected.right)>tolerance||
      Math.abs(got.bottom-expected.bottom)>tolerance)
      throw new AssertionError("Button bounds "+got+" expected "+expected);
  }
  private static Rect detect(Bitmap image,Rect text){
    TagBoundsDetector.Detection hit=TagBoundsDetector.detect(image,text);
    return hit==null?null:hit.tile;
  }
  public static void main(String[] args)throws Exception{
    BufferedImage image=new BufferedImage(900,500,BufferedImage.TYPE_INT_RGB);
    Graphics2D g=image.createGraphics();
    g.setColor(new java.awt.Color(215,215,215));g.fillRect(0,0,900,500);
    ArrayList<Rect> grid=new ArrayList<>();
    for(int i=0;i<5;i++){
      int x=200+i%3*165,y=220+i/3*72;
      g.setColor(new java.awt.Color(48,48,48));g.fillRect(x,y,142,46);
      grid.add(new Rect(x,y,x+142,y+46));
    }
    // Self-captured partial green fill must not become the detected border.
    g.setColor(new java.awt.Color(53,95,59));g.fillRect(235,220,107,46);g.dispose();
    Bitmap bitmap=new Bitmap(image);
    near(detect(bitmap,new Rect(244,235,298,253)),grid.get(0),2);
    near(detect(bitmap,new Rect(381,307,490,325)),grid.get(4),2);
    if(!RecruitmentScreenProbe.present(bitmap::getPixel,grid.toArray(new Rect[0]),900,500))
      throw new AssertionError("Live five-button screen rejected");
    if(!TagGrid.isRecruitmentGrid(grid))throw new AssertionError("Valid five-tag grid rejected");
    if(TagGrid.isRecruitmentGrid(grid.subList(0,3)))throw new AssertionError("Partial frame accepted");
    ArrayList<Rect> overlay=new ArrayList<>(grid);
    overlay.set(4,new Rect(10,100,152,146));
    if(TagGrid.isRecruitmentGrid(overlay))throw new AssertionError("Own-panel text accepted as grid");
    overlay.set(4,grid.get(3));
    if(TagGrid.isRecruitmentGrid(overlay))throw new AssertionError("Duplicate tile accepted");
    // Optional private screenshot stays local; never uploaded to the repository.
    if(args.length>0){
      Bitmap shot=new Bitmap(ImageIO.read(new File(args[0])));
      int[][] labels={{554,370,604,389},{719,370,766,389},{871,370,944,389},
        {551,441,606,459},{690,441,796,459}};
      int[][] buttons={{507,354,650,401},{671,354,815,401},{835,354,979,401},
        {507,425,650,472},{671,425,815,472}};
      ArrayList<Rect> hits=new ArrayList<>();
      for(int i=0;i<5;i++){
        int[] l=labels[i],b=buttons[i];
        Rect hit=detect(shot,new Rect(l[0],l[1],l[2],l[3]));
        near(hit,new Rect(b[0],b[1],b[2],b[3]),3);hits.add(hit);
        System.out.println("Screenshot tile "+i+": "+hit);
      }
      if(!TagGrid.isRecruitmentGrid(hits))throw new AssertionError("Screenshot grid rejected");
      Rect[] physical=hits.toArray(new Rect[0]);
      if(!RecruitmentScreenProbe.controlsPresent(shot::getPixel,physical,shot.getWidth(),shot.getHeight()))throw new AssertionError("Bottom buttons not found");
      if(!RecruitmentScreenProbe.present(shot::getPixel,physical,shot.getWidth(),shot.getHeight()))
        throw new AssertionError("Recruitment screenshot rejected by fast gate");
      if(args.length>1){
        Bitmap modal=new Bitmap(ImageIO.read(new File(args[1])));
        if(RecruitmentScreenProbe.present(modal::getPixel,physical,modal.getWidth(),modal.getHeight()))
          throw new AssertionError("Expedited-plan dialog accepted as recruitment");
        if(RecruitmentScreenProbe.controlsPresent(modal::getPixel,physical,modal.getWidth(),modal.getHeight()))throw new AssertionError("Dialog accepted as original confirm/cancel panel");
        System.out.println("Dialog with stale green overlay correctly rejected");
      }
      if(args.length>2){
        Bitmap selected=new Bitmap(ImageIO.read(new File(args[2])));
        if(!RecruitmentScreenProbe.present(selected::getPixel,physical,selected.getWidth(),selected.getHeight()))
          throw new AssertionError("Selected Medic screenshot rejected by fast gate");
        System.out.println("Selected Medic screen correctly retained");
      }
      TagOcrInput strip=TagOcrInput.prepare(shot,physical);
      for(int i=0;i<5;i++){
        Rect mapped=strip.map(new Rect(18,i*144+18,442,(i+1)*144-18));
        near(mapped,physical[i],1);
      }

    }
    BufferedImage colors=new BufferedImage(180,160,BufferedImage.TYPE_INT_RGB);
    Graphics2D color=colors.createGraphics();
    Rect[] states={new Rect(10,10,150,55),new Rect(10,60,150,105),new Rect(10,110,150,155)};
    java.awt.Color[] backgrounds={new java.awt.Color(48,48,48),new java.awt.Color(0,155,210),new java.awt.Color(250,210,0)};
    for(int i=0;i<3;i++){
      Rect box=states[i];color.setColor(backgrounds[i]);color.fillRect(box.left,box.top,box.width(),box.height());
      color.setColor(i==2?java.awt.Color.BLACK:java.awt.Color.WHITE);
      color.fillRect(60,box.top+15,35,15);
    }
    color.dispose();Bitmap source=new Bitmap(colors);
    Bitmap normalized=TagContrastNormalizer.enhance(source,states);
    for(Rect box:states){
      if((normalized.getPixel(65,box.top+20)&0xffffff)!=0||
         (normalized.getPixel(30,box.top+20)&0xffffff)!=0xffffff)
        throw new AssertionError("Normal/selected/rare tag contrast differs");
    }
    if(source.getPixel(30,80)==normalized.getPixel(30,80))
      throw new AssertionError("Original capture modified or selected tag not normalized");
    if(RecruitmentClock.parse("07 : 40 : 00")!=460||RecruitmentClock.parse("01:00:00")!=60||
      RecruitmentClock.parse("09:00:00")!=540||RecruitmentClock.parse("09:10:00")!=-1||RecruitmentClock.parse("12:59:00")!=-1)
      throw new AssertionError("Recruitment time parsing");
    System.out.println("Tag geometry regression tests PASS");
  }
}
