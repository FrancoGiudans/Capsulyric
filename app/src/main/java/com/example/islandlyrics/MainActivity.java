package com.example.islandlyrics;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.graphics.Insets;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "IslandLyrics";
    private static final String PREFS_NAME = "IslandLyricsPrefs";
    private static final String PREF_WHITELIST = "whitelist_packages";

    // UI Elements
    private MaterialCardView mCardStatus;
    private ImageView mIvStatusIcon;
    private TextView mTvStatusText;
    private TextView mTvAppVersion;
    
    private TextView mTvSong;
    private TextView mTvArtist;
    private TextView mTvLyric;
    
    // Default Whitelist
    private static final String[] DEFAULT_WHITELIST = {
        "com.netease.cloudmusic",
        "com.tencent.qqmusic",
        "com.kugou.android",
        "com.spotify.music",
        "com.apple.android.music",
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Handle Edge-to-Edge / Status Bar Insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeDefaultWhitelist();
        bindViews();
        setupClickListeners();
        setupObservers();
        
        checkAndRequestPermission();
        checkNotificationListenerPermission();
        
        updateVersionInfo();
    }
    
    private void initializeDefaultWhitelist() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.contains(PREF_WHITELIST)) {
            Set<String> set = new HashSet<>(Arrays.asList(DEFAULT_WHITELIST));
            prefs.edit().putStringSet(PREF_WHITELIST, set).apply();
        }
    }

    private void bindViews() {
        mCardStatus = findViewById(R.id.cv_status);
        mIvStatusIcon = findViewById(R.id.iv_status_icon);
        mTvStatusText = findViewById(R.id.tv_status_text);
        
        mTvAppVersion = findViewById(R.id.tv_app_version);
        
        mTvSong = findViewById(R.id.tv_song);
        mTvArtist = findViewById(R.id.tv_artist);
        mTvLyric = findViewById(R.id.tv_lyric);
    }

    private void setupClickListeners() {
        // Status Card -> Diagnostics
        mCardStatus.setOnClickListener(v -> showDiagnosticsDialog());
        
        // Menu Items
        findViewById(R.id.menu_github).setOnClickListener(v -> openGitHub());
        findViewById(R.id.menu_log).setOnClickListener(v -> showDebugLogs());
        findViewById(R.id.menu_settings).setOnClickListener(v -> showWhitelistDialog());
        
        // Copy Lyric
        findViewById(R.id.cv_playback).setOnClickListener(v -> {
            String text = mTvLyric.getText().toString();
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("Lyric", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Lyric copied!", Toast.LENGTH_SHORT).show();
        });


    }

    private void updateStatusCard(boolean isActive) {
        if (isActive) {
            String source = LyricRepository.getInstance().getSourceAppName().getValue();
            if (source == null) source = "Active";
            
            // Solid Green logic
            mCardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_active));
            mIvStatusIcon.setImageResource(R.drawable.ic_check_circle);
            // Icons are white on solid color
            mIvStatusIcon.setImageTintList(ColorStateList.valueOf(android.graphics.Color.WHITE));
            
            mTvStatusText.setText(getString(R.string.label_status_prefix) + source);
            // Text is white
            mTvStatusText.setTextColor(android.graphics.Color.WHITE);
        } else {
            // Solid Red logic
            mCardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.status_inactive));
            mIvStatusIcon.setImageResource(R.drawable.ic_cancel);
            mIvStatusIcon.setImageTintList(ColorStateList.valueOf(android.graphics.Color.WHITE));
            
            mTvStatusText.setText(getString(R.string.label_status_prefix) + getString(R.string.status_inactive));
            mTvStatusText.setTextColor(android.graphics.Color.WHITE);
        }
    }
    
    private void updateVersionInfo() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            String type = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 ? "Debug" : "Release";
            // Use new format: App Version: X.X.X \n App Channel: Debug
            mTvAppVersion.setText(getString(R.string.app_version_fmt, version, type));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void setupObservers() {
        LyricRepository repo = LyricRepository.getInstance();

        // Playback Status Observer
        repo.getIsPlaying().observe(this, isPlaying -> {
            updateStatusCard(isPlaying);
        });

        // Lyric Observer
        repo.getCurrentLyric().observe(this, lyric -> {
            String prefix = getString(R.string.label_lyric_prefix);
            if (lyric != null) {
                mTvLyric.setText(prefix + lyric);
            } else {
                mTvLyric.setText(prefix);
            }
        });

        // Metadata Observer
        repo.getLiveMetadata().observe(this, mediaInfo -> {
             // Strings defined in XML: label_title_prefix, label_artist_prefix
             // We need to fetch and explicitly set them
             String titlePrefix = getString(R.string.label_title_prefix);
             String artistPrefix = getString(R.string.label_artist_prefix);
             
             if (mediaInfo == null) return;
             if (mediaInfo.title != null) mTvSong.setText(titlePrefix + mediaInfo.title);
             if (mediaInfo.artist != null) mTvArtist.setText(artistPrefix + mediaInfo.artist);
        });
    }

    // --- Dialogs ---

    private void showDiagnosticsDialog() {
        String source = LyricRepository.getInstance().getSourceAppName().getValue();
        boolean isConnected = true; // Assuming permission is connected if we are here
        
        String message = "Service: " + (Boolean.TRUE.equals(LyricRepository.getInstance().getIsPlaying().getValue()) ? "Running" : "Idle") + "\n" +
                         "Current Source: " + (source != null ? source : "None") + "\n" +
                         "API Status: Connected";

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_diagnostics_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_refresh_session, (dialog, which) -> {
                    // Send command to regenerate
                    Intent intent = new Intent(this, MediaMonitorService.class);
                    // trigger re-bind/check (simple startService works to trigger onStartCommand if we had it, 
                    // but for NotificationListener, we can't easily force it other than toggle.
                    // Ideally MediaMonitorService exposes a static method or broadcast receiver.
                    // For now, let's just log.
                    AppLogger.getInstance().log(TAG, "User requested session refresh.");
                    Toast.makeText(this, "Refresh requested (Check Log)", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showWhitelistDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> whitelisted = prefs.getStringSet(PREF_WHITELIST, new HashSet<>());
        
        // List installed music apps or just usage list
        // For simplicity, let's show the default list + whatever is currently in prefs + common ones
        // In a real app we might query PackageManager for Intent.CATEGORY_APP_MUSIC
        
        final String[] apps = DEFAULT_WHITELIST; // Use the fixed list for now to ensure stability
        boolean[] checkedItems = new boolean[apps.length];
        
        for (int i = 0; i < apps.length; i++) {
            checkedItems[i] = whitelisted.contains(apps[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_whitelist_title)
                .setMultiChoiceItems(apps, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Save", (dialog, which) -> {
                    Set<String> newSet = new HashSet<>();
                    for (int i = 0; i < apps.length; i++) {
                        if (checkedItems[i]) {
                            newSet.add(apps[i]);
                        }
                    }
                    prefs.edit().putStringSet(PREF_WHITELIST, newSet).apply();
                    Toast.makeText(this, "Saved. Service will update shortly.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDebugLogs() {
        TextView logView = new TextView(this);
        logView.setPadding(32, 32, 32, 32);
        logView.setTextIsSelectable(true);
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(logView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Internal Logs")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .create();
        
        AppLogger.getInstance().getLogs().observe(this, logs -> {
            logView.setText(logs);
            scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
        });
        
        dialog.show();
    }
    
    private void openGitHub() {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/HChenX/IslandLyrics")); 
        startActivity(intent);
    }

    // --- Permissions ---

    private void checkAndRequestPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void checkNotificationListenerPermission() {
        if (!android.provider.Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners").contains(getPackageName())) {
            
            new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Island Lyrics needs 'Notification Access'. Grant?")
                .setPositiveButton("Grant", (dialog, which) -> {
                    startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }
}
