package com.mastery.uif;
import android.Manifest; import android.app.Activity; import android.content.pm.PackageManager;
import android.graphics.Bitmap; import android.graphics.Color; import android.graphics.SurfaceTexture;
import android.hardware.Camera; import android.os.Bundle;
import android.view.TextureView; import android.widget.Button; import android.widget.ImageView;
import android.widget.LinearLayout; import android.widget.TextView; import android.widget.FrameLayout;
import org.tensorflow.lite.Interpreter;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream;
import java.nio.ByteBuffer; import java.nio.ByteOrder;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private Camera camera; private TextureView textureView; private ImageView overlayView;
    private TextView statsText; private UIFEngine uifEngine; private Interpreter tfliteEngine;
    private boolean useUIF = true;
    private long lastTime = 0; private int frames = 0; private float totalLatency = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uifEngine = new UIFEngine();
        try {
            File tfliteModel = new File(copyAsset("mobilenet.tflite"));
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Give TFLite 4 threads to be fair
            tfliteEngine = new Interpreter(tfliteModel, options);
        } catch (Exception e) { e.printStackTrace(); }

        FrameLayout mainLayout = new FrameLayout(this);
        textureView = new TextureView(this); textureView.setSurfaceTextureListener(this);
        overlayView = new ImageView(this); overlayView.setScaleType(ImageView.ScaleType.FIT_XY);
        
        LinearLayout topPanel = new LinearLayout(this); topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setBackgroundColor(Color.parseColor("#BB000000"));
        
        statsText = new TextView(this); statsText.setTextColor(Color.WHITE); statsText.setTextSize(18f);
        statsText.setPadding(20, 20, 20, 20);
        
        LinearLayout btnPanel = new LinearLayout(this); btnPanel.setOrientation(LinearLayout.HORIZONTAL);
        Button btnTFLite = new Button(this); btnTFLite.setText("RUN TFLITE (Google)");
        Button btnUIF = new Button(this); btnUIF.setText("RUN UIF (Master Y)");
        
        btnTFLite.setOnClickListener(v -> { useUIF = false; resetStats(); });
        btnUIF.setOnClickListener(v -> { useUIF = true; resetStats(); });
        
        btnPanel.addView(btnTFLite); btnPanel.addView(btnUIF);
        topPanel.addView(btnPanel); topPanel.addView(statsText);
        
        mainLayout.addView(textureView); mainLayout.addView(overlayView); mainLayout.addView(topPanel);
        setContentView(mainLayout);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
    }

    private void resetStats() { frames = 0; totalLatency = 0; }

    private String copyAsset(String filename) {
        File f = new File(getCacheDir(), filename);
        if (!f.exists()) {
            try (InputStream is = getAssets().open(filename); FileOutputStream fos = new FileOutputStream(f)) {
                byte[] buffer = new byte[1024]; int read;
                while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
            } catch (Exception e) {}
        }
        return f.getAbsolutePath();
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
        try { camera = Camera.open(); camera.setPreviewTexture(surface); camera.startPreview(); startArena(); } catch (Exception e) {}
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { if(camera != null) { camera.stopPreview(); camera.release(); } return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    private void startArena() {
        new Thread(() -> {
            Bitmap outBmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
            int[] intValues = new int[224 * 224];
            ByteBuffer tfliteInput = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4); tfliteInput.order(ByteOrder.nativeOrder());
            ByteBuffer tfliteOutput = ByteBuffer.allocateDirect(1 * 1001 * 4); tfliteOutput.order(ByteOrder.nativeOrder());

            while(camera != null) {
                Bitmap inBmp = textureView.getBitmap(224, 224);
                if(inBmp != null) {
                    long start = System.nanoTime();
                    
                    if(useUIF) {
                        // [MASTER Y'S C++ ENGINE] - ZERO COPY MAGIC
                        uifEngine.processFrame(inBmp, outBmp);
                    } else {
                        // [GOOGLE'S TFLITE ENGINE] - FORCING IT TO DO ACTUAL WORK
                        inBmp.getPixels(intValues, 0, 224, 0, 0, 224, 224);
                        tfliteInput.rewind();
                        for (int i = 0; i < 224 * 224; ++i) {
                            int val = intValues[i];
                            tfliteInput.putFloat(((val >> 16) & 0xFF) / 255.0f);
                            tfliteInput.putFloat(((val >> 8) & 0xFF) / 255.0f);
                            tfliteInput.putFloat((val & 0xFF) / 255.0f);
                        }
                        if(tfliteEngine != null) tfliteEngine.run(tfliteInput, tfliteOutput);
                        outBmp.eraseColor(Color.RED); // Visually indicate TFLite is running
                    }
                    
                    long end = System.nanoTime();
                    float latencyMs = (end - start) / 1000000.0f;
                    totalLatency += latencyMs; frames++;
                    long now = System.currentTimeMillis();
                    
                    if(now - lastTime >= 1000) {
                        float avgLatency = totalLatency / frames;
                        String activeEngine = useUIF ? "[ UIF ENGINE (1-Bit) ]" : "[ TFLITE (Float-32) ]";
                        int textColor = useUIF ? Color.GREEN : Color.RED;
                        String status = activeEngine + "\nLatency: " + String.format("%.2f", avgLatency) + " ms\nFPS: " + frames;
                        
                        runOnUiThread(() -> { statsText.setTextColor(textColor); statsText.setText(status); overlayView.setImageBitmap(outBmp); });
                        frames = 0; totalLatency = 0; lastTime = now;
                    }
                }
            }
        }).start();
    }
}
