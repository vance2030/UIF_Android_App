package com.mastery.uif;
import android.graphics.Bitmap;
public class UIFEngine {
    static { System.loadLibrary("uif_engine"); }
    public native boolean loadModel(String modelPath);
    public native void releaseEngine();
    // NEW JNI CALL FOR OBJECT DETECTION
    public native float[] runDetection(Bitmap input); 
}
