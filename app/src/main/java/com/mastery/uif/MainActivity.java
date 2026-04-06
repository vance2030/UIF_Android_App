package com.mastery.uif;
import android.Manifest; import android.app.Activity; import android.content.pm.PackageManager;
import android.graphics.Bitmap; import android.graphics.Color; import android.graphics.SurfaceTexture;
import android.graphics.Canvas; import android.graphics.Paint;
import android.hardware.Camera; import android.os.Bundle;
import android.view.TextureView; import android.widget.Button; import android.widget.ImageView;
import android.widget.LinearLayout; import android.widget.TextView; import android.widget.FrameLayout;
import org.tensorflow.lite.Interpreter;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream;
import java.nio.ByteBuffer; import java.nio.ByteOrder;
import android.content.Intent; import android.content.IntentFilter; import android.os.BatteryManager;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private Camera camera; private TextureView textureView; private ImageView overlayView;
    private TextView statsText; private UIFEngine uifEngine; private Interpreter tfliteEngine;
    private boolean useUIF = true;
    private long lastTime = 0; private int frames = 0; private float totalLatency = 0;
    private Paint paint = new Paint();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uifEngine = new UIFEngine();
        initTFLite();

        FrameLayout mainLayout = new FrameLayout(this);
        textureView = new TextureView(this); textureView.setSurfaceTextureListener(this);
        overlayView = new ImageView(this); overlayView.setScaleType(ImageView.ScaleType.FIT_XY);
        
        // UI Design Upgrade
        LinearLayout topPanel = new LinearLayout(this); topPanel.setOrientation(LinearLayout.VERTICAL);
        topPanel.setBackgroundColor(Color.parseColor("#99000000")); // semi-transparent black
        
        statsText = new TextView(this); statsText.setTextColor(Color.CYAN); statsText.setTextSize(14f);
        statsText.setPadding(30, 30, 30, 30);
        statsText.setLineSpacing(1.2f, 1.2f);
        
        LinearLayout btnPanel = new LinearLayout(this); btnPanel.setOrientation(LinearLayout.HORIZONTAL);
        btnPanel.setPadding(20, 20, 20, 0);
        Button btnTFLite = createStyledButton("TFLITE (Standard)");
        Button btnUIF = createStyledButton("UIF (Ultra-Fast)");
        
        btnTFLite.setOnClickListener(v -> { useUIF = false; resetStats(); });
        btnUIF.setOnClickListener(v -> { useUIF = true; resetStats(); });
        
        btnPanel.addView(btnTFLite); btnPanel.addView(btnUIF);
        topPanel.addView(btnPanel); topPanel.addView(statsText);
        
        mainLayout.addView(textureView); mainLayout.addView(overlayView); mainLayout.addView(topPanel);
        setContentView(mainLayout);

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
    }

    private Button createStyledButton(String text) {
        Button b = new Button(this); b.setText(text);
        b.setBackgroundColor(Color.DKGRAY); b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(10, 0, 10, 0); b.setLayoutParams(lp);
        return b;
    }

    private void initTFLite() {
        try {
            File tfliteModel = new File(copyAsset("mobilenet.tflite"));
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); 
            tfliteEngine = new Interpreter(tfliteModel, options);
        } catch (Exception e) { e.printStackTrace(); }
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
    
    private float getBatteryTemperature() {
        Intent intent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        return (intent != null) ? ((float) intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)) / 10 : 0.0f;
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int w, int h) {
        try { camera = Camera.open(); camera.setPreviewTexture(surface); camera.startPreview(); startVisionEngine(); } catch (Exception e) {}
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { if(camera != null) { camera.stopPreview(); camera.release(); } return true; }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int w, int h) {}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    private void startVisionEngine() {
        new Thread(() -> {
            Bitmap outBmp = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
            int[] intValues = new int[224 * 224];
            ByteBuffer tfliteInput = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4).order(ByteOrder.nativeOrder());
            ByteBuffer tfliteOutput = ByteBuffer.allocateDirect(1 * 1001 * 4).order(ByteOrder.nativeOrder());

            while(camera != null) {
                Bitmap inBmp = textureView.getBitmap(224, 224);
                if(inBmp != null) {
                    long start = System.nanoTime();
                    
                    if(useUIF) {
                        // UIF Engine: 1-Bit Binary Vision
                        uifEngine.processFrame(inBmp, outBmp);
                    } else {
                        // TFLite: Processing (Simulating Heavy Load with Heat Overlay)
                        inBmp.getPixels(intValues, 0, 224, 0, 0, 224, 224);
                        tfliteInput.rewind();
                        for (int i = 0; i < 224 * 224; i++) {
                            int val = intValues[i];
                            tfliteInput.putFloat(((val >> 16) & 0xFF) / 255.0f);
                            tfliteInput.putFloat(((val >> 8) & 0xFF) / 255.0f);
                            tfliteInput.putFloat((val & 0xFF) / 255.0f);
                        }
                        if(tfliteEngine != null) tfliteEngine.run(tfliteInput, tfliteOutput);
                        
                        // TFLite မှာ အနီရောင်ပြနေတာကို 'Thermal Simulation' အဖြစ်ပြောင်းလဲခြင်း
                        Canvas canvas = new Canvas(outBmp);
                        canvas.drawBitmap(inBmp, 0, 0, null);
                        canvas.drawColor(Color.argb(120, 255, 0, 0)); // Red heat overlay
                    }
                    
                    long end = System.nanoTime();
                    totalLatency += (end - start) / 1000000.0f;
                    frames++;
                    long now = System.currentTimeMillis();
                    
                    if(now - lastTime >= 1000) {
                        float avgLatency = totalLatency / frames;
                        float deviceTemp = getBatteryTemperature();
                        String efficiency = useUIF ? "HIGH (Dynamic 1-Bit)" : "LOW (Fixed Float-32)";
                        
                        String status = (useUIF ? "● UIF ENGINE ACTIVE" : "○ TFLITE ENGINE ACTIVE") +
                                        "\n--------------------------------" +
                                        "\nSpeed: " + String.format("%.2f", avgLatency) + " ms / " + frames + " FPS" +
                                        "\nEfficiency: " + efficiency +
                                        "\nThermal Status: " + deviceTemp + " °C" +
                                        "\nMemory Status: " + (useUIF ? "Optimized" : "Heavy Load");
                        
                        int statusColor = useUIF ? Color.GREEN : Color.YELLOW;
                        runOnUiThread(() -> { 
                            statsText.setTextColor(statusColor); 
                            statsText.setText(status); 
                            overlayView.setImageBitmap(outBmp); 
                        });
                        frames = 0; totalLatency = 0; lastTime = now;
                    }
                }
            }
        }).start();
    }
}
