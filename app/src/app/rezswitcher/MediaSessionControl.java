package app.rezswitcher;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import app.rezswitcher.core.PauseResumeSink;
import app.rezswitcher.core.ResumePolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pauses/resumes media sessions by package. Needs the notification listener enabled over adb. */
final class MediaSessionControl implements PauseResumeSink.MediaControl {
    private final MediaSessionManager manager;
    private final AudioManager audio;
    private final ComponentName listener;
    /** Matched by package: an app may recreate its session after stopping (seen with Netflix). */
    private final Set<String> pausedPackages = new LinkedHashSet<>();

    MediaSessionControl(Context context) {
        this.manager = context.getSystemService(MediaSessionManager.class);
        this.audio = context.getSystemService(AudioManager.class);
        this.listener = new ComponentName(context, NotificationListener.class);
    }

    @Override
    public boolean pausePlaying() {
        boolean paused = false;
        for (MediaController c : sessions()) {
            PlaybackState state = c.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) {
                c.getTransportControls().pause();
                pausedPackages.add(c.getPackageName());
                Log.i(AfrService.TAG, "paused " + c.getPackageName());
                paused = true;
            }
        }
        return paused;
    }

    @Override
    public void resumePaused() {
        Set<String> missing = new LinkedHashSet<>(pausedPackages);
        List<String> activePackages = new ArrayList<>();
        for (MediaController c : sessions()) {
            activePackages.add(c.getPackageName());
            if (!missing.remove(c.getPackageName())) continue;
            PlaybackState state = c.getPlaybackState();
            if (state == null || state.getState() != PlaybackState.STATE_PLAYING) {
                c.getTransportControls().play();
                Log.i(AfrService.TAG, "resumed " + c.getPackageName());
            }
        }
        // An app that stopped during the switch may mark its session inactive (Netflix does), so it
        // drops out of getActiveSessions. It normally stays the media-button session, so a PLAY key
        // reaches it (verified on the Shield 2026-10-08) - unless another app now owns media buttons.
        if (ResumePolicy.useMediaKeyFallback(missing, activePackages, pausedPackages)) {
            long now = SystemClock.uptimeMillis();
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            audio.dispatchMediaKeyEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY, 0));
            Log.i(AfrService.TAG, "resumed via media key " + missing);
        } else if (!missing.isEmpty()) {
            Log.i(AfrService.TAG, "left paused " + missing + "; other active sessions " + activePackages);
        }
        pausedPackages.clear();
    }

    private List<MediaController> sessions() {
        try {
            return manager.getActiveSessions(listener);
        } catch (SecurityException e) {
            Log.w(AfrService.TAG, "no notification-listener access; pause/resume skipped", e);
            return Collections.emptyList();
        }
    }
}
