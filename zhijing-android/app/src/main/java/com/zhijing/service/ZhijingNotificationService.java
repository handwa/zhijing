package com.zhijing.service;

import android.content.ComponentName;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class ZhijingNotificationService extends NotificationListenerService {

    private static final String[] MEDIA_PACKAGES = {
            "com.netease.cloudmusic",   // 网易云音乐
            "com.tencent.qqmusic",      // QQ音乐
            "com.kugou.android",        // 酷狗音乐
            "com.spotify.music",        // Spotify
    };

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isMediaApp(sbn.getPackageName())) return;

        android.os.Bundle extras = sbn.getNotification().extras;
        String title = extras.getString(android.app.Notification.EXTRA_TITLE, "");
        String text = extras.getString(android.app.Notification.EXTRA_TEXT, "");

        if (!title.isEmpty()) {
            handleMediaNotification(sbn.getPackageName(), title, text);
        }
    }

    private boolean isMediaApp(String packageName) {
        for (String pkg : MEDIA_PACKAGES) {
            if (pkg.equals(packageName)) return true;
        }
        return false;
    }

    private void handleMediaNotification(String pkg, String title, String text) {
        // 将媒体信息送入知识图谱
        String combined = title + " " + text;
        com.zhijing.ai.TextExtractor extractor = new com.zhijing.ai.TextExtractor();
        com.zhijing.ai.TextExtractor.ExtractionResult result = extractor.extract(combined);

        com.zhijing.graph.KnowledgeGraph graph = new com.zhijing.graph.KnowledgeGraph(this);
        graph.addContent(result, null);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
    }
}
