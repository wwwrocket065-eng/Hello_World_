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
    add("On","Вкл.","Увімк.");
    add("Settings","Настройки","Налаштування");
    add("Reset Settings","Сбросить настройки","Скинути налаштування");
    add("Game","Игра","Гра");
    add("Volume","Звук","Звук");
    add("Notification","Уведомления","Сповіщення");
    add("Others","Прочее","Інше");
    add("User Center","Центр пользователя","Центр користувача");
    add("Game Settings","Настройки игры","Налаштування гри");
    add("Maintain 2x Speed","Сохранять скорость 2×","Зберігати швидкість 2×");
    add("When enabled, operations will be conducted at 2x speed","При включении операции проходят на скорости 2×","Увімкнені операції проходять на швидкості 2×");
    add("Auto-Select Dialogue Option","Автовыбор ответа в диалогах","Автовибір відповіді в діалогах");
    add("The first dialogue option will be automatically selected when long pressing to speed up story playback","При удержании для ускорения сюжета автоматически выбирается первый ответ","Під час утримання для прискорення сюжету автоматично вибирається перша відповідь");
    add("Dynamic Art-Related Settings","Настройки анимированных иллюстраций","Налаштування анімованих ілюстрацій");
    add("Only applies to Operators with dynamic art","Только для операторов с анимированными иллюстрациями","Лише для операторів з анімованими ілюстраціями");
    add("Home Screen Only","Только главный экран","Лише головний екран");
    add("Dynamic Art Entrance Animation","Анимация появления иллюстрации","Анімація появи ілюстрації");
    add("Entrance animations will no longer be played if turned off (except manual playback)","При отключении анимация появления доступна только при ручном запуске","Після вимкнення анімація появи доступна лише за ручного запуску");
    add("Assistant Entrance Animation","Анимация появления помощника","Анімація появи помічника");
    add("Assistant entrance animations will no longer be played if turned off","При отключении анимация появления помощника не воспроизводится","Після вимкнення анімація появи помічника не відтворюється");
    add("Every Login","При каждом входе","Під час кожного входу");
    add("Once per day","Раз в день","Раз на день");
    add("Outfit Rotation Frequency","Частота смены костюмов","Частота зміни костюмів");
    add("Per day","Каждый день","Щодня");
    add("Per visit","При каждом посещении","Під час кожного відвідування");
    add("Exclusive Event","Особое событие","Особлива подія");
    add("Breaking News","Новости","Новини");
    add("Limited Offer","Особое предложение","Особлива пропозиція");
    add("Pack","Набор","Набір");
    add("Confirm","Подтвердить","Підтвердити");
    add("Cancel","Отмена","Скасувати");
    add("Back","Назад","Назад");
    add("Skill","Навык","Навичка");
    add("Talent","Талант","Талант");
  }
  private static void add(String s,String ru,String uk){LABELS.put(key(s),new String[]{ru,uk});}
  private static String key(String source){return TranslationPolicy.normalize(source).toLowerCase(Locale.ROOT).replaceAll("^[*•\\s]+", "").replaceAll("[–—−]", "-").replaceAll("\\s*-\\s*", "-").replaceAll("[.!:]+$", "");}
  public static String find(String source,String target){String[] pair=LABELS.get(key(source));return pair==null?null:pair["uk".equals(target)?1:0];}
}
