package com.doctell.app;

import android.util.Log;

public class DocTellApp extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        Log.d("DocTellApp", "PDFBoxResourceLoader.init()");
    }
}
