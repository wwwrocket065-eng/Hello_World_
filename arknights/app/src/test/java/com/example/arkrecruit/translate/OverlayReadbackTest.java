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
public class OverlayReadbackTest {
 @Test public void recoversSourceWithoutBlankingTheVisibleTranslation(){
  Bitmap original=Bitmap.createBitmap(300,120,Bitmap.Config.ARGB_8888);original.eraseColor(0xff444444);Paint glyph=new Paint();glyph.setColor(Color.WHITE);new Canvas(original).drawRect(40,40,240,50,glyph);
  TranslationOverlay layer=new TranslationOverlay();layer.items=java.util.Collections.singletonList(new TranslationOverlay.Item("Terminal","Терминал",new RectF(.05f,.1f,.95f,.9f),new RectF(0,0,1,1),0xff444444,Color.WHITE));
  for(int frame=0;frame<6;frame++){
   Bitmap overlay=Bitmap.createBitmap(300,120,Bitmap.Config.ARGB_8888);layer.draw(new Canvas(overlay),300,120,0,0);assertFalse(layer.hidden);assertEquals(1,layer.drawn);
   Bitmap capture=original.copy(Bitmap.Config.ARGB_8888,true);Paint alpha=new Paint();alpha.setAlpha(Math.round(255*TranslationOverlay.WINDOW_ALPHA));new Canvas(capture).drawBitmap(overlay,0,0,alpha);
   int[] colors=new int[300*120];capture.getPixels(colors,0,300,0,0,300,120);layer.readback.at(0).restore(colors,300,120);
   for(int y=25;y<95;y+=5)for(int x=30;x<270;x+=5)assertEquals(Color.red(original.getPixel(x,y)),Color.red(colors[y*300+x]),8);
   capture.recycle();overlay.recycle();
  }original.recycle();
 }
}
