package app.rezswitcher.core;

/**
 * Wraps a ModeSink so every display change happens with playback paused: pause playing media,
 * wait PAUSE_LEAD_MS, change mode, wait for the HDMI audio output to be re-added (falling back
 * to MAX_SETTLE_MS), wait REPLUG_GRACE_MS, resume. Requests that leave the display unchanged
 * are applied at once, with no pause. Not thread-safe: call it and run scheduled tasks on one thread.
 */
public final class PauseResumeSink implements AfrController.ModeSink {
    /** Pauses and resumes whatever media sessions are playing. */
    public interface MediaControl {
        /** Pauses every playing session and remembers it; returns true if anything was paused. */
        boolean pausePlaying();

        /** Sends play to every remembered session that is not playing, then forgets them. */
        void resumePaused();
    }

    /** Says whether a request (modeId) or release (0) would actually change the display. */
    public interface DisplayState {
        boolean willChangeDisplay(int modeId);
    }

    public interface Scheduler {
        Cancellable schedule(Runnable task, long delayMs);
    }

    public interface Cancellable {
        void cancel();
    }

    public static final long PAUSE_LEAD_MS = 500;
    public static final long REPLUG_GRACE_MS = 500;
    public static final long MAX_SETTLE_MS = 8_000;

    private final AfrController.ModeSink inner;
    private final MediaControl media;
    private final DisplayState display;
    private final Scheduler scheduler;

    private boolean holdingPause;
    /** A display change has been applied while holding the pause; its re-plug is still due. */
    private boolean switchApplied;
    private boolean awaitingReplug;
    private Cancellable pendingSwitch;
    private Cancellable pendingResume;

    public PauseResumeSink(AfrController.ModeSink inner, MediaControl media, DisplayState display, Scheduler scheduler) {
        this.inner = inner;
        this.media = media;
        this.display = display;
        this.scheduler = scheduler;
    }

    @Override
    public void requestMode(int modeId) {
        switchVia(display.willChangeDisplay(modeId), () -> inner.requestMode(modeId));
    }

    @Override
    public void clearMode() {
        switchVia(display.willChangeDisplay(0), inner::clearMode);
    }

    /** HDMI audio output was (re-)added. Resumes shortly after, if a switch is waiting for it. */
    public void onHdmiAudioReconnected() {
        if (!awaitingReplug) return;
        awaitingReplug = false;
        if (pendingResume != null) pendingResume.cancel();
        pendingResume = scheduler.schedule(this::resume, REPLUG_GRACE_MS);
    }

    /** Orderly teardown: cancel pending work, release the overlay, resume anything still held paused. */
    public void shutdown() {
        cancelPending();
        inner.clearMode();
        if (holdingPause) {
            holdingPause = false;
            switchApplied = false;
            media.resumePaused();
        }
    }

    private void switchVia(boolean displayChanges, Runnable change) {
        cancelPending();
        if (!displayChanges) {
            change.run();
            if (holdingPause) {
                if (switchApplied) awaitReplug();
                else pendingResume = scheduler.schedule(this::resume, REPLUG_GRACE_MS);
            }
            return;
        }
        if (media.pausePlaying()) holdingPause = true;
        if (!holdingPause) {
            change.run();
            return;
        }
        pendingSwitch = scheduler.schedule(() -> {
            pendingSwitch = null;
            change.run();
            switchApplied = true;
            awaitReplug();
        }, PAUSE_LEAD_MS);
    }

    private void awaitReplug() {
        awaitingReplug = true;
        pendingResume = scheduler.schedule(this::resume, MAX_SETTLE_MS);
    }

    private void resume() {
        pendingResume = null;
        awaitingReplug = false;
        holdingPause = false;
        switchApplied = false;
        media.resumePaused();
    }

    private void cancelPending() {
        if (pendingSwitch != null) {
            pendingSwitch.cancel();
            pendingSwitch = null;
        }
        if (pendingResume != null) {
            pendingResume.cancel();
            pendingResume = null;
        }
        awaitingReplug = false;
    }
}
