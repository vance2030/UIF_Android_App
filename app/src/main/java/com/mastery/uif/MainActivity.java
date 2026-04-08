package com.mastery.uif;
import android.Manifest; import android.app.Activity; import android.content.pm.PackageManager;
import android.graphics.Bitmap; import android.graphics.Color; import android.graphics.SurfaceTexture;
import android.hardware.Camera; import android.os.Bundle; import android.view.TextureView;
import android.widget.TextView; import android.widget.LinearLayout;
import java.io.File; import java.io.FileOutputStream; import java.io.InputStream;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private Camera camera; private TextureView textureView; private TextView statsText;
    private UIFEngine engine; private boolean isEngineLoaded = false;
    private long lastTime = 0; private int frames = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        textureView = new TextureView(this); textureView.setSurfaceTextureListener(this);
        statsText = new TextView(this); statsText.setTextColor(Color.GREEN); statsText.setTextSize(18f);
        statsText.setBackgroundColor(Color.BLACK); statsText.setPadding(20, 20, 20, 20);
        layout.addView(statsText); layout.addView(textureView);
        setContentView(layout);
        
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            
        engine = new UIFEngine();
        try {
            File f = new File(getCacheDir(), "real_model.uif");
            if (!f.exists()) {
                InputStream is = getAssets().open("real_model.uif");
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buffer = new byte[1024]; int read;
                while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
            }
            isEngineLoaded = engine.loadModel(f.getAbsolutePath());
        } catch (Exception e) { e.printStackTrace(); }
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        try { camera = Camera.open(); camera.setPreviewTexture(st); camera.startPreview(); startSIMDTest(); } catch (Exception e) {}
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if(camera != null) { camera.stopPreview(); camera.release(); }
        if(engine != null) { engine.releaseEngine(); } return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}

    private void startSIMDTest() {
        new Thread(new Runnable() {
            @Override public void run() {
                while(camera != null) {
                    Bitmap inBmp = textureView.getBitmap(224, 224);
                    if(inBmp != null && isEngineLoaded) {
                        long start = System.nanoTime();
                        engine.runInference(inBmp);
                        long end = System.nanoTime();
                        final float latencyMs = (end - start) / 1000000.0f;
                        frames++; long now = System.currentTimeMillis();
                        if(now - lastTime >= 1000) {
                            final int currentFps = frames;
                            runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    statsText.setText("[UIF V5: SIMD GAME CHANGER]\n>> Status: PARSED & RUNNING\n>> Latency: " + String.format("%.2f", latencyMs) + " ms\n>> Speed: " + currentFps + " FPS\n>> CPU Vector: ARM NEON 128-Bit");
                                }
                            });
                            frames = 0; lastTime = now;
                        }
                    }
                }
            }
        }).start();
    }
}
