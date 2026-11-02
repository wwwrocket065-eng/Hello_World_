package com.example.arkrecruit;

import java.util.Locale;

/** API profession codes and specialization tags are different namespaces. */
public final class OperatorTagMapper {
  private OperatorTagMapper(){}
  public static String profession(String code){
    if(code==null)return null;
    switch(code.toUpperCase(Locale.ROOT)){
      case "WARRIOR":return "Guard";
      case "TANK":return "Defender";
      case "PIONEER":return "Vanguard";
      case "SPECIAL":return "Specialist";
      case "SUPPORT":return "Supporter";
      case "SNIPER":return "Sniper";
      case "CASTER":return "Caster";
      case "MEDIC":return "Medic";
      default:return null;
    }
  }
  public static String specialization(String value){
    return RecruitmentEngine.normalize(value);
  }
}
