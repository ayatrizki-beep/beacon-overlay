package com.beacon.otc.autooverlay;

import android.content.Context;
import android.content.SharedPreferences;

public class TradeMemory {
    public static void log(Context ctx, String outcome, String signal) {
        SharedPreferences sp = ctx.getSharedPreferences("trade_memory", Context.MODE_PRIVATE);
        int total = sp.getInt("total", 0) + 1;
        int win = sp.getInt("win", 0);
        int loss = sp.getInt("loss", 0);
        if ("WIN".equals(outcome)) win++; else loss++;

        SharedPreferences.Editor e = sp.edit();
        e.putInt("total", total);
        e.putInt("win", win);
        e.putInt("loss", loss);
        e.apply();
    }

    public static String stats(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences("trade_memory", Context.MODE_PRIVATE);
        int total = sp.getInt("total", 0);
        int win = sp.getInt("win", 0);
        int wr = total > 0 ? (int)Math.round(win * 100.0 / total) : 0;
        return "DATA " + total + " WR " + wr + "%";
    }
}
