package com.mastery.uif;
import android.graphics.Bitmap;

/**
 * MASTER Y: UIF PURE INFERENCE SDK
 * Usage: 
 * 1. loadModel("/path/to/your/model.uif")
 * 2. int classId = runInference(cameraBitmap)
 * 3. releaseEngine() when done.
 */
public class UIFEngine {
    static { System.loadLibrary("uif_engine"); }
    public native boolean loadModel(String uifModelPath);
    public native void releaseEngine();
    public native int runInference(Bitmap inputFrame);
}
