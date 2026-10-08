package app.rezswitcher;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;

/** Reports the most recently resumed app. Needs the GET_USAGE_STATS appop granted over adb. */
final class ForegroundWatcher {
    interface Listener {
        void onForeground(String pkg);
    }

    private static final long LOOKBACK_MS = 10_000;

    private final UsageStatsManager usm;
    private final Listener listener;

    ForegroundWatcher(Context context, Listener listener) {
        this.usm = context.getSystemService(UsageStatsManager.class);
        this.listener = listener;
    }

    /** Call periodically; reports the latest ACTIVITY_RESUMED package in the last few seconds, if any. */
    void poll() {
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - LOOKBACK_MS, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String latest = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) latest = e.getPackageName();
        }
        if (latest != null) listener.onForeground(latest);
    }
}
