package com.example.islandlyrics;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Base Activity to handle common UI logic like Pure Black mode.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Handle Pure Black Mode for OLED
        if (ThemeHelper.isPureBlackEnabled(this)) {
            // Check if we are physically in dark mode
            int nightModeFlags = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                getWindow().getDecorView().setBackgroundColor(android.graphics.Color.BLACK);
                // Also could try forcing window background drawable to null or black color drawable
                getWindow().setBackgroundDrawableResource(android.R.color.black);
            }
        }
    }
}
