package com.example.arkrecruit;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
public class MainActivity extends Activity {
 @Override public void onCreate(Bundle b) { super.onCreate(b);
 LinearLayout l=new LinearLayout(this); l.setOrientation(1); l.setPadding(36,60,36,24); l.setBackgroundColor(Color.rgb(20,25,32));
 TextView title=new TextView(this); title.setText("Arknights Recruitment Assistant"); title.setTextSize(23); title.setTextColor(Color.WHITE); l.addView(title);
 TextView status=new TextView(this); status.setText("\nПрототип 0.1.0\n\nСервер: Global\nПриоритет: гарантия\n\nЗахват экрана пока не реализован."); status.setTextColor(Color.LTGRAY); status.setTextSize(17); l.addView(status);
 Button check=new Button(this); check.setText("Проверить запуск"); check.setOnClickListener(v->Toast.makeText(this,"Приложение запущено",Toast.LENGTH_LONG).show()); l.addView(check);
 setContentView(l); }
}
