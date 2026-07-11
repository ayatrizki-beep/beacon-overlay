package com.beacon.otc.autooverlay;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;

public class CaptureService extends Service {
    public static final String ACTION_RESULT = "com.beacon.otc.autooverlay.RESULT";

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Handler handler;
    private HandlerThread thread;
    private int width, height, density;

    private final Runnable scanLoop = new Runnable() {
        @Override
        public void run() {
            try {
                Bitmap b = getLatestBitmap();
                if (b != null) {
                    BitmapAnalyzer.Data data = BitmapAnalyzer.analyze(b);
                    OtcIntentEngine.Result result = OtcIntentEngine.decide(data);

                    SharedState.lastSignal = result.signal;
                    SharedState.lastPhase = result.phase;
                    SharedState.lastWait = result.wait;
                    SharedState.lastLevels = result.levels;
                    SharedState.lastReason = result.reason;
                    SharedState.lastConfidence = result.confidence;

                    Intent i = new Intent(ACTION_RESULT);
                    i.putExtra("signal", result.signal);
                    i.putExtra("phase", result.phase);
                    i.putExtra("wait", result.wait);
                    i.putExtra("levels", result.levels);
                    i.putExtra("reason", result.reason);
                    i.putExtra("confidence", result.confidence);
                    i.putExtra("zone", OtcIntentEngine.zoneName());
                    sendBroadcast(i);
                }
            } catch(Exception ignored) {}

            if (handler != null) handler.postDelayed(this, 1000);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat();

        if (intent == null || !intent.hasExtra("resultCode")) return START_STICKY;

        int resultCode = intent.getIntExtra("resultCode", -1);
        Intent data = intent.getParcelableExtra("data");

        MediaProjectionManager mgr = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        projection = mgr.getMediaProjection(resultCode, data);

        setupCapture();
        return START_STICKY;
    }

    private void startForegroundCompat() {
        String channelId = "beacon_capture";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(channelId, "Beacon Capture", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 2, open, Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channelId) : new Notification.Builder(this);
        b.setContentTitle("Beacon OTC Auto Capture")
            .setContentText("Membaca layar tiap 1 detik")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pi);
        startForeground(22, b.build());
    }

    private void setupCapture() {
        releaseCapture();

        DisplayMetrics dm = getResources().getDisplayMetrics();
        width = dm.widthPixels;
        height = dm.heightPixels;
        density = dm.densityDpi;

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

        if (projection == null) return;

        virtualDisplay = projection.createVirtualDisplay(
                "BeaconScreen",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, null
        );

        thread = new HandlerThread("BeaconScanThread");
        thread.start();
        handler = new Handler(thread.getLooper());
        handler.postDelayed(scanLoop, 1000);
    }

    private Bitmap getLatestBitmap() {
        if (imageReader == null) return null;

        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) return null;

            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            Bitmap cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            bitmap.recycle();
            return cropped;
        } catch(Exception e) {
            return null;
        } finally {
            if (image != null) image.close();
        }
    }

    private void releaseCapture() {
        try { if (handler != null) handler.removeCallbacksAndMessages(null); } catch(Exception ignored) {}
        try { if (thread != null) thread.quitSafely(); } catch(Exception ignored) {}
        try { if (virtualDisplay != null) virtualDisplay.release(); } catch(Exception ignored) {}
        try { if (imageReader != null) imageReader.close(); } catch(Exception ignored) {}
        virtualDisplay = null;
        imageReader = null;
        handler = null;
        thread = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseCapture();
        try { if (projection != null) projection.stop(); } catch(Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
