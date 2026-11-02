package android.graphics;
import java.awt.image.BufferedImage;
public final class Bitmap {
  public enum Config { ARGB_8888 }
  public static Bitmap createBitmap(int w,int h,Config config){return new Bitmap(new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB));}
  public void recycle(){}
  public BufferedImage image(){return image;}
  private final BufferedImage image;
  public Bitmap copy(Config c,boolean mutable){
    BufferedImage next=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_ARGB);
    java.awt.Graphics2D g=next.createGraphics();g.drawImage(image,0,0,null);g.dispose();
    return new Bitmap(next);
  }
  public void getPixels(int[] dst,int off,int stride,int x,int y,int w,int h){image.getRGB(x,y,w,h,dst,off,stride);}
  public void setPixels(int[] src,int off,int stride,int x,int y,int w,int h){image.setRGB(x,y,w,h,src,off,stride);}

  public Bitmap(BufferedImage image){this.image=image;}
  public int getWidth(){return image.getWidth();}
  public int getHeight(){return image.getHeight();}
  public boolean isRecycled(){return false;}
  public int getPixel(int x,int y){return image.getRGB(x,y);}
}
