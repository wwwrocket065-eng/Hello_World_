package com.example.arkrecruit;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

/** Use a direct OEM screen only when it is exported and accessible to this app. */
public final class OverlayPermissionNavigator {
  private OverlayPermissionNavigator(){}
  public static void open(Activity activity){
    Uri app=Uri.parse("package:"+activity.getPackageName());
    ComponentName direct=new ComponentName("com.android.settings",
      "com.android.settings.Settings$AppDrawOverlaySettingsActivity");
    try{
      ActivityInfo info=activity.getPackageManager().getActivityInfo(direct,0);
      boolean accessible=info.exported&&(info.permission==null||
        activity.checkSelfPermission(info.permission)==PackageManager.PERMISSION_GRANTED);
      if(accessible){
        activity.startActivity(new Intent("android.settings.MANAGE_APP_OVERLAY_PERMISSION",app)
          .setComponent(direct).putExtra("android.intent.extra.PACKAGE_NAME",activity.getPackageName()));
        android.util.Log.i("RecruitAssist","Overlay permission route: accessible app-specific settings");
        return;
      }
    }catch(Exception unavailable){android.util.Log.i("RecruitAssist","Direct overlay screen unavailable");}
    try{
      Toast.makeText(activity,"Выберите Arknights Recruitment Assistant",Toast.LENGTH_LONG).show();
      activity.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,app));
      android.util.Log.i("RecruitAssist","Overlay permission route: system overlay app list");
      return;
    }catch(Exception unavailable){android.util.Log.w("RecruitAssist","Overlay settings unavailable",unavailable);}
    try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,app));}
    catch(Exception unavailable){Toast.makeText(activity,"Не удалось открыть настройки разрешения",Toast.LENGTH_LONG).show();}
  }
}
