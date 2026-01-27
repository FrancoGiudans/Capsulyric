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

public class MainActivity extends BaseActivity {
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
        
        // NO Auto-Permission Checks on Launch (Privacy First)
        // Permissions are now handled in SettingsActivity with Disclaimer.
        
        updateVersionInfo();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateStatusCardState();
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
        // Status Card -> Settings (Diagnostics moved to Settings)
        // mCardStatus.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class))); // Removed per new design, purely status now? Or keep?
        // User requested Settings Card at bottom.
        
        View cvSettings = findViewById(R.id.cv_settings);
        if (cvSettings != null) {
            cvSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        }
        
        findViewById(R.id.btn_copy_lyric).setOnClickListener(v -> {
            String text = mTvLyric.getText().toString();
            copyToClipboard("Lyric", text);
        });

        findViewById(R.id.btn_copy_all).setOnClickListener(v -> {
            String song = mTvSong.getText().toString();
            String artist = mTvArtist.getText().toString();
            String lyric = mTvLyric.getText().toString();
            String full = song + " - " + artist + "\n" + lyric;
            copyToClipboard("Song Info", full);
        });
    }

    private void copyToClipboard(String label, String text) {
        if (text != null && !text.isEmpty() && !text.equals("-")) {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, label + " copied!", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatusCardState() {
        // Check Permission
        boolean listenerGranted = isNotificationListenerEnabled();
        
        if (!listenerGranted) {
            // State: Missing Permission
            setCardState(false, "Permission Required", R.color.status_inactive); // Red/Orange
            // Optional: Blur playback card or show warning
        } else {
             // Check Playing Status
             Boolean isPlaying = LyricRepository.getInstance().getIsPlaying().getValue();
             if (Boolean.TRUE.equals(isPlaying)) {
                 String source = LyricRepository.getInstance().getSourceAppName().getValue();
                 setCardState(true, "Active: " + (source != null ? source : "Music"), R.color.status_active);
             } else {
                 setCardState(true, "Service Ready (Idle)", R.color.status_active); // Or distinct color?
             }
        }
    }
    
    private void setCardState(boolean isActive, String text, int colorResId) {
        mCardStatus.setCardBackgroundColor(ContextCompat.getColor(this, colorResId));
        mTvStatusText.setText(text);
        
        if (isActive) {
             mIvStatusIcon.setImageResource(R.drawable.ic_check_circle);
        } else {
             mIvStatusIcon.setImageResource(R.drawable.ic_cancel);
        }
    }
    
    private boolean isNotificationListenerEnabled() {
        return android.provider.Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners").contains(getPackageName());
    }

    private void updateVersionInfo() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            String type = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 ? "Debug" : "Release";
            mTvAppVersion.setText(getString(R.string.app_version_fmt, version, type));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void setupObservers() {
        LyricRepository repo = LyricRepository.getInstance();

        // Playback Status Observer
        repo.getIsPlaying().observe(this, isPlaying -> {
            updateStatusCardState();
        });

        // Lyric Observer
        repo.getCurrentLyric().observe(this, lyric -> {
            if (lyric != null) {
                mTvLyric.setText(lyric);
            } else {
                mTvLyric.setText("-");
            }
        });

        // Metadata Observer
        repo.getLiveMetadata().observe(this, mediaInfo -> {
             if (mediaInfo == null) return;
             // Ensure defaults if null
             mTvSong.setText(mediaInfo.title != null ? mediaInfo.title : "-");
             mTvArtist.setText(mediaInfo.artist != null ? mediaInfo.artist : "-");
        });
    }
}
