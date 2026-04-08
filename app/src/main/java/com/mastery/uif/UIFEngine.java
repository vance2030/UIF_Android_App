package com.mastery.uif;
import android.graphics.Bitmap;
import android.util.Log;

public class UIFEngine {
    private static final String TAG = "UIF_SDK";
    static {
        try {
            System.loadLibrary("uif_engine");
            Log.i(TAG, "[UIF SYSTEM] Universal SIMD C++ Engine Loaded Successfully.");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "[FATAL] Could not load UIF C++ Engine.", e);
        }
    }
    public UIFEngine() {}
    
    // Master Y's SIMD JNI Bindings
    public native boolean loadModel(String modelPath);
    public native void releaseEngine();
    public native int runInference(Bitmap input);
}
