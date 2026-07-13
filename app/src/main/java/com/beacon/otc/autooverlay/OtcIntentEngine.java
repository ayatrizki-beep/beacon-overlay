package com.beacon.otc.autooverlay;

import java.util.Calendar;
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

    private static String preparedBias = "SKIP";
    private static String preparedPhase = "-";
    private static int preparedConfidence = 0;
    private static int preparedMinute = -1;

    private static double lastPrice = Double.NaN;
    private static String lastTick = "FLAT";
    private static int lastTickSec = -1;

    private static String deathSpike = "NONE";
    private static int deathSpikeMinute = -1;

    public static Result decide(BitmapAnalyzer.Data d) {
        Result r = new Result();

        Calendar cal = Calendar.getInstance();
        int sec = cal.get(Calendar.SECOND);
        int minute = cal.get(Calendar.MINUTE);

        updateTick(sec);

        String zone = zoneType(sec);
        String crowd = classifyCrowd();
        String liveBias = decideBias(d, crowd);
        String livePhase = decidePhase(d, crowd, liveBias);
        int liveConf = confidence(crowd, d, zone, liveBias);

        updateDeathSpike(d, sec, minute);

        if ("open_noise".equals(zone)) {
            r.signal = "TUNGGU 02";
            r.phase = "OPEN NOISE";
            r.wait = "00-01 jangan klik. Tunggu detik 02-04 jika setup fresh.";
            r.levels = makeLevels(preparedBias);
            r.reason = "Open noise. Candle baru belum stabil. | Tick:" + lastTick +
                    " | " + makeReason(d, crowd, zone, preparedBias, "OPEN_NOISE", Math.max(10, liveConf - 20));
            r.confidence = Math.max(10, liveConf - 20);
            return r;
        }

        if ("prepare".equals(zone)) {
            if (!"SKIP".equals(liveBias) && liveConf >= 50) {
                preparedBias = liveBias;
                preparedPhase = livePhase;
                preparedConfidence = liveConf;
                preparedMinute = minute;
            }

            r.signal = preparedBias + " SIAPKAN";
            r.phase = "VALIDASI ARAH / GOLD";
            r.wait = "ARAH SAJA. Jangan klik. Entry 1M hanya candle baru 02-04.";
            r.levels = makeLevels(preparedBias);
            r.reason = "45-54 adalah zona baca arah, bukan klik. " +
                    makeReason(d, crowd, zone, preparedBias, "PREPARE", preparedConfidence);
            r.confidence = preparedConfidence;
            return r;
        }

        if ("death".equals(zone)) {
            if (!"SKIP".equals(liveBias) && liveConf >= preparedConfidence) {
                preparedBias = liveBias;
                preparedPhase = livePhase;
                preparedConfidence = liveConf;
                preparedMinute = minute;
            }

            String spikeInfo = deathSpike;
            r.signal = preparedBias + " TUNGGU CANDLE BARU";
            r.phase = "DEATH ZONE / FAKE SPIKE WATCH";
            r.wait = "Jangan klik 55-59. Tunggu candle baru 02-04 untuk 1M.";
            r.levels = makeLevels(preparedBias);
            r.reason = "55-59 rawan fake spike. Spike:" + spikeInfo + " | " +
                    makeReason(d, crowd, zone, preparedBias, "WAIT NEXT", preparedConfidence);
            r.confidence = Math.max(20, preparedConfidence - 8);
            return r;
        }

        if ("execute".equals(zone)) {
            boolean hasFreshPrepare = preparedMinute >= 0 && preparedMinute != minute && !"SKIP".equals(preparedBias);
            boolean tickOk = tickSupports(preparedBias);

            if (hasFreshPrepare && preparedConfidence >= 58 && tickOk) {
                r.signal = preparedBias + " 1M SEKARANG";
                r.phase = "EXECUTION WINDOW";
                r.wait = "KLIK SEKARANG. Expiry 1M. Valid hanya 02-04.";
                r.levels = makeLevels(preparedBias);
                r.reason = "Entry valid: confidence >=70, candle baru 02-04, tick mendukung. " +
                        "Tick:" + lastTick + " | " +
                        makeReason(d, crowd, zone, preparedBias, "NOW", preparedConfidence);
                r.confidence = Math.min(95, preparedConfidence + 7);
                return r;
            }

            if (hasFreshPrepare && preparedConfidence >= 50 && !tickOk) {
                r.signal = preparedBias + " 1M TUNGGU TICK";
                r.phase = "EXECUTION WINDOW";
                r.wait = "Arah ada, tapi tick belum mendukung. Confidence cukup, tapi lewat detik 04 = batal 1M.";
                r.levels = makeLevels(preparedBias);
                r.reason = "Jangan klik kalau tick berlawanan. Tick:" + lastTick + " | " +
                        makeReason(d, crowd, zone, preparedBias, "WAIT TICK", preparedConfidence);
                r.confidence = preparedConfidence;
                return r;
            }

            r.signal = "SKIP";
            r.phase = "NO FRESH SETUP";
            r.wait = "Tidak ada prepare valid dari candle sebelumnya.";
            r.levels = "Fake:- | Batal:- | Target:-";
            r.reason = "Robot belum punya arah valid dari zona 45-59 sebelumnya.";
            r.confidence = 25;
            return r;
        }

        if ("late".equals(zone)) {
            if (!"SKIP".equals(preparedBias)) {
                r.signal = preparedBias + " TELAT 1M";
                r.phase = "LATE 1M";
                r.wait = "TELAT untuk 1M. Jangan kejar. 1M30/2M wajib TF 5M dan 10M searah.";
                r.levels = makeLevels(preparedBias);
                r.reason = "02-04 sudah lewat. Untuk 1M ini terlambat. Tick:" + lastTick +
                        " | MTF manual wajib cek 10M -> 5M -> 2M.";
                r.confidence = Math.max(20, preparedConfidence - 10);
                return r;
            }
        }

        if ("missed".equals(zone)) {
            String bias = "SKIP".equals(liveBias) ? preparedBias : liveBias;

            if (!"SKIP".equals(bias)) {
                r.signal = bias + " LEWAT 1M";
                r.phase = "EXPIRY GUARD";
                r.wait = "LEWAT untuk 1M. Jangan entry. Tunggu setup berikutnya.";
                r.levels = makeLevels(bias);
                r.reason = "Lewat 15 detik = 1M tidak ideal. Kalau 1M30/2M, hanya boleh jika 10M dan 5M mendukung. Tick:" + lastTick;
                r.confidence = Math.max(15, liveConf - 15);
                return r;
            }
        }

        if ("stop".equals(zone)) {
            String bias = "SKIP".equals(liveBias) ? preparedBias : liveBias;

            r.signal = "SKIP";
            r.phase = "STOP HUNT";
            r.wait = "SKIP 1M. Area 30-44 sering pancingan. 1M30/5M hanya jika TF besar mendukung.";
            r.levels = makeLevels(bias);
            r.reason = "30-44 bukan zona klik. Baca arah saja. Bias sementara:" + bias +
                    " | Tick:" + lastTick + " | " +
                    makeReason(d, crowd, zone, bias, "SKIP", liveConf);
            r.confidence = Math.max(15, liveConf - 20);
            return r;
        }

        r.signal = "WAIT";
        r.phase = "NOISE";
        r.wait = "05-15 telat untuk 1M. Untuk 1M30/5M wajib tunggu konfirmasi kuat.";
        r.levels = "Fake:- | Batal:- | Target:-";
        r.reason = "Expiry selector: 1M tidak valid tanpa setup fresh. Tick:" + lastTick + " | " +
                makeReason(d, crowd, zone, liveBias, "WAIT", liveConf);
        r.confidence = Math.max(10, liveConf - 25);
        return r;
    }

    private static void updateTick(int sec) {
        if (!SharedState.hasPrice) {
            lastTick = "NO_PRICE";
            return;
        }

        double p = SharedState.price;

        if (Double.isNaN(lastPrice)) {
            lastPrice = p;
            lastTick = "FLAT";
            lastTickSec = sec;
            return;
        }

        double step = priceStep(p);
        double diff = p - lastPrice;

        if (diff > step * 0.15) lastTick = "UP";
        else if (diff < -step * 0.15) lastTick = "DOWN";
        else lastTick = "FLAT";

        lastPrice = p;
        lastTickSec = sec;
    }

    private static boolean tickSupports(String bias) {
        if ("BUY".equals(bias)) return "UP".equals(lastTick) || "FLAT".equals(lastTick);
        if ("SELL".equals(bias)) return "DOWN".equals(lastTick) || "FLAT".equals(lastTick);
        return false;
    }

    private static void updateDeathSpike(BitmapAnalyzer.Data d, int sec, int minute) {
        if (sec < 55 || sec > 59) return;

        if (deathSpikeMinute != minute) {
            deathSpike = "NONE";
            deathSpikeMinute = minute;
        }

        // 55-57 = baca spike awal death zone.
        // Jangan langsung entry. Simpan arah spike untuk validasi 58-59.
        if (sec >= 55 && sec <= 57) {
            if ("UP".equals(lastTick) || "upper".equals(d.wick) || "green".equals(d.dominant)) {
                deathSpike = "UP_55_57";
            }

            if ("DOWN".equals(lastTick) || "lower".equals(d.wick) || "red".equals(d.dominant)) {
                deathSpike = "DOWN_55_57";
            }
        }

        // 58-59 = validasi akhir:
        // - kalau spike dibalik oleh wick/tick lawan = trap close
        // - kalau spike lanjut searah tanpa rejection = continuation
        if (sec >= 58) {
            if ("UP_55_57".equals(deathSpike)) {
                if ("DOWN".equals(lastTick) || "upper".equals(d.wick)) {
                    preparedBias = "SELL";
                    preparedPhase = "BUY TRAP CLOSE";
                    preparedConfidence = Math.max(preparedConfidence, 72);
                    preparedMinute = minute;
                    return;
                }

                if (("UP".equals(lastTick) || "green".equals(d.dominant)) && !"upper".equals(d.wick)) {
                    preparedBias = "BUY";
                    preparedPhase = "BUY CONTINUATION";
                    preparedConfidence = Math.max(preparedConfidence, 68);
                    preparedMinute = minute;
                    return;
                }
            }

            if ("DOWN_55_57".equals(deathSpike)) {
                if ("UP".equals(lastTick) || "lower".equals(d.wick)) {
                    preparedBias = "BUY";
                    preparedPhase = "SELL TRAP CLOSE";
                    preparedConfidence = Math.max(preparedConfidence, 72);
                    preparedMinute = minute;
                    return;
                }

                if (("DOWN".equals(lastTick) || "red".equals(d.dominant)) && !"lower".equals(d.wick)) {
                    preparedBias = "SELL";
                    preparedPhase = "SELL CONTINUATION";
                    preparedConfidence = Math.max(preparedConfidence, 68);
                    preparedMinute = minute;
                    return;
                }
            }
        }
    }

    private static String classifyCrowd() {
        if (!SharedState.hasCrowd) return "unknown";
        if (SharedState.upPct >= 85) return "buy_extreme";
        if (SharedState.downPct >= 85) return "sell_extreme";
        if (SharedState.upPct >= 65) return "buy_heavy";
        if (SharedState.downPct >= 65) return "sell_heavy";
        if (SharedState.upPct >= 58) return "buy_light";
        if (SharedState.downPct >= 58) return "sell_light";
        return "balanced";
    }

    private static String decideBias(BitmapAnalyzer.Data d, String crowd) {
        // 1) Crowd ekstrem tetap dibaca sebagai potensi squeeze/kontra crowd,
        // tapi jangan melawan wick trap yang jelas.
        if ("upper".equals(d.wick)) return "SELL"; // BUY trap / rejection atas
        if ("lower".equals(d.wick)) return "BUY";  // SELL trap / recovery bawah

        if ("buy_extreme".equals(crowd) || "buy_heavy".equals(crowd)) {
            if ("red".equals(d.dominant) || "mixed".equals(d.mhi)) return "SELL";
        }

        if ("sell_extreme".equals(crowd) || "sell_heavy".equals(crowd)) {
            if ("green".equals(d.dominant) || "mixed".equals(d.mhi)) return "BUY";
        }

        // 2) Doji / mixed tidak cukup untuk entry.
        if (d.doji || "mixed".equals(d.dominant) || "none".equals(d.dominant)) {
            return "SKIP";
        }

        // 3) Candle warna kuat boleh follow arah, bukan dibalik buta.
        // Ini koreksi besar dari engine lama.
        if ("green".equals(d.dominant) && d.bodyRatio >= 0.50) {
            if ("red".equals(d.mhi)) return "SKIP"; // MHI berlawanan, rawan fake
            return "BUY";
        }

        if ("red".equals(d.dominant) && d.bodyRatio >= 0.50) {
            if ("green".equals(d.mhi)) return "SKIP"; // MHI berlawanan, rawan fake
            return "SELL";
        }

        // 4) Body sedang: ikut arah hanya kalau MHI mendukung.
        if ("green".equals(d.dominant) && "green".equals(d.mhi) && d.bodyRatio >= 0.35) {
            return "BUY";
        }

        if ("red".equals(d.dominant) && "red".equals(d.mhi) && d.bodyRatio >= 0.35) {
            return "SELL";
        }

        return "SKIP";
    }

    private static String decidePhase(BitmapAnalyzer.Data d, String crowd, String bias) {
        if ("SKIP".equals(bias)) return "RESET / ACAK";

        if ("SELL".equals(bias)) {
            if ("buy_extreme".equals(crowd)) return "BUY TRAP EKSTREM";
            if ("buy_heavy".equals(crowd)) return "PANCING BUY";
            if ("upper".equals(d.wick)) return "UPPER WICK REJECTION";
            if ("green".equals(d.dominant)) return "BUY TRAP / UPPER WICK";
            if ("red".equals(d.dominant)) return "BUY TRAP CONFIRM";
            return "NIAT SELL";
        }

        if ("BUY".equals(bias)) {
            if ("sell_extreme".equals(crowd)) return "SELL TRAP EKSTREM";
            if ("sell_heavy".equals(crowd)) return "PANCING SELL";
            if ("lower".equals(d.wick)) return "LOWER WICK RECOVERY";
            if ("red".equals(d.dominant)) return "SELL TRAP / LOWER WICK";
            if ("green".equals(d.dominant)) return "SELL TRAP CONFIRM";
            return "NIAT BUY";
        }

        return "RESET / ACAK";
    }

    private static boolean candleFollowsBias(BitmapAnalyzer.Data d, String bias) {
        if ("BUY".equals(bias)) return "green".equals(d.dominant) && !d.doji;
        if ("SELL".equals(bias)) return "red".equals(d.dominant) && !d.doji;
        return false;
    }

    private static boolean wickFollowsBias(BitmapAnalyzer.Data d, String bias) {
        if ("BUY".equals(bias)) return "lower".equals(d.wick);
        if ("SELL".equals(bias)) return "upper".equals(d.wick);
        return false;
    }

    private static boolean mhiAgainstBias(BitmapAnalyzer.Data d, String bias) {
        if ("BUY".equals(bias)) return "red".equals(d.mhi);
        if ("SELL".equals(bias)) return "green".equals(d.mhi);
        return false;
    }

    private static boolean trapDone(BitmapAnalyzer.Data d, String crowd, String bias) {
        if ("SELL".equals(bias)) {
            return ("buy_extreme".equals(crowd) || "buy_heavy".equals(crowd)) &&
                    ("red".equals(d.dominant) || "upper".equals(d.wick));
        }

        if ("BUY".equals(bias)) {
            return ("sell_extreme".equals(crowd) || "sell_heavy".equals(crowd)) &&
                    ("green".equals(d.dominant) || "lower".equals(d.wick));
        }

        return false;
    }

    private static int confidence(String crowd, BitmapAnalyzer.Data d, String zone, String bias) {
        if ("SKIP".equals(bias)) return 20;

        // HARD FILTER: kondisi visual belum layak untuk entry presisi.
        if (d.doji) return 22;
        if ("mixed".equals(d.dominant) || "none".equals(d.dominant)) return 24;
        if ("two_way".equals(d.wick)) return 26;
        if (mhiAgainstBias(d, bias)) return 32;

        int c = 38;

        // Crowd hanya menambah confidence, bukan penentu tunggal.
        if ("buy_extreme".equals(crowd) || "sell_extreme".equals(crowd)) c += 22;
        else if ("buy_heavy".equals(crowd) || "sell_heavy".equals(crowd)) c += 15;
        else if ("buy_light".equals(crowd) || "sell_light".equals(crowd)) c += 6;

        // Wick searah bias adalah nilai paling penting untuk trap/recovery.
        if (wickFollowsBias(d, bias)) c += 18;
        else {
            if ("BUY".equals(bias) && "upper".equals(d.wick)) c -= 18;
            if ("SELL".equals(bias) && "lower".equals(d.wick)) c -= 18;
        }

        // Candle body harus mendukung arah, tapi tidak boleh mengalahkan wick.
        if (candleFollowsBias(d, bias)) c += 12;
        else if ("green".equals(d.dominant) || "red".equals(d.dominant)) c += 2;

        // Body kuat tambah sedikit confidence.
        if (d.bodyRatio >= 0.50) c += 8;
        else if (d.bodyRatio >= 0.35) c += 4;
        else c -= 8;

        // Trap done menambah validasi, tapi tetap kalah oleh hard filter di atas.
        if (trapDone(d, crowd, bias)) c += 12;

        // Zona waktu.
        if ("prepare".equals(zone)) c += 6;
        if ("death".equals(zone)) c -= 3;
        if ("execute".equals(zone)) c += 8;
        if ("late".equals(zone)) c -= 12;
        if ("stop".equals(zone)) c -= 15;
        if ("missed".equals(zone)) c -= 20;

        if (c < 5) c = 5;
        if (c > 95) c = 95;
        return c;
    }

    private static String makeReason(BitmapAnalyzer.Data d, String crowd, String zone, String bias, String entry, int conf) {
        StringBuilder sb = new StringBuilder();

        if (SharedState.hasCrowd) {
            sb.append("Crowd N").append(SharedState.upPct).append("/T").append(SharedState.downPct);
        } else {
            sb.append("Crowd kosong");
        }

        sb.append(" | ");

        if ("buy_extreme".equals(crowd)) sb.append("mayoritas BUY ekstrem -> waspada SELL trap");
        else if ("sell_extreme".equals(crowd)) sb.append("mayoritas SELL ekstrem -> waspada BUY trap");
        else if ("buy_heavy".equals(crowd)) sb.append("BUY ramai -> rawan buy trap");
        else if ("sell_heavy".equals(crowd)) sb.append("SELL ramai -> rawan sell trap");
        else sb.append("crowd belum ekstrem");

        sb.append(" | candle:").append(d.dominant);
        sb.append(" wick:").append(d.wick);
        sb.append(" body:").append(String.format(Locale.US, "%.2f", d.bodyRatio));
        sb.append(" MHI:").append(d.mhi);
        sb.append(" | bias:").append(bias);
        sb.append(" entry:").append(entry);
        sb.append(" | zone:").append(zoneName());
        sb.append(" | C").append(conf).append("%");

        return sb.toString();
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
        if (sec <= 5) return "EXECUTE";
        if (sec <= 15) return "LATE";
        if (sec <= 29) return "MISSED";
        if (sec <= 44) return "STOP";
        if (sec <= 54) return "PREPARE";
        return "DEATH";
    }

    private static String makeLevels(String bias) {
        if (!SharedState.hasPrice || "SKIP".equals(bias) || "WAIT".equals(bias)) {
            return "Fake:- | Batal:- | Target:-";
        }

        double p = SharedState.price;
        double st = priceStep(p);

        if ("SELL".equals(bias)) {
            String fake = fmt(p + st * 0.5) + "-" + fmt(p + st * 1.3);
            String trigger = "SELL bawah " + fmt(p - st * 0.4);
            String cancel = fmt(p + st * 2.0);
            String target = fmt(p - st * 1.2) + "/" + fmt(p - st * 2.2) + "/" + fmt(p - st * 3.4);
            return "Fake:" + fake + " | " + trigger + " | Batal:" + cancel + " | Target:" + target;
        }

        if ("BUY".equals(bias)) {
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
        return 0.00010;
    }

    private static String fmt(double x) {
        if (Math.abs(x) >= 1000) return String.format(Locale.US, "%.2f", x);
        if (Math.abs(x) >= 100) return String.format(Locale.US, "%.3f", x);
        if (Math.abs(x) >= 10) return String.format(Locale.US, "%.4f", x);
        return String.format(Locale.US, "%.5f", x);
    }
}
