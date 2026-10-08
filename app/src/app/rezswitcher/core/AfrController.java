package app.rezswitcher.core;

import java.util.Set;

/**
 * Decides which display mode to request from decoder frame-rate readings,
 * foreground-app changes and the passage of time. Not thread-safe: call from one thread.
 */
public final class AfrController {
    /** Receives mode decisions. modeId is a Display.Mode id; clearMode returns control to the system default. */
    public interface ModeSink {
        void requestMode(int modeId);
        void clearMode();
    }

    /** Maps a content frame rate to a display mode id, or 0 for none. */
    public interface ModeResolver {
        int modeFor(double fps);
    }

    public static final long IDLE_TIMEOUT_MS = 60_000;
    private static final int READINGS_TO_CHANGE = 2;

    private final ModeSink sink;
    private final ModeResolver resolver;
    private final Set<String> excludedPackages;

    private String foreground;
    private boolean forced;
    private int activeMode;
    private int pendingMode;
    private int pendingCount;
    private long lastReadingMs = -1;

    public AfrController(ModeSink sink, ModeResolver resolver, Set<String> excludedPackages) {
        this.sink = sink;
        this.resolver = resolver;
        this.excludedPackages = excludedPackages;
    }

    public void onFrameRate(double fps, long nowMs) {
        lastReadingMs = nowMs;
        if (forced) return;
        if (foreground != null && excludedPackages.contains(foreground)) return;
        int mode = resolver.modeFor(fps);
        if (mode == 0 || mode == activeMode) {
            resetPending();
            return;
        }
        if (activeMode == 0) {
            apply(mode);
            return;
        }
        if (mode == pendingMode) {
            pendingCount++;
        } else {
            pendingMode = mode;
            pendingCount = 1;
        }
        if (pendingCount >= READINGS_TO_CHANGE) apply(mode);
    }

    public void onForegroundChanged(String pkg, long nowMs) {
        if (pkg == null || pkg.equals(foreground)) return;
        foreground = pkg;
        if (!forced) clear();
    }

    public void onTick(long nowMs) {
        if (!forced && activeMode != 0 && lastReadingMs >= 0 && nowMs - lastReadingMs >= IDLE_TIMEOUT_MS) clear();
    }

    /** Debug override: request modeId and ignore readings, app changes and idle until clearForce(). */
    public void forceMode(int modeId) {
        forced = true;
        apply(modeId);
    }

    /** Ends a debug override: releases the mode; automatic switching resumes from the next reading. */
    public void clearForce() {
        if (!forced) return;
        forced = false;
        clear();
    }

    public int activeMode() {
        return activeMode;
    }

    private void apply(int mode) {
        activeMode = mode;
        resetPending();
        sink.requestMode(mode);
    }

    private void clear() {
        resetPending();
        if (activeMode != 0) {
            activeMode = 0;
            sink.clearMode();
        }
    }

    private void resetPending() {
        pendingMode = 0;
        pendingCount = 0;
    }
}
