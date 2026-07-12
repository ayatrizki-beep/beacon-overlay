package com.beacon.otc.autooverlay;

import java.util.Calendar;
import java.util.Locale;

public class OtcIntentEngine {
    public static class Result {
        public String signal = "WAIT";
        public String phase = "-";
        public String wait = "-";
        public String levels = "-";
        public String reason = "-";
        public int confidence = 0;
    }

    private static String preparedBias = "SKIP";
    private static String preparedMode = "-";
    private static String preparedPhase = "-";
    private static int preparedConfidence = 0;
    private static int preparedMinute = -1;

    private static double lastPrice = Double.NaN;
    private static String lastTick = "NO_PRICE";

    private static String deathSpike = "NONE";
    private static int deathMinute = -1;

    public static Result decide(BitmapAnalyzer.Data d) {
        Result r = new Result();

        Calendar cal = Calendar.getInstance();
        int sec = cal.get(Calendar.SECOND);
        int minute = cal.get(Calendar.MINUTE);

        updateTick();

        String zone = zoneType(sec);
        String crowd = classifyCrowd();

        String squeeze = detectSqueeze(d, crowd);
        String liveBias = decideBias(d, crowd, squeeze);
        String liveMode = decideMode(d, crowd, squeeze, liveBias);
        String livePhase = decidePhase(d, crowd, squeeze, liveBias);
        int liveConf = confidence(d, crowd, squeeze, liveBias, zone);

        updateDeathSpike(d, sec, minute, crowd);

        if ("prepare".equals(zone)) {
            if (!"SKIP".equals(liveBias) && liveConf >= 50) {
                preparedBias = liveBias;
                preparedMode = liveMode;
                preparedPhase = livePhase;
                preparedConfidence = liveConf;
                preparedMinute = minute;
            }

            r.signal = safeSignal(preparedBias, "SIAPKAN");
            r.phase = "PREPARE / VALIDASI ARAH";
            r.wait = "ARAH SAJA. Jangan klik. Entry nanti 00-05.";
            r.levels = makeLevels(preparedBias);
            r.reason = makeReason(d, crowd, squeeze, zone, preparedBias, preparedMode, "SIAPKAN", preparedConfidence);
            r.confidence = preparedConfidence;
            return r;
        }

        if ("death".equals(zone)) {
            if (!"SKIP".equals(liveBias) && liveConf >= preparedConfidence) {
                preparedBias = liveBias;
                preparedMode = liveMode;
                preparedPhase = livePhase;
                preparedConfidence = liveConf;
                preparedMinute = minute;
            }

            r.signal = safeSignal(preparedBias, "TUNGGU CANDLE BARU");
            r.phase = "DEATH ZONE / FAKE SPIKE WATCH";
            r.wait = "Jangan klik 55-59. Tunggu close dan candle baru.";
            r.levels = makeLevels(preparedBias);
            r.reason = "Spike akhir: " + deathSpike + "\n" +
                    makeReason(d, crowd, squeeze, zone, preparedBias, preparedMode, "WAIT NEXT", Math.max(20, preparedConfidence - 5));
            r.confidence = Math.max(20, preparedConfidence - 5);
            return r;
        }

        if ("open_noise".equals(zone)) {
            r.signal = "TUNGGU DETIK 02";
            r.phase = "OPEN NOISE";
            r.wait = "Jangan klik 00-01. Tunggu detik 02-04.";
            r.levels = makeLevels(preparedBias);
            r.reason = "Noise awal candle. Entry 1M baru aman di detik 02-04 jika setup sebelumnya valid.";
            r.confidence = 20;
            return r;
        }

        if ("execute".equals(zone)) {
            int setupAge = preparedMinute < 0 ? 999 : (minute - preparedMinute + 60) % 60;
            boolean freshPrepare = setupAge == 1 && !"SKIP".equals(preparedBias);
            boolean tickOk = tickSupports(preparedBias);

            if (freshPrepare && preparedConfidence >= 60 && tickOk) {
                r.signal = safeSignal(preparedBias, "SEKARANG");
                r.phase = "EXECUTION / FULL 1M";
                r.wait = "KLIK SEKARANG. Valid 00-05. Setelah itu batal.";
                r.levels = makeLevels(preparedBias);
                r.reason = "Entry valid: candle baru + setup dari candle sebelumnya + tick mendukung.\n" +
                        makeReason(d, crowd, squeeze, zone, preparedBias, preparedMode, "NOW", Math.min(95, preparedConfidence + 7));
                r.confidence = Math.min(95, preparedConfidence + 7);
                return r;
            }

            if (freshPrepare && preparedConfidence >= 50 && !tickOk) {
                r.signal = safeSignal(preparedBias, "TUNGGU TICK");
                r.phase = "EXECUTION / TICK BELUM SETUJU";
                r.wait = "Arah ada, tapi tick belum mendukung. Lewat 05 = batal.";
                r.levels = makeLevels(preparedBias);
                r.reason = "Jangan klik kalau tick berlawanan / belum jelas.\n" +
                        makeReason(d, crowd, squeeze, zone, preparedBias, preparedMode, "WAIT TICK", preparedConfidence);
                r.confidence = preparedConfidence;
                return r;
            }

            r.signal = "SKIP";
            r.phase = "NO FRESH SETUP";
            r.wait = "Tidak ada setup valid dari 45-59 sebelumnya.";
            r.levels = "Fake:- | Batal:- | Target:-";
            r.reason = "Belum ada arah valid. Tunggu zona PREPARE berikutnya.";
            r.confidence = 25;
            return r;
        }

        if ("late".equals(zone)) {
            String b = "SKIP".equals(preparedBias) ? liveBias : preparedBias;
            String m = "SKIP".equals(preparedBias) ? liveMode : preparedMode;
            int c = Math.max(liveConf, preparedConfidence);

            if (!"SKIP".equals(b)) {
                r.signal = safeSignal(b, "TELAT 1M");
                r.phase = "LATE WARNING";
                r.wait = "Telat untuk 1M. Jangan kejar. 1M30/2M wajib 5M+10M searah.";
                r.levels = makeLevels(b);
                r.reason = makeReason(d, crowd, squeeze, zone, b, m, "LATE", Math.max(20, c - 10));
                r.confidence = Math.max(20, c - 10);
                return r;
            }
        }

        if ("missed".equals(zone)) {
            String b = "SKIP".equals(liveBias) ? preparedBias : liveBias;
            String m = "SKIP".equals(liveBias) ? preparedMode : liveMode;
            int c = Math.max(liveConf, preparedConfidence);

            if (!"SKIP".equals(b)) {
                r.signal = safeSignal(b, "LEWAT 1M");
                r.phase = "EXPIRY GUARD";
                r.wait = "Setup 1M hangus. Jangan entry. Tunggu candle berikutnya.";
                r.levels = makeLevels(b);
                r.reason = makeReason(d, crowd, squeeze, zone, b, m, "MISSED", Math.max(15, c - 18));
                r.confidence = Math.max(15, c - 18);
                return r;
            }
        }

        if ("stop".equals(zone)) {
            r.signal = "SKIP";
            r.phase = "STOP HUNT";
            r.wait = "Jangan entry 30-44. Baca spike palsu saja.";
            r.levels = makeLevels(liveBias);
            r.reason = makeReason(d, crowd, squeeze, zone, liveBias, liveMode, "SKIP", Math.max(15, liveConf - 15));
            r.confidence = Math.max(15, liveConf - 15);
            return r;
        }

        r.signal = "WAIT";
        r.phase = "NOISE";
        r.wait = "Jangan entry. Tunggu struktur terbentuk.";
        r.levels = "Fake:- | Batal:- | Target:-";
        r.reason = makeReason(d, crowd, squeeze, zone, liveBias, liveMode, "WAIT", Math.max(10, liveConf - 20));
        r.confidence = Math.max(10, liveConf - 20);
        return r;
    }

