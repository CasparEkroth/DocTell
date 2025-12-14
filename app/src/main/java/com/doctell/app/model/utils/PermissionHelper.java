package com.doctell.app.model.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionHelper {

    public static final int REQ_POST_NOTIFICATIONS = 42;
    public static final int REQ_BLUETOOTH_CONNECT = 43;
    private static String TAG = "PermissionHelper";
    private PermissionHelper(){}
    public static boolean ensureNotificationPermission(Context ctx, Activity activity) {
        Log.d(TAG,"ensureNotificationPermission");

        if (Build.VERSION.SDK_INT < 33) return true;
        if (ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQ_POST_NOTIFICATIONS
        );
        return false;
    }

    public static void ensureBluetoothPermission(Context ctx, Activity activity) {
        Log.d(TAG,"ensureBluetoothPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.BLUETOOTH_CONNECT
        ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQ_BLUETOOTH_CONNECT
            );
        }
    }

    public static boolean cheekBluetoothPermission(Context ctx){
        Log.d(TAG,"cheekBluetoothPermission");
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
            return true;
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean cheekNotificationPermission(Context ctx){
        Log.d(TAG,"cheekNotificationPermission");
        if (Build.VERSION.SDK_INT < 33) return true;
        return  (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED);
    }
}
