package com.example.arkrecruit.translate;
import java.util.*;
/** Instant translations of stable menu labels; arbitrary new text still goes to Qwen. */
public final class InterfaceGlossary {
  private static final Map<String,String[]> LABELS=new HashMap<>();
  static{
    add("AUTO","Авто","Авто");add("OFF","Выкл.","Вимк.");add("SKIP","Пропустить","Пропустити");add("LOG","История","Історія");
    add("Terminal","Терминал","Термінал");add("Squads","Отряды","Загони");
    add("Operator","Операторы","Оператори");add("Operator Management","Управление операторами","Керування операторами");
    add("Store","Магазин","Крамниця");add("Recruitment","Набор","Набір");add("Recruit","Набор","Набір");
    add("Headhunt","Поиск персонала","Пошук персоналу");add("Missions","Задания","Завдання");add("Base","База","База");
    add("Depot","Склад","Склад");add("Friends","Друзья","Друзі");add("Archives","Архив","Архів");
    add("Current","Текущая операция","Поточна операція");add("All Stages Cleared","Все этапы пройдены","Усі етапи пройдено");
  }
  private static void add(String s,String ru,String uk){LABELS.put(s.toLowerCase(Locale.ROOT),new String[]{ru,uk});}
  public static String find(String source,String target){String[] pair=LABELS.get(TranslationPolicy.normalize(source).toLowerCase(Locale.ROOT));return pair==null?null:pair["uk".equals(target)?1:0];}
}
