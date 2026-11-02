package android.graphics;
public final class Color {
  public static final int WHITE=0xffffffff;
  public static int red(int c){return c>>16&255;}
  public static int green(int c){return c>>8&255;}
  public static int blue(int c){return c&255;}
}
