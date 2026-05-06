package com.zhijing.ai;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.BatteryManager;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class ContextAnalyzer {

    public static class DeviceContext {
        public String currentApp;       // 当前应用包名
        public boolean isHeadsetOn;     // 是否插耳机
        public boolean isCharging;      // 是否充电
        public boolean isNightTime;     // 是否夜间（22:00-07:00）
        public String recommendFormat;  // 推荐格式：TEXT / AUDIO / DEFER
    }

    private final Context context;

    public ContextAnalyzer(Context context) {
        this.context = context;
    }

    public DeviceContext analyze() {
        DeviceContext ctx = new DeviceContext();
        ctx.currentApp = getCurrentApp();
        ctx.isHeadsetOn = isHeadsetConnected();
        ctx.isCharging = isDeviceCharging();
        ctx.isNightTime = isNightTime();
        ctx.recommendFormat = decideFormat(ctx);
        return ctx;
    }

    private String getCurrentApp() {
        UsageStatsManager usageStatsManager =
                (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long time = System.currentTimeMillis();
        List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, time - 10000, time);
        if (stats == null || stats.isEmpty()) return "unknown";

        SortedMap<Long, UsageStats> sortedMap = new TreeMap<>();
        for (UsageStats usageStats : stats) {
            sortedMap.put(usageStats.getLastTimeUsed(), usageStats);
        }
        return sortedMap.get(sortedMap.lastKey()).getPackageName();
    }

    private boolean isHeadsetConnected() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isWiredHeadsetOn() || audioManager.isBluetoothA2dpOn();
    }

    private boolean isDeviceCharging() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.isCharging();
    }

    private boolean isNightTime() {
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        return hour >= 22 || hour < 7;
    }

    private String decideFormat(DeviceContext ctx) {
        // 夜间 + 视频类App → 推文字摘要
        if (ctx.isNightTime) return "TEXT";
        // 插耳机 → 可以推语音
        if (ctx.isHeadsetOn) return "AUDIO";
        // 默认文字
        return "TEXT";
    }
}
