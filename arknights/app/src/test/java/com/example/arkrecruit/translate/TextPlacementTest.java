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
public class TextPlacementTest {
  @Test public void samplesGreyUnderGlyphsInsteadOfOrangeStripeAndPreservesTilt(){
    Bitmap source=Bitmap.createBitmap(420,180,Bitmap.Config.ARGB_8888);source.eraseColor(0xff464646);Canvas c=new Canvas(source);Paint p=new Paint();p.setColor(0xffff6600);c.drawRect(0,0,420,45,p);
    Point[] corners={new Point(40,60),new Point(340,75),new Point(338,130),new Point(38,115)};
    TextPlacement placement=TextPlacement.from(new Rect(38,60,340,130),corners);
    assertEquals(2.86,placement.angle,.2);int bg=placement.background(source);assertTrue(Color.red(bg)<90);assertTrue(Math.abs(Color.red(bg)-Color.green(bg))<5);
    assertTrue(placement.bounds.top>50);source.recycle();
  }
  @Test public void compensatedMaskErasesOldWhiteLettersThroughPassThroughAlpha(){
    Bitmap source=Bitmap.createBitmap(240,80,Bitmap.Config.ARGB_8888);source.eraseColor(0xff464646);Paint p=new Paint();p.setColor(Color.WHITE);new Canvas(source).drawRect(45,20,190,60,p);
    TextPlacement placement=TextPlacement.from(new Rect(5,5,235,75),null);Bitmap mask=placement.mask(source,0xff464646,TranslationOverlay.WINDOW_ALPHA);
    Paint blend=new Paint();blend.setAlpha(Math.round(255*TranslationOverlay.WINDOW_ALPHA));Canvas canvas=new Canvas(source);canvas.drawBitmap(mask,null,placement.bounds,blend);
    assertEquals(Color.red(source.getPixel(25,40)),Color.red(source.getPixel(100,40)),3);mask.recycle();source.recycle();
  }
  @Test public void enlargedRotatedTranslationUsesSourceScaleAndDoesNotPaintAboveQuad(){
    Bitmap output=Bitmap.createBitmap(600,220,Bitmap.Config.ARGB_8888);output.eraseColor(Color.MAGENTA);
    TranslationOverlay layer=new TranslationOverlay();float[] q={.1f,.25f,.9f,.32f,.89f,.75f,.09f,.68f};
    layer.items=java.util.Collections.singletonList(new TranslationOverlay.Item("Terminal","Терминал",new RectF(.09f,.25f,.9f,.75f),new RectF(0,0,1,1),Color.BLACK,Color.WHITE,2,q,.43f,null));
    layer.draw(new Canvas(output),600,220,0,0);assertEquals(Color.MAGENTA,output.getPixel(280,30));assertEquals(1,layer.drawn);assertTrue("Large menu labels must not retain the old26px cap",layer.lastFontSize>35);output.recycle();
  }

  @Test public void denseWhiteInkDoesNotInvertDarkOrBlueButtonBackground(){
    for(int background:new int[]{0xff383838,0xff119cb8,0xffededed}){
      Bitmap source=Bitmap.createBitmap(260,100,Bitmap.Config.ARGB_8888);source.eraseColor(background);
      Paint ink=new Paint();ink.setColor(background==0xffededed?Color.BLACK:Color.WHITE);
      new Canvas(source).drawRect(32,32,228,68,ink);
      TextPlacement placement=TextPlacement.from(new Rect(30,30,230,70),null);
      assertEquals("Use button margin, not dense foreground glyphs",background,placement.background(source));source.recycle();
    }
  }
}
