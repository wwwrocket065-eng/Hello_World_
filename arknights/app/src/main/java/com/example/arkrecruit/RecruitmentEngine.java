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
    public final int guaranteed, possible, hours;
    Result(List<String> t,List<Operator> ops,int hours) {
      this.hours=hours;
      tags=t; operators=ops;
      int min=7,max=0;
      for(Operator x:ops) { min=Math.min(min,x.rarity); max=Math.max(max,x.rarity); }
      guaranteed=min;possible=max;
    }
    public String chanceForRarity(int rarity){
      int count=0;
      for(Operator op:operators)if(op.rarity==rarity)count++;
      if(count==0)return "0%*";
      return count==operators.size()?"100%*":"шанс неизвестен";
    }
    public String raritySummary(){
      StringBuilder b=new StringBuilder();
      if(guaranteed<possible)b.append("Не ниже ").append(guaranteed).append("★ — 100%*\n");
      for(int rarity=hours>=8?3:hours>=4?2:1;rarity<=6;rarity++){
        if(rarity==6&&possible<6)continue;
        if(b.length()>0&&b.charAt(b.length()-1)!='\n')b.append("\n");
        b.append(rarity).append("★ — ").append(chanceForRarity(rarity));
      }
      b.append("\n* При ").append(hours).append(" ч и сохранении тегов");
      return b.toString();
    }
    public String description() {
      StringBuilder b=new StringBuilder(raritySummary());
      for(Operator o:operators) b.append("\n").append(o.rarity).append("★ ").append(o.name)
        .append(" — ").append(operators.size()==1?"100%*":"шанс неизвестен");
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
    List<Result> all=combinations(visible,operators,chance,hours);
    return all.isEmpty()?null:all.get(0);
  }
  /** All viable selections of zero to three tags, ranked by the same priority. */
  public static List<Result> combinations(List<String> visible,List<Operator> operators,boolean chance,int hours) {
    ArrayList<Result> results=new ArrayList<>();
    if(visible==null||operators==null||operators.isEmpty())return results;
    LinkedHashSet<String> valid=new LinkedHashSet<>();
    for(String t:visible) {String n=normalize(t);if(n!=null)valid.add(n);}
    ArrayList<String> tags=new ArrayList<>(valid);if(tags.size()>5)tags=new ArrayList<>(tags.subList(0,5));
    for(int mask=0;mask<(1<<tags.size());mask++) {
      if(Integer.bitCount(mask)>3)continue;
      List<String> selected=new ArrayList<>();
      for(int k=0;k<tags.size();k++)if((mask&(1<<k))!=0) selected.add(tags.get(k));
      if(hours>=4&&selected.contains("Robot"))continue;
      if(hours>=8&&selected.contains("Starter"))continue;
      ArrayList<Operator> matches=new ArrayList<>();
      for(Operator op:operators) {
        if(hours>=4&&op.rarity<=1)continue;
        if(hours<4&&op.rarity>=5)continue;
        if(hours>=8&&op.rarity<=2)continue;
        if(op.rarity==6&&!selected.contains("Top Operator"))continue;
        if(op.rarity==5&&selected.contains("Top Operator"))continue;
        if(selected.contains("Senior Operator")&&op.rarity!=5)continue;
        if(selected.contains("Top Operator")&&op.rarity!=6)continue;
        if(op.tags.containsAll(selected))matches.add(op);
      }
      if(matches.isEmpty())continue;
      matches.sort((a,b)->{int c=Integer.compare(b.rarity,a.rarity);return c!=0?c:a.name.compareToIgnoreCase(b.name);});
      Result r=new Result(selected,matches,hours);
      results.add(r);
    }
    results.sort((a,b)->Long.compare(score(b,chance),score(a,chance)));
    return results;
  }
  private static long score(Result r,boolean chance){
    long value=chance?r.possible*100000000L+r.guaranteed*1000000L:
      r.guaranteed*100000000L+r.possible*1000000L;
    return value+(10000L-Math.min(9999,r.operators.size()))*10L+r.tags.size();
  }
}
