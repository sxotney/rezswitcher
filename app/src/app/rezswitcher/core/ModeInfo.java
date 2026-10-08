package app.rezswitcher.core;

/** Immutable copy of the parts of android.view.Display.Mode the selector needs. */
public final class ModeInfo {
    public final int id;
    public final int width;
    public final int height;
    public final float refreshRate;

    public ModeInfo(int id, int width, int height, float refreshRate) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.refreshRate = refreshRate;
    }
}
