package com.marketap.android;

import android.app.Application;

import com.marketap.sdk.Marketap;
import com.marketap.sdk.service.MarketapSDK;

public class MyJavaApplication extends Application {
    private static final MarketapSDK marketap = Marketap.getInstance();

    @Override
    public void onCreate() {
        super.onCreate();
        marketap.initialize(this, "MY_PROJECT_ID");
        marketap.track("hi");
    }
}
