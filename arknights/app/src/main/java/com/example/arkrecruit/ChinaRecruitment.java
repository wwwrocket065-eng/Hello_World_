package com.example.arkrecruit;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Validates the CN recruitment-specific pool and maps its tags to engine names. */
public final class ChinaRecruitment {
  private ChinaRecruitment(){}
  private static final String[] TAGS={"","Guard","Sniper","Defender","Medic","Supporter","Caster","Specialist","Vanguard","Melee","Ranged","Top Operator","Crowd-Control","Nuker","Senior Operator","Healing","Support","Starter","DP-Recovery","DPS","Survival","AoE","Defense","Slow","Debuff","Fast-Redeploy","Shift","Summon","Robot","Elemental"};
  public static JSONArray extract(JSONObject gacha,JSONObject chars,JSONArray bundled)throws Exception{
    String detail=gacha.getString("recruitDetail").replaceAll("<[^>]*>","").replace("\\n","\n");
    Set<String> names=new HashSet<>(),found=new HashSet<>();
    for(String section:detail.split("-{5,}")){
      Matcher match=Pattern.compile("★{1,6}\\s*\\n(.+)",Pattern.DOTALL).matcher(section);
      if(match.find())for(String name:match.group(1).split("/"))if(!name.trim().isEmpty())names.add(name.trim());
    }
    Map<String,String> translations=new HashMap<>(),english=new HashMap<>();
    JSONArray tagList=gacha.getJSONArray("gachaTags");
    for(int i=0;i<tagList.length();i++){
      JSONObject tag=tagList.getJSONObject(i);int id=tag.getInt("tagId");
      if(id>0&&id<TAGS.length)translations.put(tag.getString("tagName"),TAGS[id]);
    }
    for(int i=0;i<bundled.length();i++){JSONObject row=bundled.getJSONObject(i);english.put(row.getString("id"),row.getString("name"));}
    JSONArray result=new JSONArray();
    for(Iterator<String> it=chars.keys();it.hasNext();){
      String id=it.next();JSONObject data=chars.getJSONObject(id);
      if(!id.startsWith("char_")||!names.contains(data.optString("name")))continue;
      found.add(data.getString("name"));
      Object rarity=data.get("rarity");int stars=rarity instanceof Number?((Number)rarity).intValue()+1:Integer.parseInt(rarity.toString().replaceAll("[^0-9]",""));
      LinkedHashSet<String> tags=new LinkedHashSet<>();
      String position=RecruitmentEngine.normalize(data.getString("position"));
      String profession=OperatorTagMapper.profession(data.getString("profession"));
      if(position==null||profession==null)throw new IllegalArgumentException("Unknown CN position/profession");
      tags.add(position);tags.add(profession);
      JSONArray specials=data.getJSONArray("tagList");
      for(int j=0;j<specials.length();j++){
        String tag=translations.get(specials.getString(j));
        if(tag==null)throw new IllegalArgumentException("Unknown CN tag");tags.add(tag);
      }
      String name=english.containsKey(id)?english.get(id):data.optString("appellation",data.getString("name"));
      JSONObject row=new JSONObject();row.put("id",id);row.put("name",name);row.put("rarity",stars);row.put("tags",new JSONArray(tags));result.put(row);
    }
    if(names.size()<100||!found.equals(names)||result.length()!=names.size())throw new IllegalArgumentException("Unresolved/duplicate China recruitment entries");
    return result;
  }
}
