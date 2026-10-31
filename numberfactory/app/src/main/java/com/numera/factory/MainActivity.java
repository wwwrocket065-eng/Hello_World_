package com.numera.factory;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;
import android.view.View;

public final class MainActivity extends Activity {
    private FactoryView factoryView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xff08131f);
        applyImmersive();
        factoryView = new FactoryView(this);
        setContentView(factoryView);
    }

    private void applyImmersive() {
        // Full-screen immersive mode keeps system navigation away from the game toolbar.
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    @Override protected void onPause() {
        if (factoryView != null) factoryView.save();
        super.onPause();
    }
}