    private static String safeSignal(String bias, String action) {
        if (bias == null || "SKIP".equals(bias)) return "SKIP";
        return bias + " " + action;
    }

    private static void updateTick() {
        if (!SharedState.hasPrice) {
            lastTick = "NO_PRICE";
            return;
        }

        double p = SharedState.price;

        if (Double.isNaN(lastPrice)) {
            lastPrice = p;
            lastTick = "FLAT";
            return;
        }

        double step = priceStep(p);
        double diff = p - lastPrice;

        if (diff > step * 0.15) lastTick = "UP";
        else if (diff < -step * 0.15) lastTick = "DOWN";
        else lastTick = "FLAT";

        lastPrice = p;
    }

    private static boolean tickSupports(String bias) {
        if ("NO_PRICE".equals(lastTick)) return true;
        if ("BUY".equals(bias)) return "UP".equals(lastTick) || "FLAT".equals(lastTick);
        if ("SELL".equals(bias)) return "DOWN".equals(lastTick) || "FLAT".equals(lastTick);
        return false;
    }

    private static String classifyCrowd() {
        if (!SharedState.hasCrowd) return "unknown";

        int up = SharedState.upPct;
        int down = SharedState.downPct;

        if (up >= 85) return "buy_extreme";
        if (down >= 85) return "sell_extreme";
        if (up >= 70) return "buy_heavy";
        if (down >= 70) return "sell_heavy";
        if (up >= 58) return "buy_light";
        if (down >= 58) return "sell_light";

        return "balanced";
    }

