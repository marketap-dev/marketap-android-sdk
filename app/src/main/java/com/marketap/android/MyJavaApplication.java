package com.marketap.android;

import android.app.Application;

import com.marketap.sdk.Marketap;

public class MyJavaApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Marketap.initialize(this, "MY_PROJECT_ID");
    }
}
