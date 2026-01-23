package com.doctell.app;

import android.util.Log;
import com.google.firebase.FirebaseApp;

public class DocTellApp extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplicationContext());
        Log.d("DocTellApp", "PDFBoxResourceLoader.init()");
    }
}
