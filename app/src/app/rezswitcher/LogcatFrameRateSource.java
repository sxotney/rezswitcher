package app.rezswitcher;

import android.util.Log;

import app.rezswitcher.core.FrameRateParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Streams the NVIDIA decoder's frame-rate log lines. Needs READ_LOGS granted over adb. Restarts logcat if it exits. */
final class LogcatFrameRateSource {
    interface Listener {
        void onFrameRate(double fps);
    }

    private static final String[] CMD = {"logcat", "-v", "brief", "-T", "1", "NvOsDebugPrintf:D", "*:S"};
    private static final long RESTART_DELAY_MS = 5_000;

    private final Listener listener;
    private volatile boolean running;
    private volatile Process process;
    private Thread thread;

    LogcatFrameRateSource(Listener listener) {
        this.listener = listener;
    }

    void start() {
        running = true;
        thread = new Thread(this::loop, "afr-logcat");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        Process p = process;
        if (p != null) p.destroy();
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running) {
            try {
                process = Runtime.getRuntime().exec(CMD);
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while (running && (line = r.readLine()) != null) {
                        double fps = FrameRateParser.parse(line);
                        if (fps > 0) listener.onFrameRate(fps);
                    }
                }
            } catch (Exception e) {
                Log.w(AfrService.TAG, "logcat reader failed", e);
            } finally {
                Process p = process;
                if (p != null) p.destroy();
            }
            if (running) {
                try {
                    Thread.sleep(RESTART_DELAY_MS);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
}
