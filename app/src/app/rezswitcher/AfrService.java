package app.rezswitcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import app.rezswitcher.core.AfrController;
import app.rezswitcher.core.ModeInfo;
import app.rezswitcher.core.ModeSelector;
import app.rezswitcher.core.PauseResumeSink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class AfrService extends Service {
    static final String TAG = "RezSwitcher";
    static final String EXTRA_FORCE_MODE = "force_mode";
    private static final String CHANNEL = "afr";
    private static final long POLL_MS = 2_000;

    /** All controller access happens on this single thread. */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private OverlayModeSink sink;
    private PauseResumeSink switching;
    private AudioDeviceCallback audioCallback;
    private Display display;
    private AfrController controller;
    private LogcatFrameRateSource logcat;
    private ForegroundWatcher foreground;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "RezSwitcher", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("RezSwitcher")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build();
        startForeground(1, n);

        sink = new OverlayModeSink(this);
        display = getSystemService(DisplayManager.class).getDisplay(Display.DEFAULT_DISPLAY);
        switching = new PauseResumeSink(sink, new MediaSessionControl(this),
                new DisplayChangeCheck(display), (task, delayMs) -> {
            ScheduledFuture<?> future = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        });
        controller = new AfrController(switching, this::resolveMode, Collections.<String>emptySet());

        audioCallback = new AudioDeviceCallback() {
            @Override
            public void onAudioDevicesAdded(AudioDeviceInfo[] added) {
                for (AudioDeviceInfo device : added) {
                    if (device.isSink() && device.getType() == AudioDeviceInfo.TYPE_HDMI) {
                        Log.i(TAG, "hdmi audio added");
                        executor.execute(switching::onHdmiAudioReconnected);
                        return;
                    }
                }
            }
        };
        getSystemService(AudioManager.class).registerAudioDeviceCallback(audioCallback, null);

        logcat = new LogcatFrameRateSource(fps -> executor.execute(() -> {
            Log.i(TAG, "frame rate " + fps);
            controller.onFrameRate(fps, SystemClock.elapsedRealtime());
        }));
        foreground = new ForegroundWatcher(this, pkg -> controller.onForegroundChanged(pkg, SystemClock.elapsedRealtime()));

        executor.scheduleWithFixedDelay(() -> {
            try {
                foreground.poll();
                controller.onTick(SystemClock.elapsedRealtime());
            } catch (RuntimeException e) {
                Log.w(TAG, "poll failed", e);
            }
        }, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
        logcat.start();
        Log.i(TAG, "started; " + display.getSupportedModes().length + " modes");
    }

    /**
     * Mode ids are re-read on every call: the Shield renumbers its modes after an HDMI
     * renegotiation (observed 2026-10-08: id 11 changed from 4K 25 Hz to 4K 24 Hz).
     */
    private int resolveMode(double fps) {
        List<ModeInfo> modes = new ArrayList<>();
        int width = 0, height = 0;
        for (Display.Mode m : display.getSupportedModes()) {
            modes.add(new ModeInfo(m.getModeId(), m.getPhysicalWidth(), m.getPhysicalHeight(), m.getRefreshRate()));
            if (m.getPhysicalWidth() * m.getPhysicalHeight() > width * height) {
                width = m.getPhysicalWidth();
                height = m.getPhysicalHeight();
            }
        }
        int mode = ModeSelector.selectModeId(modes, width, height, fps);
        Log.i(TAG, "fps " + fps + " -> mode " + mode + " at " + width + "x" + height);
        return mode;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra(EXTRA_FORCE_MODE)) {
            int mode = intent.getIntExtra(EXTRA_FORCE_MODE, 0);
            Log.i(TAG, "force_mode=" + mode);
            executor.execute(() -> {
                if (mode == 0) controller.clearForce(); else controller.forceMode(mode);
            });
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        getSystemService(AudioManager.class).unregisterAudioDeviceCallback(audioCallback);
        logcat.stop();
        try {
            executor.submit(switching::shutdown).get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            Log.w(TAG, "orderly shutdown failed; clearing overlay directly", e);
            sink.clearMode();
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
