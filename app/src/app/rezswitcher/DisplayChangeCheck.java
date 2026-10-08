package app.rezswitcher;

import android.view.Display;

import app.rezswitcher.core.PauseResumeSink;

/**
 * Compares by size and refresh rate, never by id (ids are renumbered after HDMI renegotiation).
 * Releasing an active override always counts as a change: removing the overlay can expose an
 * underlying app's own preferredDisplayModeId (the Channel 4 app sets one), which can't be predicted.
 */
final class DisplayChangeCheck implements PauseResumeSink.DisplayState {
    private static final float TOLERANCE = 0.02f;

    private final Display display;
    private boolean overriding;

    DisplayChangeCheck(Display display) {
        this.display = display;
    }

    @Override
    public boolean willChangeDisplay(int modeId) {
        if (modeId == 0) {
            boolean change = overriding;
            overriding = false;
            return change;
        }
        overriding = true;
        Display.Mode active = display.getMode();
        for (Display.Mode m : display.getSupportedModes()) {
            if (m.getModeId() == modeId) {
                return m.getPhysicalWidth() != active.getPhysicalWidth()
                        || m.getPhysicalHeight() != active.getPhysicalHeight()
                        || Math.abs(m.getRefreshRate() - active.getRefreshRate()) > TOLERANCE;
            }
        }
        return true;
    }
}
