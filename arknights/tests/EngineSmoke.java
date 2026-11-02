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
      if(choice.description().contains("%")||choice.description().contains("шанс неизвестен"))
        throw new AssertionError("Removed probability UI reintroduced");
    }
    for(RecruitmentEngine.Operator op:choice.operators){
      three|=op.rarity==3;four|=op.rarity==4;five|=op.rarity==5;
    }
  }
  if(!baseline||!three||!four||!five)throw new AssertionError("Missing lower-rarity alternatives");
  RecruitmentEngine.Result onlyFive=RecruitmentEngine.best(Arrays.asList("Support"),regression,false,9);
  if(onlyFive==null||onlyFive.guaranteed!=5||onlyFive.possible!=5)
    throw new AssertionError("Guaranteed rarity incorrect");
  for(RecruitmentEngine.Result choice:RecruitmentEngine.combinations(Arrays.asList("Support"),regression,false,3))
    for(RecruitmentEngine.Operator op:choice.operators)if(op.rarity>=5)
      throw new AssertionError("Five-star allowed below four hours");
  List<RecruitmentEngine.Operator> goalRoster=Arrays.asList(
    new RecruitmentEngine.Operator("Mayer",5,Arrays.asList("Ranged","Supporter","Summon")),
    new RecruitmentEngine.Operator("Beanstalk",4,Arrays.asList("Ranged","Vanguard","Summon","DP-Recovery")),
    new RecruitmentEngine.Operator("Kroos",3,Arrays.asList("Ranged","Sniper","DPS")),
    new RecruitmentEngine.Operator("Exusiai",6,Arrays.asList("Ranged","Sniper","DPS")),
    new RecruitmentEngine.Operator("Unreachable caster",5,Arrays.asList("Caster","DPS"))
  );
  List<RecruitmentEngine.Result> goals=RecruitmentEngine.combinations(
    Arrays.asList("Summon","Supporter","Sniper","DP-Recovery","Starter"),goalRoster,false,9);
  List<RecruitmentEngine.Operator> reachable=RecruitmentEngine.reachable(goals);
  Set<String> names=new HashSet<>();for(RecruitmentEngine.Operator op:reachable)names.add(op.name);
  if(reachable.size()!=3||!names.equals(new HashSet<>(Arrays.asList("Mayer","Beanstalk","Kroos"))))
    throw new AssertionError("List must union all tag choices, deduplicate and exclude untaggable/Top-only operators");
  if(goals.get(0).guaranteed!=5)throw new AssertionError("Automatic best recommendation changed");
  RecruitmentEngine.Result kroos=RecruitmentEngine.forTarget(goals,"Kroos");
  if(kroos==null||!kroos.tags.contains("Sniper")||kroos.guaranteed!=3)
    throw new AssertionError("Lower-rarity target is unavailable outside best combo");
  RecruitmentEngine.Result mayer=RecruitmentEngine.forTarget(goals,"Mayer");
  if(mayer==null||mayer.operators.size()!=1||!mayer.operators.get(0).name.equals("Mayer"))
    throw new AssertionError("Target plan failed to narrow operators");
  if(RecruitmentEngine.forTarget(goals,"Unreachable caster")!=null)
    throw new AssertionError("No-tags baseline advertised as a targeted plan");
  RecruitmentEngine.Result goalA=RecruitmentEngine.forTarget(goals,"Mayer"),goalB=RecruitmentEngine.forTarget(goals,"Kroos");
  if(goalA==null||goalB==null||goalA.tags.size()>3||goalB.tags.size()>3||
      !goalA.operators.stream().anyMatch(op->op.name.equals("Mayer"))||
      !goalB.operators.stream().anyMatch(op->op.name.equals("Kroos")))
    throw new AssertionError("Independent multi-target plans must each remain valid");
  List<RecruitmentEngine.Operator> timed=Arrays.asList(
    new RecruitmentEngine.Operator("Robot one",1,Arrays.asList("Robot","Healing")),
    new RecruitmentEngine.Operator("Starter two",2,Arrays.asList("Starter","Healing")),
    new RecruitmentEngine.Operator("Three",3,Arrays.asList("Healing")),
    new RecruitmentEngine.Operator("Five",5,Arrays.asList("Healing")));
  for(int mins:new int[]{60,230,240,450,460,540}){
    List<RecruitmentEngine.Result> choices=RecruitmentEngine.combinationsAtMinutes(Arrays.asList("Healing"),timed,false,mins);
    Set<Integer> rarities=new HashSet<>();for(RecruitmentEngine.Operator op:choices.get(0).operators)rarities.add(op.rarity);
    if(rarities.contains(1)!=(mins<240)||rarities.contains(2)!=(mins<460)||rarities.contains(5)!=(mins>=240))
      throw new AssertionError("Wrong rarity pool at minute "+mins);
  }
  System.out.println("Recruitment engine smoke tests PASS");
 }
}

