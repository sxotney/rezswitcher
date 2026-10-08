package app.rezswitcher.core;

import java.util.List;

/** Maps a content frame rate to the display mode that shows it without judder. */
public final class ModeSelector {
    private static final float REFRESH_TOLERANCE = 0.02f;

    private ModeSelector() {}

    /** Target display refresh rate for content at fps, or 0 if there is no good match. */
    public static float targetRefresh(double fps) {
        if (fps >= 23.95 && fps < 23.99) return 23.976f;
        if (fps >= 23.99 && fps <= 24.05) return 24.0f;
        if ((fps >= 24.9 && fps <= 25.1) || (fps >= 49.8 && fps <= 50.2)) return 50.0f;
        if ((fps >= 29.95 && fps < 29.99) || (fps >= 59.88 && fps < 59.97)) return 59.94f;
        if ((fps >= 29.99 && fps <= 30.05) || (fps >= 59.97 && fps <= 60.05)) return 60.0f;
        return 0f;
    }

    /** Mode id at width x height whose refresh matches fps, or 0 if none. */
    public static int selectModeId(List<ModeInfo> modes, int width, int height, double fps) {
        float target = targetRefresh(fps);
        if (target == 0f) return 0;
        for (ModeInfo m : modes) {
            if (m.width == width && m.height == height
                    && Math.abs(m.refreshRate - target) < REFRESH_TOLERANCE) {
                return m.id;
            }
        }
        return 0;
    }
}
