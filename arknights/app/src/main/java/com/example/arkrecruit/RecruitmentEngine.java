package com.example.arkrecruit;

import java.util.*;

/** Deterministic recruitment matching. Does not invent probabilities. */
public final class RecruitmentEngine {
  private RecruitmentEngine() {}
  public static final String[] TAGS = {
    "Top Operator","Senior Operator","Robot","Starter","Melee","Ranged",
    "Caster","Defender","Guard","Medic","Sniper","Specialist","Supporter","Vanguard",
    "AoE","Crowd-Control","DPS","DP-Recovery","Debuff","Fast-Redeploy",
    "Defense","Elemental","Healing","Nuker","Shift","Slow","Summon","Support","Survival"
  };
  public static final class Operator {
    public final String name; public final int rarity; public final Set<String> tags;
    public Operator(String name,int rarity,Collection<String> tags) {
      this.name=name; this.rarity=rarity; this.tags=new HashSet<>(tags);
      if(rarity==6)this.tags.add("Top Operator");
      if(rarity==5)this.tags.add("Senior Operator");
      if(rarity==2)this.tags.add("Starter");
      if(rarity==1)this.tags.add("Robot");
    }
  }
  public static final class Result {
    public final List<String> tags; public final List<Operator> operators;
    public final int guaranteed, possible;
    Result(List<String> t,List<Operator> ops) {
      tags=t; operators=ops;
      int min=7,max=0;
      for(Operator x:ops) { min=Math.min(min,x.rarity); max=Math.max(max,x.rarity); }
      guaranteed=min;possible=max;
    }
    public String description() {
      StringBuilder b=new StringBuilder();
      b.append("Гарантия ").append(guaranteed).append("★; возможно до ").append(possible).append("★");
      for(Operator o:operators) b.append("\n").append(o.rarity).append("★ ").append(o.name);
      return b.toString();
    }
  }
  public static String normalize(String raw) {
    if(raw==null) return null;
    String cleaned=raw.toLowerCase(Locale.ROOT).replace('–','-').replace('—','-')
        .replaceAll("[^a-z0-9]+","").trim();
    if(cleaned.equals("aoe"))return "AoE";
    if(cleaned.equals("dps"))return "DPS";
    for(String t:TAGS) if(t.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","").equals(cleaned)) return t;
    if(cleaned.equals("topoperator")||cleaned.equals("topoperators")) return "Top Operator";
    if(cleaned.equals("senioroperator")||cleaned.equals("senioroperators")) return "Senior Operator";
    if(cleaned.equals("crowdcontrol"))return "Crowd-Control";
    if(cleaned.equals("dpregen")||cleaned.equals("dprecovery")) return "DP-Recovery";
    if(cleaned.equals("fastredeploy"))return "Fast-Redeploy";
    return null;
  }

  /** Conservative OCR-only repair for occasional dropped/misread letters.
   * Manual tag entry and matching continue to use strict normalize(). */
  public static String normalizeOcr(String raw) {
    String exact=normalize(raw);
    if(exact!=null)return exact;
    if(raw==null)return null;
    String value=raw.toLowerCase(Locale.ROOT).replace('–','-').replace('—','-')
      .replaceAll("[^a-z0-9]+","");
    if(value.equals("a0e"))return "AoE";
    if(value.length()<5)return null;
    int best=100,second=100;String answer=null;
    for(String tag:TAGS) {
      String candidate=tag.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
      int dist=distance(value,candidate);
      if(dist<best){second=best;best=dist;answer=tag;}
      else if(dist<second)second=dist;
    }
    // Require a unique nearest tag, with at most 1 edit for short tags.
    int max=value.length()>=11?2:1;
    return best<=max && second>best ?answer:null;
  }
  private static int distance(String a,String b) {
    int[] prev=new int[b.length()+1],cur=new int[b.length()+1];
    for(int j=0;j<=b.length();j++)prev[j]=j;
    for(int i=1;i<=a.length();i++){
      cur[0]=i;
      for(int j=1;j<=b.length();j++)
        cur[j]=Math.min(Math.min(prev[j]+1,cur[j-1]+1),
          prev[j-1]+(a.charAt(i-1)==b.charAt(j-1)?0:1));
      int[] tmp=prev;prev=cur;cur=tmp;
    }
    return prev[b.length()];
  }

  public static Result best(List<String> visible,List<Operator> operators,boolean chance,int hours) {
    if(visible==null||visible.isEmpty()||operators==null||operators.isEmpty())return null;
    LinkedHashSet<String> valid=new LinkedHashSet<>();
    for(String t:visible) {String n=normalize(t);if(n!=null)valid.add(n);}
    ArrayList<String> tags=new ArrayList<>(valid);if(tags.size()>5)tags=new ArrayList<>(tags.subList(0,5));
    Result best=null; long bestScore=Long.MIN_VALUE;
    for(int mask=1;mask<(1<<tags.size());mask++) {
      if(Integer.bitCount(mask)>3)continue;
      List<String> selected=new ArrayList<>();
      for(int k=0;k<tags.size();k++)if((mask&(1<<k))!=0) selected.add(tags.get(k));
      if(hours>=4&&selected.contains("Robot"))continue;
      if(hours>3&&hours<4&&selected.contains("Starter"))continue;
      ArrayList<Operator> matches=new ArrayList<>();
      for(Operator op:operators) {
        if(hours>=4&&op.rarity<=1)continue;
        if(hours>=8&&op.rarity<=2)continue;
        if(op.rarity==6&&!selected.contains("Top Operator"))continue;
        if(op.rarity==5&&selected.contains("Top Operator"))continue;
        if(selected.contains("Senior Operator")&&op.rarity!=5)continue;
        if(selected.contains("Top Operator")&&op.rarity!=6)continue;
        if(op.tags.containsAll(selected))matches.add(op);
      }
      if(matches.isEmpty())continue;
      matches.sort((a,b)->{int c=Integer.compare(b.rarity,a.rarity);return c!=0?c:a.name.compareToIgnoreCase(b.name);});
      Result r=new Result(selected,matches);
      // Guaranteed rarity wins by default. Chance mode first compares highest possible rarity.
      long score = chance ? r.possible*100000000L + r.guaranteed*1000000L :
                            r.guaranteed*100000000L + r.possible*1000000L;
      score += (10000L-Math.min(9999,matches.size()))*10L;
      score += selected.size();
      if(score>bestScore){bestScore=score;best=r;}
    }
    return best;
  }
}
