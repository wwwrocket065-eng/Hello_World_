package com.numera.factory;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

public final class MainActivity extends Activity {
    private FactoryView factoryView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                             WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(0xff08131f);
        factoryView = new FactoryView(this);
        setContentView(factoryView);
    }

    @Override protected void onPause() {
        if (factoryView != null) factoryView.save();
        super.onPause();
    }
}