    private static String detectSqueeze(BitmapAnalyzer.Data d, String crowd) {
        boolean greenStrong = "green".equals(d.dominant) && !d.doji && d.bodyRatio >= 0.42;
        boolean redStrong = "red".equals(d.dominant) && !d.doji && d.bodyRatio >= 0.42;

        boolean tickUp = "UP".equals(lastTick) || "FLAT".equals(lastTick) || "NO_PRICE".equals(lastTick);
        boolean tickDown = "DOWN".equals(lastTick) || "FLAT".equals(lastTick) || "NO_PRICE".equals(lastTick);

        boolean noUpperReject = !"upper".equals(d.wick) || d.bodyRatio >= 0.55;
        boolean noLowerReject = !"lower".equals(d.wick) || d.bodyRatio >= 0.55;

        if (("sell_extreme".equals(crowd) || "sell_heavy".equals(crowd)) &&
                greenStrong && tickUp) {
            return "SHORT_SQUEEZE";
        }

        if (("buy_extreme".equals(crowd) || "buy_heavy".equals(crowd)) &&
                redStrong && tickDown) {
            return "LONG_SQUEEZE";
        }

        return "NONE";
    }

    private static String decideBias(BitmapAnalyzer.Data d, String crowd, String squeeze) {
        if ("SHORT_SQUEEZE".equals(squeeze)) return "BUY";
        if ("LONG_SQUEEZE".equals(squeeze)) return "SELL";

        // MHI Filter:
        // Body kuat tapi MHI berlawanan = rawan fake breakout / fake breakdown.
        // Jangan paksa entry jika MHI memberi peringatan.
        if (!d.doji && d.bodyRatio >= 0.42) {
            if ("green".equals(d.dominant) && "red".equals(d.mhi)) return "SKIP";
            if ("red".equals(d.dominant) && "green".equals(d.mhi)) return "SKIP";
        }

        if ("buy_extreme".equals(crowd) || "buy_heavy".equals(crowd)) return "SELL";
        if ("sell_extreme".equals(crowd) || "sell_heavy".equals(crowd)) return "BUY";

        if ("upper".equals(d.wick)) return "SELL";
        if ("lower".equals(d.wick)) return "BUY";

        if (!d.doji) {
            if ("green".equals(d.dominant) && d.bodyRatio >= 0.55) return "BUY";
            if ("red".equals(d.dominant) && d.bodyRatio >= 0.55) return "SELL";
            if ("green".equals(d.dominant) && "buy_light".equals(crowd)) return "SELL";
            if ("red".equals(d.dominant) && "sell_light".equals(crowd)) return "BUY";
        }

        return "SKIP";
    }

    private static String decideMode(BitmapAnalyzer.Data d, String crowd, String squeeze, String bias) {
        if ("SHORT_SQUEEZE".equals(squeeze)) return "SHORT SQUEEZE FOLLOW";
        if ("LONG_SQUEEZE".equals(squeeze)) return "LONG SQUEEZE FOLLOW";

        if ("BUY".equals(bias) && "lower".equals(d.wick)) return "REJECTION BUY / CRT BUY";
        if ("SELL".equals(bias) && "upper".equals(d.wick)) return "REJECTION SELL / CRT SELL";

        if ("BUY".equals(bias) && ("sell_extreme".equals(crowd) || "sell_heavy".equals(crowd))) {
            return "CROWD TRAP BUY";
        }

        if ("SELL".equals(bias) && ("buy_extreme".equals(crowd) || "buy_heavy".equals(crowd))) {
            return "CROWD TRAP SELL";
        }

        if ("BUY".equals(bias)) return "BUY STRUCTURE";
        if ("SELL".equals(bias)) return "SELL STRUCTURE";

        return "NO SETUP";
    }

