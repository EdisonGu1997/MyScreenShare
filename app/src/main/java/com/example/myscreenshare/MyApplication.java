package com.example.myscreenshare;

import android.app.Application;
import android.content.Context;
import android.view.animation.CycleInterpolator;

public class MyApplication extends Application {
    private static Context sContext;

    @Override
    public void onCreate() {
        super.onCreate();
        sContext = getApplicationContext();
    }

    public static Context getContext() {
        return sContext;
    }
}
