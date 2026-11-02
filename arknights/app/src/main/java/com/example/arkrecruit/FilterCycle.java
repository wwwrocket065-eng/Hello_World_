package com.example.arkrecruit;
/** Visual cycle: selected (2), current tags (1), all (0), preserving saved values. */
public final class FilterCycle {
  private FilterCycle(){}
  public static int next(int mode){return mode==2?1:mode==1?0:2;}
}