    private static String decidePhase(BitmapAnalyzer.Data d, String crowd, String squeeze, String bias) {
        if ("SHORT_SQUEEZE".equals(squeeze)) return "SELL MAYORITAS DIHUKUM / BUY FOLLOW";
        if ("LONG_SQUEEZE".equals(squeeze)) return "BUY MAYORITAS DIHUKUM / SELL FOLLOW";

        if ("BUY".equals(bias)) {
            if ("sell_extreme".equals(crowd)) return "SELL EXTREME TRAP";
            if ("lower".equals(d.wick)) return "REJECTION BAWAH";
            if ("green".equals(d.dominant)) return "BUY MOMENTUM";
            return "NIAT BUY";
        }

        if ("SELL".equals(bias)) {
            if ("buy_extreme".equals(crowd)) return "BUY EXTREME TRAP";
            if ("upper".equals(d.wick)) return "REJECTION ATAS";
            if ("red".equals(d.dominant)) return "SELL MOMENTUM";
            return "NIAT SELL";
        }

        return "RESET / ACAK";
    }

    private static void updateDeathSpike(BitmapAnalyzer.Data d, int sec, int minute, String crowd) {
        if (sec < 55 || sec > 59) return;

        if (deathMinute != minute) {
            deathMinute = minute;
            deathSpike = "NONE";
        }

        if (sec >= 55 && sec <= 57) {
            if ("UP".equals(lastTick) || "upper".equals(d.wick) || "green".equals(d.dominant)) {
                deathSpike = "UP_55_57";
            }

            if ("DOWN".equals(lastTick) || "lower".equals(d.wick) || "red".equals(d.dominant)) {
                deathSpike = "DOWN_55_57";
            }
        }

        if (sec >= 58) {
            if ("UP_55_57".equals(deathSpike) && ("DOWN".equals(lastTick) || "upper".equals(d.wick))) {
                preparedBias = "SELL";
                preparedMode = "FAKE UP CLOSE / SELL";
                preparedPhase = "BUY TRAP AKHIR";
                preparedConfidence = Math.max(preparedConfidence, 68);
                preparedMinute = minute;
            }

            if ("DOWN_55_57".equals(deathSpike) && ("UP".equals(lastTick) || "lower".equals(d.wick))) {
                preparedBias = "BUY";
                preparedMode = "FAKE DOWN CLOSE / BUY";
                preparedPhase = "SELL TRAP AKHIR";
                preparedConfidence = Math.max(preparedConfidence, 68);
                preparedMinute = minute;
            }
        }
    }

    private static int confidence(BitmapAnalyzer.Data d, String crowd, String squeeze, String bias, String zone) {
        if ("SKIP".equals(bias)) return 25;

        int c = 40;

        if ("SHORT_SQUEEZE".equals(squeeze) || "LONG_SQUEEZE".equals(squeeze)) c += 28;

        if ("buy_extreme".equals(crowd) || "sell_extreme".equals(crowd)) c += 18;
        else if ("buy_heavy".equals(crowd) || "sell_heavy".equals(crowd)) c += 12;
        else if ("buy_light".equals(crowd) || "sell_light".equals(crowd)) c += 6;

        if ("BUY".equals(bias) && "lower".equals(d.wick)) c += 13;
        if ("SELL".equals(bias) && "upper".equals(d.wick)) c += 13;

        if ("BUY".equals(bias) && "green".equals(d.dominant) && !d.doji) c += 10;
        if ("SELL".equals(bias) && "red".equals(d.dominant) && !d.doji) c += 10;

        if ("BUY".equals(bias) && "red".equals(d.mhi)) c -= 6;
        if ("SELL".equals(bias) && "green".equals(d.mhi)) c -= 6;

        if (d.doji) c -= 18;
        if ("two_way".equals(d.wick)) c -= 8;

        if ("prepare".equals(zone)) c += 8;
        if ("execute".equals(zone)) c += 6;
        if ("death".equals(zone)) c -= 5;
        if ("stop".equals(zone)) c -= 10;
        if ("missed".equals(zone)) c -= 15;

        if (!tickSupports(bias)) c -= 10;

        if (c < 5) c = 5;
        if (c > 95) c = 95;

        return c;
    }

