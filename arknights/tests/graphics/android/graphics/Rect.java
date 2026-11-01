package android.graphics;
public class Rect {
  public int left,top,right,bottom;
  public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
  public int width(){return right-left;}
  public int height(){return bottom-top;}
  public int centerX(){return (left+right)/2;}
  public int centerY(){return (top+bottom)/2;}
  public float exactCenterX(){return (left+right)*.5f;}
  public float exactCenterY(){return (top+bottom)*.5f;}
  public boolean isEmpty(){return width()<=0||height()<=0;}
  @Override public String toString(){return left+","+top+","+right+","+bottom;}
}
