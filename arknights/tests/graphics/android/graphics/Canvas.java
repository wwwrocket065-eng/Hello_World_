package android.graphics;
public final class Canvas {
  private final Bitmap target;
  public Canvas(Bitmap target){this.target=target;}
  public void drawColor(int color){
    java.awt.Graphics2D g=target.image().createGraphics();
    g.setColor(new java.awt.Color(color,true));g.fillRect(0,0,target.getWidth(),target.getHeight());g.dispose();
  }
  public void drawBitmap(Bitmap source,Rect src,Rect dst,Paint paint){
    java.awt.Graphics2D g=target.image().createGraphics();
    g.drawImage(source.image(),dst.left,dst.top,dst.right,dst.bottom,src.left,src.top,src.right,src.bottom,null);g.dispose();
  }
}
