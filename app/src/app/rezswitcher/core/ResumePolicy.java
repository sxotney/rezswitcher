package app.rezswitcher.core;

import java.util.Collection;

/** Decides when an untargeted media key may stand in for a session-targeted resume. */
public final class ResumePolicy {
    private ResumePolicy() {}

    /**
     * A paused app whose session went inactive can only be resumed with a MEDIA_PLAY key, which goes
     * to whichever app owns the media-button session. Only allow it when no app other than the ones
     * we paused has an active session, so the key cannot start unrelated playback.
     */
    public static boolean useMediaKeyFallback(Collection<String> missingPaused,
                                              Collection<String> activePackages,
                                              Collection<String> pausedPackages) {
        if (missingPaused.isEmpty()) return false;
        for (String pkg : activePackages) {
            if (!pausedPackages.contains(pkg)) return false;
        }
        return true;
    }
}
