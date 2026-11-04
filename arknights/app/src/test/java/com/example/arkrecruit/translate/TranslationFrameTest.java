package com.example.arkrecruit.translate;
import android.graphics.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TranslationFrameTest {
  @Test public void frameSupportsCanvasAndMasksOnlyScaledOwnWindows(){
    int[] source=new int[16];java.util.Arrays.fill(source,Color.WHITE);
    Bitmap frame=TranslationFrame.create(source,4,4,new Rect[]{new Rect(2,2,6,6)},8,8);
    try{
      assertTrue(frame.isMutable());
      assertEquals(Color.WHITE,frame.getPixel(0,0));assertEquals(Color.BLACK,frame.getPixel(1,1));assertEquals(Color.BLACK,frame.getPixel(2,2));assertEquals(Color.WHITE,frame.getPixel(3,3));
      for(int c:source)assertEquals(Color.WHITE,c);
      new Canvas(frame).drawColor(Color.RED);assertEquals(Color.RED,frame.getPixel(3,3));
    }finally{frame.recycle();}
  }
  @Test public void frameWithoutExclusionsRetainsSourcePixels(){
    int[] source={Color.RED,Color.GREEN,Color.BLUE,Color.WHITE};
    Bitmap frame=TranslationFrame.create(source,2,2,new Rect[0],2,2);
    try{int[] actual=new int[4];frame.getPixels(actual,0,2,0,0,2,2);assertArrayEquals(source,actual);assertTrue(frame.isMutable());}finally{frame.recycle();}
  }
}
