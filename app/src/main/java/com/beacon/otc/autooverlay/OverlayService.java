package com.beacon.otc.autooverlay;

import android.text.method.ScrollingMovementMethod;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;

public class OverlayService extends Service {
    private WindowManager wm;
    private LinearLayout root;
    private TextView signal, phase, wait, levels, reason, price, crowd, zone, stats;
    private float touchX, touchY;
    private int startX, startY;
    private BroadcastReceiver receiver;

    @Override
    public void onCreate() {
        super.onCreate();
        buildOverlay();
        registerResultReceiver();
    }

    private void startForegroundCompat() {
        String channelId = "beacon_overlay";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(channelId, "Beacon Overlay", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channelId) : new Notification.Builder(this);
        b.setContentTitle("Beacon OTC Overlay")
            .setContentText("Overlay aktif")
            .setSmallIcon(android.R.drawable.presence_online)
            .setContentIntent(pi);
        startForeground(11, b.build());
    }

    private TextView tv(String t, int size, int color, int style) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(null, style);
        v.setGravity(Gravity.CENTER);
        v.setPadding(4, 2, 4, 2);
        return v;
    }

    private Button btn(String t, int bg, int fg) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(20);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        b.setPadding(1,1,1,1);
        return b;
    }

    private void buildOverlay() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(6, 5, 6, 5);
        root.setBackgroundColor(Color.rgb(10, 10, 10));

        signal = tv("READY", 22, Color.rgb(255, 209, 92), 1);
        root.addView(signal, new LinearLayout.LayoutParams(360, 46));

        LinearLayout row0 = new LinearLayout(this);
        row0.setOrientation(LinearLayout.HORIZONTAL);
        zone = tv("ZONE", 10, Color.WHITE, 1);
        stats = tv(TradeMemory.stats(this), 10, Color.GRAY, 1);
        row0.addView(zone, new LinearLayout.LayoutParams(120, 28));
        row0.addView(stats, new LinearLayout.LayoutParams(220, 28));
        root.addView(row0);

        LinearLayout rowPrice = new LinearLayout(this);
        rowPrice.setOrientation(LinearLayout.HORIZONTAL);
        price = tv("P:-", 12, Color.WHITE, 1);
        Button m10 = btn("-10", Color.rgb(34,34,34), Color.WHITE);
        Button m5 = btn("-5", Color.rgb(34,34,34), Color.WHITE);
        Button p5 = btn("+5", Color.rgb(34,34,34), Color.WHITE);
        Button p10 = btn("+10", Color.rgb(34,34,34), Color.WHITE);
        rowPrice.addView(price, new LinearLayout.LayoutParams(100, 38));
        rowPrice.addView(m10, new LinearLayout.LayoutParams(58, 38));
        rowPrice.addView(m5, new LinearLayout.LayoutParams(58, 38));
        rowPrice.addView(p5, new LinearLayout.LayoutParams(58, 38));
        rowPrice.addView(p10, new LinearLayout.LayoutParams(58, 38));
        root.addView(rowPrice);

        m10.setOnClickListener(v -> adjustPrice(-10));
        m5.setOnClickListener(v -> adjustPrice(-5));
        p5.setOnClickListener(v -> adjustPrice(5));
        p10.setOnClickListener(v -> adjustPrice(10));
        price.setOnClickListener(v -> {
            SharedState.hasPrice = true;
            if (SharedState.price == 0) SharedState.price = 8400;
            updateManualText();
        });

        LinearLayout rowCrowd1 = new LinearLayout(this);
        rowCrowd1.setOrientation(LinearLayout.HORIZONTAL);
        Button n70 = btn("N70", Color.rgb(0,53,31), Color.rgb(0,255,136));
        Button n90 = btn("N90", Color.rgb(0,53,31), Color.rgb(0,255,136));
        Button t70 = btn("T70", Color.rgb(59,0,17), Color.rgb(255,59,95));
        Button t90 = btn("T90", Color.rgb(59,0,17), Color.rgb(255,59,95));
        rowCrowd1.addView(n70, new LinearLayout.LayoutParams(82, 38));
        rowCrowd1.addView(n90, new LinearLayout.LayoutParams(82, 38));
        rowCrowd1.addView(t70, new LinearLayout.LayoutParams(82, 38));
        rowCrowd1.addView(t90, new LinearLayout.LayoutParams(82, 38));
        root.addView(rowCrowd1);

        n70.setOnClickListener(v -> { SharedState.setCrowdUp(70); updateManualText(); });
        n90.setOnClickListener(v -> { SharedState.setCrowdUp(90); updateManualText(); });
        t70.setOnClickListener(v -> { SharedState.setCrowdDown(70); updateManualText(); });
        t90.setOnClickListener(v -> { SharedState.setCrowdDown(90); updateManualText(); });

        crowd = tv("CROWD -", 10, Color.GRAY, 1);
        root.addView(crowd, new LinearLayout.LayoutParams(340, 26));

        phase = tv("MODE:-", 10, Color.rgb(53,214,255), 1);
        phase.setGravity(Gravity.LEFT);
        root.addView(phase, new LinearLayout.LayoutParams(350, 28));

        wait = tv("TUNGGU:-", 12, Color.rgb(255,209,92), 1);
        wait.setGravity(Gravity.LEFT);
        root.addView(wait, new LinearLayout.LayoutParams(350, 30));

        levels = tv("LEVEL:-", 9, Color.WHITE, 0);
        levels.setGravity(Gravity.LEFT);
        root.addView(levels, new LinearLayout.LayoutParams(350, 52));

        reason = tv("Auto scan berjalan setelah izin capture.", 9, Color.LTGRAY, 0);
        reason.setGravity(Gravity.LEFT);
        
        reason.setGravity(Gravity.LEFT);
        reason.setSingleLine(false);
        reason.setMinLines(3);
        reason.setMaxLines(6);
        reason.setVerticalScrollBarEnabled(true);
        reason.setMovementMethod(new ScrollingMovementMethod());
