package com.beacon.otc.autooverlay;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Menganalisa area chart yang di-crop dari screenshot layar (hasil MediaProjection)
 * untuk memperkirakan warna candle terakhir, rasio body, arah wick, doji, dan pola
 * 3-candle MHI sederhana.
 *
 * PENTING: Ini heuristik berbasis warna piksel, bukan pengenalan candle yang presisi.
 * Sesuaikan konstanta X1,X2,Y1,Y2 (area chart) dan warna target dengan tampilan
 * broker yang dipakai (default konstanta di bawah mengasumsikan candle hijau/merah
 * standar seperti IQ Option, wick lebih tipis dan lebih pucat dari body).
 */
public class BitmapAnalyzer {

    // Area chart (dalam persen dari lebar/tinggi layar) yang akan dianalisa.
    // Ubah nilai ini kalau posisi chart di HP kamu berbeda, atau kalau overlay
    // ikut tertangkap di area ini.
    public static double X1 = 0.05; // kiri
    public static double X2 = 0.95; // kanan
    public static double Y1 = 0.25; // atas
    public static double Y2 = 0.75; // bawah

    // Lebar kolom candle terakhir yang dianalisa (persen dari lebar area crop)
    private static final double LAST_CANDLE_WIDTH = 0.12;

    public static class Data {
        public String dominant = "none";   // "green" | "red" | "none" | "mixed"
        public double bodyRatio = 0.0;      // 0..1, seberapa besar body vs total area candle
        public String wick = "none";        // "upper" | "lower" | "two_way" | "none"
        public boolean doji = false;
        public String mhi = "none";         // "green" | "red" | "mixed" (3 candle terakhir)
    }

    public static Data analyze(Bitmap full) {
        Data d = new Data();
        if (full == null) return d;

        int w = full.getWidth();
        int h = full.getHeight();
        if (w <= 0 || h <= 0) return d;

        int cx1 = clamp((int) (w * X1), 0, w - 1);
        int cx2 = clamp((int) (w * X2), 1, w);
        int cy1 = clamp((int) (h * Y1), 0, h - 1);
        int cy2 = clamp((int) (h * Y2), 1, h);
        if (cx2 <= cx1 || cy2 <= cy1) return d;

        int cropW = cx2 - cx1;
        int cropH = cy2 - cy1;

        // Kolom candle terakhir = potongan paling kanan dari area chart.
        int lastW = Math.max(4, (int) (cropW * LAST_CANDLE_WIDTH));
        int lastX = cx2 - lastW;

        int greenCount = 0, redCount = 0, otherCount = 0;
        int topThirdColor = -1, bottomThirdColor = -1;
        int sampledRows = 0;

        // Sample setiap beberapa piksel biar cepat (tidak perlu per-piksel penuh).
        int stepX = Math.max(1, lastW / 12);
        int stepY = Math.max(1, cropH / 40);

        int[] rowGreen = new int[cropH / stepY + 1];
        int[] rowRed = new int[cropH / stepY + 1];
        int rowIdx = 0;

        for (int y = cy1; y < cy2; y += stepY) {
            int g = 0, r = 0, o = 0;
            for (int x = lastX; x < cx2; x += stepX) {
                int px = full.getPixel(x, y);
                String c = classify(px);
                if ("green".equals(c)) g++;
                else if ("red".equals(c)) r++;
                else o++;
            }
            rowGreen[rowIdx] = g;
            rowRed[rowIdx] = r;
            greenCount += g;
            redCount += r;
            otherCount += o;
            rowIdx++;
            sampledRows++;
        }

        int total = greenCount + redCount + otherCount;
        if (total == 0 || sampledRows == 0) return d;

        double greenFrac = greenCount / (double) total;
        double redFrac = redCount / (double) total;

        if (greenFrac < 0.06 && redFrac < 0.06) {
            // Nyaris tidak ada candle terbaca di area ini (mungkin blocked/hitam atau area salah).
            d.dominant = "none";
            return d;
        }

        d.dominant = greenFrac >= redFrac ? "green" : "red";
        double bodyColorFrac = Math.max(greenFrac, redFrac);
        d.bodyRatio = bodyColorFrac; // proporsi warna body dominan dalam kolom candle terakhir

        // Deteksi wick: bandingkan kepadatan warna body di 1/3 atas vs 1/3 bawah kolom.
        int third = Math.max(1, sampledRows / 3);
        int topG = 0, topR = 0, botG = 0, botR = 0;
        for (int i = 0; i < sampledRows; i++) {
            if (i < third) { topG += rowGreen[i]; topR += rowRed[i]; }
            else if (i >= sampledRows - third) { botG += rowGreen[i]; botR += rowRed[i]; }
        }
        int topBody = topG + topR;
        int botBody = botG + botR;
        int midThird = Math.max(1, sampledRows - 2 * third);
        int midBody = total - topBody - botBody; // kasar

        boolean thinTop = topBody < (total / (double) sampledRows) * third * 0.35;
        boolean thinBottom = botBody < (total / (double) sampledRows) * third * 0.35;

        if (thinTop && thinBottom) {
            d.wick = "two_way";
        } else if (thinTop) {
            d.wick = "upper";
        } else if (thinBottom) {
            d.wick = "lower";
        } else {
            d.wick = "none";
        }

        // Doji: body sangat tipis dibanding total kolom (warna body sedikit dibanding background/wick).
        d.doji = bodyColorFrac < 0.18;

        // MHI kasar: bandingkan 3 kolom candle berurutan sebelum candle terakhir.
        d.mhi = estimateMhi(full, cx1, cy1, cx2, cy2, lastW, stepX, stepY);

        return d;
    }

    private static String estimateMhi(Bitmap full, int cx1, int cy1, int cx2, int cy2,
                                       int colWidth, int stepX, int stepY) {
        String[] colors = new String[3];
        int endX = cx2 - colWidth; // mulai dari sebelum candle terakhir
        for (int c = 0; c < 3; c++) {
            int xStart = endX - colWidth * (c + 1);
            int xEnd = xStart + colWidth;
            if (xStart < cx1) { colors[c] = "none"; continue; }

            int g = 0, r = 0, o = 0;
            for (int y = cy1; y < cy2; y += stepY) {
                for (int x = Math.max(xStart, cx1); x < xEnd; x += stepX) {
                    String cl = classify(full.getPixel(x, y));
                    if ("green".equals(cl)) g++;
                    else if ("red".equals(cl)) r++;
                    else o++;
                }
            }
            int t = g + r + o;
            if (t == 0 || (g < t * 0.06 && r < t * 0.06)) colors[c] = "none";
            else colors[c] = g >= r ? "green" : "red";
        }

        boolean allGreen = colors[0].equals("green") && colors[1].equals("green") && colors[2].equals("green");
        boolean allRed = colors[0].equals("red") && colors[1].equals("red") && colors[2].equals("red");
        if (allGreen) return "green";
        if (allRed) return "red";
        return "mixed";
    }

    /** Klasifikasi kasar warna piksel candle: hijau (bullish) atau merah (bearish). */
    private static String classify(int px) {
        int r = Color.red(px);
        int g = Color.green(px);
        int b = Color.blue(px);

        // Hijau: kanal G dominan dan cukup jauh dari R.
        if (g > r + 25 && g > b + 10 && g > 60) return "green";
        // Merah: kanal R dominan dan cukup jauh dari G.
        if (r > g + 25 && r > b - 10 && r > 60) return "red";
        return "other";
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
