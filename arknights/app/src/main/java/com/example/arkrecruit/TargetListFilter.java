package com.example.arkrecruit;
import java.util.*;
/** The chooser filters visibility, never selection or target plans. */
public final class TargetListFilter {
  private TargetListFilter(){}
  public static List<RecruitmentEngine.Operator> select(int mode,List<RecruitmentEngine.Operator> all,List<RecruitmentEngine.Operator> reachable,Collection<String> targets){
    if(mode==1)return new ArrayList<>(reachable);
    if(mode!=2)return new ArrayList<>(all);
    ArrayList<RecruitmentEngine.Operator> selected=new ArrayList<>();
    for(RecruitmentEngine.Operator op:all)if(targets.contains(op.name))selected.add(op);
    return selected;
  }
}
