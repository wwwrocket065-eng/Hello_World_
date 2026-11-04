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
public class TranslationOverlayTest {
  @Test public void completedOcrRevealsTranslationOutsideRecruitment(){
    Bitmap image=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888);image.eraseColor(Color.MAGENTA);
    TranslationOverlay overlay=new TranslationOverlay();
    overlay.items=java.util.Collections.singletonList(new TranslationOverlay.Item("Terminal","Терминал",new RectF(.1f,.1f,.8f,.5f),new RectF(0,0,1,1),Color.BLACK,Color.WHITE));
    overlay.hidden=true;overlay.draw(new Canvas(image),400,200,0,0);assertEquals(Color.MAGENTA,image.getPixel(319,99));
    overlay.hidden=TranslationVisibility.hiddenAfterOcr(true,false,false);overlay.draw(new Canvas(image),400,200,0,0);
    assertEquals(Color.BLACK,image.getPixel(319,99));assertEquals(Color.MAGENTA,image.getPixel(390,190));
    int bright=0;for(int y=20;y<100;y++)for(int x=40;x<320;x++)if(image.getPixel(x,y)!=Color.BLACK)bright++;
    assertTrue("Russian glyphs must be drawn, not just the mask",bright>30);image.recycle();
  }
}
