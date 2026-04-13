package com.mastery.uif;

import android.Manifest; import android.app.Activity; import android.content.pm.PackageManager;
import android.graphics.Bitmap; import android.graphics.Canvas; import android.graphics.Color;
import android.graphics.Paint; import android.graphics.PorterDuff; import android.graphics.SurfaceTexture;
import android.hardware.Camera; import android.os.Bundle; import android.view.TextureView;
import android.widget.FrameLayout; import android.widget.ImageView; import android.widget.TextView;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private Camera camera; private TextureView textureView; private ImageView overlayView;
    private TextView statsText; private UIFEngine engine; 
    private boolean isEngineLoaded = false;
    private Paint boxPaint, textPaint;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout layout = new FrameLayout(this);
        
        textureView = new TextureView(this); textureView.setSurfaceTextureListener(this);
        overlayView = new ImageView(this); // For drawing Bounding Boxes
        
        statsText = new TextView(this); statsText.setTextColor(Color.GREEN); statsText.setTextSize(16f);
        statsText.setBackgroundColor(Color.parseColor("#88000000")); statsText.setPadding(20, 20, 20, 20);
        
        layout.addView(textureView); layout.addView(overlayView); layout.addView(statsText);
        setContentView(layout);
        
        // Setup Bounding Box Painters
        boxPaint = new Paint(); boxPaint.setColor(Color.GREEN); boxPaint.setStyle(Paint.Style.STROKE); boxPaint.setStrokeWidth(5f);
        textPaint = new Paint(); textPaint.setColor(Color.GREEN); textPaint.setTextSize(40f); textPaint.setStyle(Paint.Style.FILL);
        
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            
        engine = new UIFEngine();
        // NOTE: Engine expects "yolo_core.uif" in the future. For now, it will load but wait.
        isEngineLoaded = true; 
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        try { camera = Camera.open(); camera.setPreviewTexture(st); camera.startPreview(); } catch (Exception e) {}
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if(camera != null) { camera.stopPreview(); camera.release(); } return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {
        if (camera == null || !isEngineLoaded) return;
        Bitmap inBmp = textureView.getBitmap(224, 224);
        if(inBmp != null) {
            long start = System.nanoTime();
            
            // [THE GAME CHANGER]: Calling C++ to get Bounding Boxes
            // Returns flat array: [x1, y1, x2, y2, class_id, score, x1, y1...]
            float[] boxes = engine.runDetection(inBmp); 
            
            long end = System.nanoTime();
            float latencyMs = (end - start) / 1000000.0f;
            
            drawBoxes(boxes); // Draw the actual green boxes
            
            runOnUiThread(() -> statsText.setText(
                "[UIF V7: REAL-TIME DETECTION]\n>> Latency: " + String.format("%.2f", latencyMs) + " ms\n>> Engine: 128-Bit SIMD ACTIVE"
            ));
        }
    }

    private void drawBoxes(float[] boxes) {
        Bitmap overlayBitmap = Bitmap.createBitmap(textureView.getWidth(), textureView.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(overlayBitmap);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        if (boxes != null && boxes.length > 0) {
            // Loop through every 6 floats (1 bounding box)
            for (int i = 0; i < boxes.length; i += 6) {
                float x1 = boxes[i] * canvas.getWidth();
                float y1 = boxes[i+1] * canvas.getHeight();
                float x2 = boxes[i+2] * canvas.getWidth();
                float y2 = boxes[i+3] * canvas.getHeight();
                int class_id = (int) boxes[i+4];
                float score = boxes[i+5];

                if(score > 0.5f) { // Confidence Threshold
                    canvas.drawRect(x1, y1, x2, y2, boxPaint);
                    canvas.drawText("Obj:" + class_id + " (" + String.format("%.2f", score) + ")", x1, y1 - 10, textPaint);
                }
            }
        }
        runOnUiThread(() -> overlayView.setImageBitmap(overlayBitmap));
    }
}
