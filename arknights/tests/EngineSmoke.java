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
  if(!"Supporter".equals(OperatorTagMapper.profession("SUPPORT"))||
      !"Support".equals(OperatorTagMapper.specialization("Support")))
    throw new AssertionError("Profession Supporter confused with Support specialization");
  List<RecruitmentEngine.Operator> regression=Arrays.asList(
    new RecruitmentEngine.Operator("Fang",3,Arrays.asList("Melee","Vanguard","DP-Recovery")),
    new RecruitmentEngine.Operator("Courier",4,Arrays.asList("Melee","Vanguard","DP-Recovery","Defense")),
    new RecruitmentEngine.Operator("Zima",5,Arrays.asList("Melee","Vanguard","DP-Recovery","Support")),
    new RecruitmentEngine.Operator("Orchid",3,Arrays.asList("Ranged","Supporter","Slow")),
    new RecruitmentEngine.Operator("Earthspirit",4,Arrays.asList("Ranged","Supporter","Slow"))
  );
  List<RecruitmentEngine.Result> variants=RecruitmentEngine.combinations(
    Arrays.asList("DP-Recovery","Supporter","Sniper","Medic","Starter"),regression,false,9);
  boolean baseline=false,three=false,four=false,five=false;
  for(RecruitmentEngine.Result choice:variants){
    if(choice.tags.contains("DP-Recovery")&&choice.tags.contains("Supporter"))
      throw new AssertionError("Impossible DP-Recovery + Supporter combination");
    if(choice.tags.isEmpty()){
      baseline=true;
      if(!"шанс неизвестен".equals(choice.chanceForRarity(5)))
        throw new AssertionError("Invented a mixed-pool probability");
    }
    for(RecruitmentEngine.Operator op:choice.operators){
      three|=op.rarity==3;four|=op.rarity==4;five|=op.rarity==5;
    }
  }
  if(!baseline||!three||!four||!five)throw new AssertionError("Missing lower-rarity alternatives");
  RecruitmentEngine.Result onlyFive=RecruitmentEngine.best(Arrays.asList("Support"),regression,false,9);
  if(onlyFive==null||!"100%*".equals(onlyFive.chanceForRarity(5))||
      !"0%*".equals(onlyFive.chanceForRarity(3)))
    throw new AssertionError("Conditional 0/100 probabilities incorrect");
  for(RecruitmentEngine.Result choice:RecruitmentEngine.combinations(Arrays.asList("Support"),regression,false,3))
    for(RecruitmentEngine.Operator op:choice.operators)if(op.rarity>=5)
      throw new AssertionError("Five-star allowed below four hours");
  System.out.println("Recruitment engine smoke tests PASS");
 }
}

