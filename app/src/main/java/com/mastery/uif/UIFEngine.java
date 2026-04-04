package com.mastery.uif;

import android.graphics.Bitmap;
import android.util.Log;

public class UIFEngine {
    private static final String TAG = "UIF_SDK";

    static {
        try {
            System.loadLibrary("uif_engine");
            Log.i(TAG, "Master Y UIF Protocol: Native Layer Loaded Successfully.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "FATAL: Could not load UIF C++ Engine.", e);
        }
    }

    public UIFEngine() {}

    public native boolean initEngine(String modelPath);
    public native void processFrame(Bitmap input, Bitmap output);
}
