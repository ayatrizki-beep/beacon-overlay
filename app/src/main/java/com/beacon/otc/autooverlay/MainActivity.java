package com.beacon.otc.autooverlay;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 101;
    private static final int REQ_CAPTURE = 102;
    private MediaProjectionManager projectionManager;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.rgb(7,7,7));

        TextView title = new TextView(this);
        title.setText("BEACON OTC AUTO OVERLAY");
        title.setTextColor(Color.rgb(255,209,92));
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setText("1) Izinkan overlay\\n2) Start Auto Capture\\n3) Buka IQ Option");
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setPadding(0, 24, 0, 24);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        Button overlay = new Button(this);
        overlay.setText("IZINKAN / START OVERLAY");
        overlay.setOnClickListener(v -> ensureOverlay());
        root.addView(overlay, new LinearLayout.LayoutParams(-1, -2));

        Button capture = new Button(this);
        capture.setText("START AUTO CAPTURE");
        capture.setOnClickListener(v -> startCaptureRequest());
        root.addView(capture, new LinearLayout.LayoutParams(-1, -2));

        Button stop = new Button(this);
        stop.setText("STOP SERVICE");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            stopService(new Intent(this, OverlayService.class));
            status.setText("Service dihentikan.");
        });
        root.addView(stop, new LinearLayout.LayoutParams(-1, -2));

        TextView note = new TextView(this);
        note.setText("\\nCatatan: ini hanya scanner/rekomendasi. Tidak auto-click, tidak auto-entry, tidak martingale.");
        note.setTextColor(Color.GRAY);
        note.setTextSize(12);
        root.addView(note, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            try { requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 99); } catch(Exception ignored) {}
        }
    }

    private void ensureOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            status.setText("Aktifkan izin 'Tampil di atas aplikasi lain', lalu kembali.");
            return;
        }
        Intent s = new Intent(this, OverlayService.class);
        startService(s);
        status.setText("Overlay aktif. Sekarang tekan START AUTO CAPTURE.");
    }

    private void startCaptureRequest() {
        ensureOverlay();
        Intent i = projectionManager.createScreenCaptureIntent();
        startActivityForResult(i, REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent s = new Intent(this, CaptureService.class);
                s.putExtra("resultCode", resultCode);
                s.putExtra("data", data);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
                status.setText("Auto capture aktif. Buka IQ Option.");
            } else {
                status.setText("Izin capture layar ditolak.");
            }
        }
    }
}
