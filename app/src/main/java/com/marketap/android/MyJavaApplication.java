package com.marketap.android;

import android.app.Application;

import com.marketap.sdk.Marketap;
import com.marketap.sdk.model.external.UserProperty;

public class MyJavaApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Marketap.initialize(this, "MY_PROJECT_ID");
        Marketap.track("hi");
        Marketap.identify("wow",
                new UserProperty.Builder()
                        .set("name", "John Doe")
                        .set("email", "John.Doe@example.com")
                        .build()
        );
    }
}
