package com.example.arkrecruit;
import android.content.Context;
import android.view.Gravity;
import android.widget.*;
public final class ThemeControl {
 private ThemeControl(){}
 public static void add(LinearLayout host,Runnable changed){
  NavigationButton entry=new NavigationButton(host.getContext(),"Тема",false);host.addView(entry,new LinearLayout.LayoutParams(-1,-2));
  entry.setOnClickListener(v->{Context c=host.getContext();LinearLayout page=new LinearLayout(c);page.setOrientation(1);
   LinearLayout heading=new LinearLayout(c);heading.setGravity(Gravity.CENTER_VERTICAL);NavigationButton back=new NavigationButton(c,"",true);heading.addView(back,new LinearLayout.LayoutParams(UiStyle.dp(c,44),UiStyle.dp(c,44)));
   TextView title=new TextView(c);title.setText("Тема");title.setTextColor(UiStyle.TEXT);title.setTextSize(20);heading.addView(title);page.addView(heading);
   RadioGroup choices=new RadioGroup(c);page.addView(choices);RadioButton day=new RadioButton(c),night=new RadioButton(c);day.setId(android.view.View.generateViewId());night.setId(android.view.View.generateViewId());day.setText("Светлая");night.setText("Тёмная");day.setTextColor(UiStyle.TEXT);night.setTextColor(UiStyle.TEXT);choices.addView(day);choices.addView(night);choices.check(UiStyle.isLight()?day.getId():night.getId());
   choices.setOnCheckedChangeListener((g,id)->{UiStyle.theme(c,id==day.getId());if(changed!=null)changed.run();});
   SettingsPageTransition.Session session=SettingsPageTransition.open(host,page);Runnable restore=()->session.close(()->HighlightColorControl.clearBack(host));HighlightColorControl.registerBack(host,restore);back.setOnClickListener(b->restore.run());UiStyle.watch(host);
  });
 }
}
