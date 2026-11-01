package com.example.arkrecruit;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

/** User-initiated MediaProjection consent, without the single-app picker on Android 14+. */
public final class CapturePermissionActivity extends Activity {
  private static final int REQUEST_CAPTURE=1901;
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    if(state!=null)return;
    MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
    try {
      Intent request=Build.VERSION.SDK_INT>=34
        ?m.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        :m.createScreenCaptureIntent();
      startActivityForResult(request,REQUEST_CAPTURE);
    }catch(Exception e){
      Toast.makeText(this,"Не удалось запросить захват экрана",Toast.LENGTH_LONG).show();
      finish();
    }
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==REQUEST_CAPTURE){
      if(result==RESULT_OK&&data!=null){
        Intent svc=new Intent(this,ScreenService.class);
        svc.setAction("CAPTURE");
        svc.putExtra("captureCode",result);
        svc.putExtra("captureData",data);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(svc);else startService(svc);
      }else Toast.makeText(this,"Захват экрана не разрешён",Toast.LENGTH_SHORT).show();
    }
    finish();
  }
}