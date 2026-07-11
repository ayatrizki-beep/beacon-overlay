package com.beacon.otc.autooverlay;

import java.util.Locale;

public class OtcIntentEngine {
    public static class Result {
        public String signal = "SKIP";
        public String phase = "-";
        public String wait = "-";
        public String levels = "-";
        public String reason = "-";
        public int confidence = 0;
    }

    public static Result decide(BitmapAnalyzer.Data d) {
        Result r = new Result();

        int sec = java.util.Calendar.getInstance().get(java.util.Calendar.SECOND);
        String zoneType = zoneType(sec);

        String crowd = "unknown";
        StringBuilder reason = new StringBuilder();

        if (SharedState.hasCrowd) {
            if (SharedState.upPct >= 80) {
                crowd = "buy_extreme";
                reason.append("NAIK ekstrem");
            } else if (SharedState.downPct >= 80) {
                crowd = "sell_extreme";
                reason.append("TURUN ekstrem");
            } else if (SharedState.upPct >= 60) {
                crowd = "buy_heavy";
                reason.append("NAIK ramai");
            } else if (SharedState.downPct >= 60) {
                crowd = "sell_heavy";
                reason.append("TURUN ramai");
            } else {
                crowd = "balanced";
                reason.append("Crowd seimbang");
            }
        } else {
            reason.append("Crowd kosong");
        }

        reason.append(" | ");
        if (d.doji) reason.append("Doji/noise");
        else if ("green".equals(d.dominant)) reason.append(d.bodyRatio >= 0.45 ? "Hijau besar=BUY trap" : "Hijau aktif");
        else if ("red".equals(d.dominant)) reason.append(d.bodyRatio >= 0.45 ? "Merah besar=SELL trap" : "Merah aktif");
        else reason.append("Candle campur");

        if ("upper".equals(d.wick)) reason.append(" | Wick atas");
        if ("lower".equals(d.wick)) reason.append(" | Wick bawah");
        if ("two_way".equals(d.wick)) reason.append(" | Wick dua arah");
        reason.append(" | MHI ").append(d.mhi);

        String phase = "RESET / ACAK";
        String bias = "SKIP";
        String entry = "WAIT";
        String wait = "1 candle reset";

        if (d.doji) {
            phase = "RESET / ACAK";
            bias = "SKIP";
            wait = "1 candle reset";
        } else if ("buy_extreme".equals(crowd) || "buy_heavy".equals(crowd)) {
            bias = "SELL";
            if ("green".equals(d.dominant)) {
                phase = "PANCING BUY";
                entry = "WAIT";
                wait = "1-2 candle";
                if ("upper".equals(d.wick) && "gold".equals(zoneType)) {
                    phase = "BUY TRAP SELESAI";
                    entry = "NOW";
                    wait = "0 candle / sekarang valid";
                }
            } else if ("red".equals(d.dominant)) {
                phase = "HUKUM BUY";
                entry = "gold".equals(zoneType) ? "NOW" : "WAIT";
                wait = "0-1 candle";
            } else {
                phase = "PANCING BUY";
                wait = "1 candle";
            }
        } else if ("sell_extreme".equals(crowd) || "sell_heavy".equals(crowd)) {
            bias = "BUY";
            if ("red".equals(d.dominant)) {
                phase = "PANCING SELL";
                entry = "WAIT";
                wait = "1-2 candle";
                if ("lower".equals(d.wick) && "gold".equals(zoneType)) {
                    phase = "SELL TRAP SELESAI";
                    entry = "NOW";
                    wait = "0 candle / sekarang valid";
                }
            } else if ("green".equals(d.dominant)) {
                phase = "HUKUM SELL";
                entry = "gold".equals(zoneType) ? "NOW" : "WAIT";
                wait = "0-1 candle";
            } else {
                phase = "PANCING SELL";
                wait = "1 candle";
            }
        } else {
            if ("upper".equals(d.wick)) {
                phase = "REJECTION ATAS";
                bias = "SELL";
                entry = "gold".equals(zoneType) ? "NOW" : "WAIT";
                wait = "0-1 candle";
            } else if ("lower".equals(d.wick)) {
                phase = "REJECTION BAWAH";
                bias = "BUY";
                entry = "gold".equals(zoneType) ? "NOW" : "WAIT";
                wait = "0-1 candle";
            } else if ("green".equals(d.dominant)) {
                phase = "POTENSI BUY TRAP";
                bias = "SELL";
                entry = "WAIT";
                wait = "1-2 candle";
            } else if ("red".equals(d.dominant)) {
                phase = "POTENSI SELL TRAP";
                bias = "BUY";
                entry = "WAIT";
                wait = "1-2 candle";
            }
        }

        if ("SELL".equals(bias) && "green".equals(d.mhi) && "NOW".equals(entry)) {
            entry = "WAIT";
            wait = "1 candle";
        }
        if ("BUY".equals(bias) && "red".equals(d.mhi) && "NOW".equals(entry)) {
            entry = "WAIT";
            wait = "1 candle";
        }

        String signal;
        if ("bad".equals(zoneType)) {
            signal = "NO ENTRY";
            wait = "Tunggu zona emas 45-54";
        } else if ("late".equals(zoneType)) {
            signal = ("BUY".equals(bias) || "SELL".equals(bias)) ? bias + " LATE" : "SKIP";
            wait = "Sudah telat. Tunggu 1 candle reset.";
        } else {
            signal = "SKIP".equals(bias) ? "SKIP" : bias + " " + entry;
        }

        int conf = confidence(crowd, d, zoneType);

        r.signal = signal;
        r.phase = phase;
        r.wait = wait;
        r.levels = makeLevels(bias);
        r.reason = reason.toString();
        r.confidence = conf;
        return r;
    }