root.addView(reason, new LinearLayout.LayoutParams(350, 48));

        LinearLayout rowLearn = new LinearLayout(this);
        rowLearn.setOrientation(LinearLayout.HORIZONTAL);
        Button win = btn("WIN", Color.rgb(0,53,31), Color.rgb(0,255,136));
        Button loss = btn("LOSS", Color.rgb(59,0,17), Color.rgb(255,59,95));
        Button close = btn("X", Color.rgb(40,40,40), Color.WHITE);
        rowLearn.addView(win, new LinearLayout.LayoutParams(110, 38));
        rowLearn.addView(loss, new LinearLayout.LayoutParams(110, 38));
        rowLearn.addView(close, new LinearLayout.LayoutParams(110, 38));
        root.addView(rowLearn);

        win.setOnClickListener(v -> { TradeMemory.log(this, "WIN", SharedState.lastSignal); stats.setText(TradeMemory.stats(this)); });
        loss.setOnClickListener(v -> { TradeMemory.log(this, "LOSS", SharedState.lastSignal); stats.setText(TradeMemory.stats(this)); });
        close.setOnClickListener(v -> stopSelf());

        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                620, 520,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.TOP | Gravity.LEFT;
        lp.x = 8;
        lp.y = 120;

        root.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    startX = lp.x;
                    startY = lp.y;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = startX + (int)(e.getRawX() - touchX);
                    lp.y = startY + (int)(e.getRawY() - touchY);
                    wm.updateViewLayout(root, lp);
                    return true;
            }
            return false;
        });

        wm.addView(root, lp);
        updateManualText();
    }

    private void adjustPrice(double d) {
        if (!SharedState.hasPrice) {
            SharedState.hasPrice = true;
            SharedState.price = 8400;
        }
        SharedState.price += d;
        updateManualText();
    }

    private void updateManualText() {
        price.setText("P:" + SharedState.priceText());
        crowd.setText(SharedState.crowdText());
    }

    private void registerResultReceiver() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent i) {
                if (!CaptureService.ACTION_RESULT.equals(i.getAction())) return;

                String sig = i.getStringExtra("signal");
                String ph = i.getStringExtra("phase");
                String wt = i.getStringExtra("wait");
                String lv = i.getStringExtra("levels");
                String rs = i.getStringExtra("reason");
                int cf = i.getIntExtra("confidence", 0);
                String zn = i.getStringExtra("zone");

                signal.setText(sig);
                phase.setText("MODE: " + ph + " | C" + cf + "%");
                wait.setText("TUNGGU: " + wt);
                levels.setText(lv);
                reason.setText(rs);
                zone.setText(zn);

                if (sig != null && sig.contains("BUY")) signal.setTextColor(Color.rgb(0,255,136));
                else if (sig != null && sig.contains("SELL")) signal.setTextColor(Color.rgb(255,59,95));
                else signal.setTextColor(Color.rgb(255,170,0));
            }
        };

        IntentFilter f = new IntentFilter(CaptureService.ACTION_RESULT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, f, RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, f);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (receiver != null) unregisterReceiver(receiver); } catch(Exception ignored) {}
        try { if (root != null && wm != null) wm.removeView(root); } catch(Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
