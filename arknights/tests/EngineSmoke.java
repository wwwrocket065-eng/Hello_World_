package com.example.arkrecruit;
import java.util.*;
public final class EngineSmoke {
 public static void main(String[] args){
  List<RecruitmentEngine.Operator> roster=Arrays.asList(
    new RecruitmentEngine.Operator("Kal'tsit",6,Arrays.asList("Ranged","Medic","Summon")),
    new RecruitmentEngine.Operator("Exusiai",6,Arrays.asList("Ranged","Sniper","DPS")),
    new RecruitmentEngine.Operator("Mayer",5,Arrays.asList("Ranged","Supporter","Summon")),
    new RecruitmentEngine.Operator("Beanstalk",4,Arrays.asList("Ranged","Vanguard","Summon")),
    new RecruitmentEngine.Operator("PhonoR-0",1,Arrays.asList("Ranged","Supporter","Elemental"))
  );
  RecruitmentEngine.Result result=RecruitmentEngine.best(
    Arrays.asList("Top Operator","Summon","Ranged","AoE","Elemental"),roster,false,9);
  if(result==null||result.guaranteed!=6||result.operators.size()!=1||
      !result.operators.get(0).name.equals("Kal'tsit"))
    throw new AssertionError("Top Operator matching failed");
  RecruitmentEngine.Result robot=RecruitmentEngine.best(
    Arrays.asList("Robot","Elemental"),roster,false,3);
  if(robot==null||robot.guaranteed!=1)throw new AssertionError("Robot short-time matching failed");
  if(RecruitmentEngine.normalize("DP - Recovery")==null)
    throw new AssertionError("Tag normalization failed");
  if(!"Top Operator".equals(RecruitmentEngine.normalizeOcr("Top Operat0r")))
    throw new AssertionError("Top Operator OCR correction failed");
  if(!"Ranged".equals(RecruitmentEngine.normalizeOcr("Rangcd")))
    throw new AssertionError("Ranged OCR correction failed");
  if(!"Summon".equals(RecruitmentEngine.normalizeOcr("Summ0n")))
    throw new AssertionError("Summon OCR correction failed");
  if(RecruitmentEngine.normalizeOcr("Something completely unrelated")!=null)
    throw new AssertionError("OCR false positive");
  System.out.println("Recruitment engine smoke tests PASS");
 }
}
