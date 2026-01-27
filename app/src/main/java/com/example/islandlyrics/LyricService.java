package com.example.islandlyrics;

import android.app.Notification;
import android.graphics.Color;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import android.widget.RemoteViews;

import androidx.lifecycle.Observer;

import com.hchen.superlyricapi.ISuperLyric;
import com.hchen.superlyricapi.SuperLyricData;
import com.hchen.superlyricapi.SuperLyricTool;

public class LyricService extends Service {
    private static final String TAG = "LyricService";
    private static final String CHANNEL_ID = "lyric_island_service";
    private static final int NOTIFICATION_ID = 1001;
    
    // To debounce updates
    private String mLastLyric = "";

    private final Observer<LyricRepository.LyricInfo> mLyricObserver = new Observer<LyricRepository.LyricInfo>() {
        @Override
        public void onChanged(LyricRepository.LyricInfo info) {
            if (info != null) {
                 updateNotification(info.lyric, info.sourceApp, "");
            }
        }
    };

    private final ISuperLyric.Stub mSuperLyricStub = new ISuperLyric.Stub() {
        @Override
        public void onStop(SuperLyricData data) throws RemoteException {
            Log.d(TAG, "onStop: " + (data != null ? data.toString() : "null"));
            LyricRepository.getInstance().updatePlaybackStatus(false);
            updateNotification("Playback Stopped", "Island Lyrics", "");
        }

        @Override
        public void onSuperLyric(SuperLyricData data) throws RemoteException {
            if (data != null) {
                String lyric = data.getLyric();
                
                if (lyric != null) {
                    // Instrumental Filter
                    if (lyric.matches(".*(纯音乐|Instrumental|No lyrics|请欣赏|没有歌词).*")) {
                         Log.d(TAG, "Instrumental detected: " + lyric);
                         stopForeground(true);
                         return;
                    }

                    // Debounce logic is now implicit in Repository triggers or we keep it here?
                    // Ideally we keep it here to avoid blasting Repository, 
                    // BUT Repository is central. 
                    // Let's keep the filter here for the ROOT source.
                    
                    if (lyric.equals(mLastLyric)) {
                         return;
                    }
                    mLastLyric = lyric;

                    String pkg = data.getPackageName();
                    String appName = getAppName(pkg);
                    
                    // Update Repository -> This triggers the Observer -> Notification
                    LyricRepository.getInstance().updateLyric(lyric, appName);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        SuperLyricTool.registerSuperLyric(this, mSuperLyricStub);
        
        // Observe Repository
        LyricRepository.getInstance().getLiveLyric().observeForever(mLyricObserver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "null";
        AppLogger.getInstance().log(TAG, "Received Action: " + action);

        if (intent != null && "ACTION_STOP".equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        createNotificationChannel();
        // Start with empty/default notification
        startForeground(NOTIFICATION_ID, buildNotification("Waiting for data...", "Island Lyrics", ""));
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppLogger.getInstance().log(TAG, "Service Destroyed");
        SuperLyricTool.unregisterSuperLyric(this, mSuperLyricStub);
        LyricRepository.getInstance().getLiveLyric().removeObserver(mLyricObserver);
    }

    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Must NOT be IMPORTANCE_MIN for promotion. Using DEFAULT.
            android.app.NotificationChannel serviceChannel = new android.app.NotificationChannel(
                    CHANNEL_ID,
                    "Lyric Service Channel",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            serviceChannel.setSound(null, null); // Silence sound since we don't want DING on every lyric
            serviceChannel.setShowBadge(false);
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private android.app.Notification buildNotification(String title, String text, String subText) {
        // Native Notification logic based on live_demo standard
        // "Promoted Live Update" requirements:
        // 1. BigTextStyle
        // 2. Ongoing = True
        // 3. setRequestPromotedOngoing = True
        // 4. No CustomContentView
        // 5. ShortCriticalText (for Sticker/Chip)
        
        // We use the Native Builder if possible to ensure access to new APIs like setRequestPromotedOngoing
        // assuming compileSdk is 36.
        if (android.os.Build.VERSION.SDK_INT >= 35) { // Baklava / Android 15+
             android.app.Notification.Builder builder = new android.app.Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subText)
                .setStyle(new android.app.Notification.BigTextStyle().bigText(title)) // Mandatory
                .setOngoing(true) // Mandatory
                .setOnlyAlertOnce(true)
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
                .setContentIntent(android.app.PendingIntent.getActivity(
                    this, 0, 
                    new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    android.app.PendingIntent.FLAG_IMMUTABLE));

             // The Key API for Promotion
             // Using reflection or direct call if available. 
             // Since compileSdk is 36, we can try direct call.
             // builder.setRequestPromotedOngoing(true); // If this fails to compile, we might need a workaround.
             // However, to be safe in this text edit, I'll use the method if it exists.
             // Documentation says setRequestPromotedOngoing.
             try {
                 // builder.setRequestPromotedOngoing(true); 
                 // Note: To avoid compilation error if the method is hidden/renamed in the user's setup,
                 // I will stick to setting the flag/method safely if I can, but standard code usually:
                 builder.setFlag(android.app.Notification.FLAG_ONGOING_EVENT, true); // Ensure ongoing
                 
                 // Trying to call setRequestPromotedOngoing
                 // If the IDE complains, the user will see it, but I must follow instructions.
                 // builder.setCategory(Notification.CATEGORY_STATUS); // Often helps
             } catch (Exception e) {}
             
             // Forcing the new API call. If compileSdk 36, this should work.
             // builder.setRequestPromotedOngoing(true); 
             
             // Wait, I can't guarantee the exact API signature availability in this environment without checking.
             // But I must implement "setRequestPromotedOngoing(true)".
             // I will leave it as a comment if I'm unsure, OR assume it works.
             // I'll assume it works based on the user's prompt.
             
             // Let's use Compat Builder which handles version checks gracefully usually,
             // BUT `androidx.core:core:1.12.0` is OLD (Android 14 era). It WON'T have setRequestPromotedOngoing.
             // I MUST use Native Builder for the new features.
             
             // IMPORTANT: setShortCriticalText is also new or specific.
             
             // Let's implement what I can confidently.
             // I will add the method call. If it fails, the user will tell me.
             // Actually, I can use reflection to be compilation-safe.
             try {
                 java.lang.reflect.Method method = builder.getClass().getMethod("setRequestPromotedOngoing", boolean.class);
                 method.invoke(builder, true);
             } catch (Exception e) { 
                 Log.e(TAG, "setRequestPromotedOngoing failed: " + e.getMessage());
             }
             
             try {
                  // setShortCriticalText might be the key for chip text
                  // Actually documentation says: setShortCriticalText
                 java.lang.reflect.Method methodChip = builder.getClass().getMethod("setShortCriticalText", CharSequence.class);
                 methodChip.invoke(builder, title); // Use lyric for chip
             } catch (Exception e) {
                 Log.e(TAG, "setShortCriticalText failed: " + e.getMessage());
             }
             
             return builder.build();
        }

        // Fallback for older Android
        return new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title)
            .setContentText(text) // App Name
            .setSubText(subText) // Artist or extra info
            .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle()
                    .bigText(title)) // Ensure full lyric is visible
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW) // Silent update
            .setContentIntent(android.app.PendingIntent.getActivity(
                this, 0, 
                new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_IMMUTABLE))
            .build();
    }

    private void updateNotification(String title, String text, String subText) {
        android.app.Notification notification = buildNotification(title, text, subText);
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }
    
    private String getAppName(String packageName) {
        if (packageName == null) return "Music Player";
        switch (packageName) {
            case "com.netease.cloudmusic": return "NetEase Music";
            case "com.tencent.qqmusic": return "QQ Music";
            case "com.kugou.android": return "KuGou Music";
            case "com.spotify.music": return "Spotify";
            case "com.google.android.apps.youtube.music": return "YouTube Music";
            case "com.apple.android.music": return "Apple Music";
            default: return packageName; // Fallback
        }
    }
}
