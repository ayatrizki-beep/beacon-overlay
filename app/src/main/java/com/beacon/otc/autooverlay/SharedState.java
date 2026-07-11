package com.beacon.otc.autooverlay;

public class SharedState {
    public static volatile boolean autoOn = true;

    public static volatile boolean hasPrice = false;
    public static volatile double price = 0.0;

    public static volatile boolean hasCrowd = false;
    public static volatile int upPct = 0;
    public static volatile int downPct = 0;

    public static volatile String lastSignal = "READY";
    public static volatile String lastPhase = "-";
    public static volatile String lastWait = "-";
    public static volatile String lastLevels = "-";
    public static volatile String lastReason = "-";
    public static volatile int lastConfidence = 0;

    public static void setCrowdUp(int pct) {
        hasCrowd = true;
        upPct = pct;
        downPct = 100 - pct;
    }

    public static void setCrowdDown(int pct) {
        hasCrowd = true;
        downPct = pct;
        upPct = 100 - pct;
    }

    public static String priceText() {
        if (!hasPrice) return "-";
        if (Math.abs(price) >= 1000) return String.valueOf(Math.round(price * 100.0) / 100.0);
        if (Math.abs(price) >= 100) return String.valueOf(Math.round(price * 1000.0) / 1000.0);
        return String.valueOf(Math.round(price * 100000.0) / 100000.0);
    }

    public static String crowdText() {
        if (!hasCrowd) return "CROWD -";
        return "N" + upPct + " / T" + downPct;
    }
}