    private static String zoneType(int sec) {
        if (sec <= 1) return "open_noise";
        if (sec <= 4) return "execute";
        if (sec <= 15) return "late";
        if (sec <= 29) return "missed";
        if (sec <= 44) return "stop";
        if (sec <= 54) return "prepare";
        return "death";
    }

    public static String zoneName() {
        int sec = Calendar.getInstance().get(Calendar.SECOND);
        if (sec <= 1) return "OPEN NOISE";
        if (sec <= 4) return "EXECUTE";
        if (sec <= 15) return "LATE";
        if (sec <= 29) return "MISSED";
        if (sec <= 44) return "STOP";
        if (sec <= 54) return "PREPARE";
        return "DEATH";
    }

    private static String makeReason(BitmapAnalyzer.Data d, String crowd, String squeeze, String zone,
                                     String bias, String mode, String entry, int conf) {
        StringBuilder sb = new StringBuilder();

        sb.append("Mode: ").append(mode).append("\n");
        sb.append("Zona: ").append(zoneName()).append(" | Entry: ").append(entry).append("\n");

        if (SharedState.hasCrowd) {
            sb.append("Crowd: N").append(SharedState.upPct).append(" / T").append(SharedState.downPct);
        } else {
            sb.append("Crowd: kosong");
        }

        sb.append(" | Tick: ").append(lastTick).append("\n");

        if ("SHORT_SQUEEZE".equals(squeeze)) {
            sb.append("SHORT SQUEEZE: mayoritas SELL dihukum. Jangan lawan naik kuat.\n");
        } else if ("LONG_SQUEEZE".equals(squeeze)) {
            sb.append("LONG SQUEEZE: mayoritas BUY dihukum. Jangan lawan turun kuat.\n");
        } else if ("buy_extreme".equals(crowd) || "buy_heavy".equals(crowd)) {
            sb.append("BUY ramai: rawan trap SELL, kecuali squeeze naik valid.\n");
        } else if ("sell_extreme".equals(crowd) || "sell_heavy".equals(crowd)) {
            sb.append("SELL ramai: rawan trap BUY, kecuali squeeze turun valid.\n");
        } else {
            sb.append("Crowd belum ekstrem. Utamakan struktur candle.\n");
        }

        sb.append("Candle: ").append(d.dominant)
                .append(" | Wick: ").append(d.wick)
                .append(" | Body: ").append(String.format(Locale.US, "%.2f", d.bodyRatio))
                .append(" | MHI: ").append(d.mhi)
                .append("\n");

        sb.append("Bias: ").append(bias).append(" | Confidence: ").append(conf).append("%");

        return sb.toString();
    }

    private static String makeLevels(String bias) {
        if (!SharedState.hasPrice || "SKIP".equals(bias) || bias == null) {
            return "Fake:- | Batal:- | Target:-";
        }

        double p = SharedState.price;
        double st = priceStep(p);

        if ("SELL".equals(bias)) {
            String fake = fmt(p + st * 0.5) + "-" + fmt(p + st * 1.3);
            String trigger = "SELL < " + fmt(p - st * 0.4);
            String cancel = fmt(p + st * 2.0);
            String target = fmt(p - st * 1.2) + "/" + fmt(p - st * 2.2);
            return "Fake:" + fake + " | " + trigger + " | Batal:" + cancel + " | Target:" + target;
        }

        if ("BUY".equals(bias)) {
            String fake = fmt(p - st * 1.3) + "-" + fmt(p - st * 0.5);
            String trigger = "BUY > " + fmt(p + st * 0.4);
            String cancel = fmt(p - st * 2.0);
            String target = fmt(p + st * 1.2) + "/" + fmt(p + st * 2.2);
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
        return 0.00010;
    }

    private static String fmt(double x) {
        if (Math.abs(x) >= 1000) return String.format(Locale.US, "%.2f", x);
        if (Math.abs(x) >= 100) return String.format(Locale.US, "%.3f", x);
        if (Math.abs(x) >= 10) return String.format(Locale.US, "%.4f", x);
        return String.format(Locale.US, "%.5f", x);
    }
}
