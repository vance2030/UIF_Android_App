package com.mastery.uif;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.FrameLayout;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {
    private Camera camera;
    private TextureView textureView;
    private ImageView overlayView;
    private TextView fpsText;
    private UIFEngine engine;
    private long lastTime = 0;
    private int frames = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        engine = new UIFEngine();
        
        // ဖန်သားပြင်ကို Code ဖြင့် တိုက်ရိုက်တည်ဆောက်ခြင်း (XML မလိုပါ)
        FrameLayout layout = new FrameLayout(this);
        textureView = new TextureView(this);
        textureView.setSurfaceTextureListener(this);
        overlayView = new ImageView(this);
        overlayView.setScaleType(ImageView.ScaleType.FIT_XY);
        
        fpsText = new TextView(this);
        fpsText.setTextColor(0xFF00FF00); // အစိမ်းရောင်
        fpsText.setTextSize(20f);
        fpsText.setPadding(30, 50, 0, 0);

        layout.addView(textureView);
        layout.addView(overlayView);
        layout.addView(fpsText);
        setContentView(layout);

        // ကင်မရာ အသုံးပြုခွင့် တောင်းခံခြင်း
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        try {
            camera = Camera.open();
            camera.setPreviewTexture(surface);
            camera.startPreview();
            startAIThread(); // C++ Engine ကို စတင်လည်ပတ်ခြင်း
        } catch (Exception e) {}
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if(camera != null) { camera.stopPreview(); camera.release(); }
        return true;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) {}

    private void startAIThread() {
        new Thread(() -> {
            // Resolution ကို 320x240 ထား၍ Real-time Math ကို သက်သေပြမည်
            Bitmap outBmp = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
            while(camera != null) {
                Bitmap inBmp = textureView.getBitmap(320, 240);
                if(inBmp != null) {
                    // MASTER Y ၏ C++ ENGINE က ဓာတ်ပုံကို တွက်ချက်ခြင်း
                    engine.processFrame(inBmp, outBmp);
                    frames++;
                    long now = System.currentTimeMillis();
                    if(now - lastTime >= 1000) {
                        final int currentFps = frames;
                        runOnUiThread(() -> fpsText.setText("[UIF ENGINE ONLINE]\nC++ Math FPS: " + currentFps));
                        frames = 0; lastTime = now;
                    }
                    // တွက်ပြီးသား ပုံကို ဖုန်း Screen ပေါ်သို့ ပြန်ဆွဲတင်ခြင်း
                    runOnUiThread(() -> overlayView.setImageBitmap(outBmp));
                }
            }
        }).start();
    }
}
