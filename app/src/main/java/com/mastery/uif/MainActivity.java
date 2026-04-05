package com.mastery.uif;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import android.graphics.Color;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        TextView tv = new TextView(this);
        tv.setTextColor(Color.GREEN);
        tv.setBackgroundColor(Color.BLACK);
        tv.setTextSize(18f);
        tv.setPadding(50, 50, 50, 50);

        try {
            UIFEngine engine = new UIFEngine();
            tv.setText("UIF TRANSCENDENCE ENGINE\n\nSTATUS: ONLINE\nC++ NATIVE LAYER: LOADED SUCCESSFULLY\n\nMASTER Y, THE EDGE DEVICE IS NOW UNSTOPPABLE.");
        } catch (Exception e) {
            tv.setText("ERROR: C++ Engine failed to load.");
        }

        setContentView(tv);
    }
}