    private static String zoneType(int sec) {
        if (sec <= 29) return "bad";
        if (sec <= 44) return "bad";
        if (sec <= 54) return "gold";
        return "late";
    }

    public static String zoneName() {
        int sec = java.util.Calendar.getInstance().get(java.util.Calendar.SECOND);
        if (sec <= 29) return "NOISE";
        if (sec <= 44) return "STOP";
        if (sec <= 54) return "GOLD";
        return "DEAD";
    }

    private static int confidence(String crowd, BitmapAnalyzer.Data d, String zoneType) {
        if (d.doji) return 25;
        int c = 45;
        if ("buy_extreme".equals(crowd) || "sell_extreme".equals(crowd)) c += 20;
        else if ("buy_heavy".equals(crowd) || "sell_heavy".equals(crowd)) c += 13;

        if ("upper".equals(d.wick) || "lower".equals(d.wick)) c += 12;
        else if ("two_way".equals(d.wick)) c -= 10;

        if ("green".equals(d.mhi) || "red".equals(d.mhi)) c += 5;
        else c -= 5;

        if ("gold".equals(zoneType)) c += 10;
        else c -= 18;

        if (c < 5) c = 5;
        if (c > 95) c = 95;
        return c;
    }

    private static String makeLevels(String bias) {
        if (!SharedState.hasPrice || "SKIP".equals(bias)) return "Fake:- | Batal:- | Target:-";
        double p = SharedState.price;
        double st = priceStep(p);

        if ("SELL".equals(bias)) {
            String fake = fmt(p + st * 0.5) + "-" + fmt(p + st * 1.3);
            String trigger = "SELL bawah " + fmt(p - st * 0.4);
            String cancel = fmt(p + st * 2.0);
            String target = fmt(p - st * 1.2) + "/" + fmt(p - st * 2.2) + "/" + fmt(p - st * 3.4);
            return "Fake:" + fake + " | " + trigger + " | Batal:" + cancel + " | Target:" + target;
        } else if ("BUY".equals(bias)) {
            String fake = fmt(p - st * 1.3) + "-" + fmt(p - st * 0.5);
            String trigger = "BUY atas " + fmt(p + st * 0.4);
            String cancel = fmt(p - st * 2.0);
            String target = fmt(p + st * 1.2) + "/" + fmt(p + st * 2.2) + "/" + fmt(p + st * 3.4);
            return "Fake:" + fake + " | " + trigger + " | Batal:" + cancel + " | Target:" + target;
        }
        return "Fake:- | Batal:- | Target:-";
    }

    private static double priceStep(double price) {
        double p = Math.abs(price);
        if (p >= 10000) return 10.0;
        if (p >= 1000) return 5.0;
        if (p >= 100) return 1.0;
        if (p >= 10) return 0.10;
        return 0.01;
    }

    private static String fmt(double x) {
        if (Math.abs(x) >= 1000) return String.format(Locale.US, "%.2f", x);
        if (Math.abs(x) >= 100) return String.format(Locale.US, "%.3f", x);
        return String.format(Locale.US, "%.5f", x);
    }
}
