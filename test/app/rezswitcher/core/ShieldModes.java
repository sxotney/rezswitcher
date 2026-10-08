package app.rezswitcher.core;

import java.util.Arrays;
import java.util.List;

final class ShieldModes {
    static final List<ModeInfo> ALL = Arrays.asList(
            new ModeInfo(1, 1280, 720, 60.000004f),
            new ModeInfo(2, 3840, 2160, 29.97003f),
            new ModeInfo(3, 3840, 2160, 23.976025f),
            new ModeInfo(4, 1920, 1080, 29.97003f),
            new ModeInfo(5, 1920, 1080, 23.976025f),
            new ModeInfo(6, 1920, 1080, 59.94006f),
            new ModeInfo(7, 1280, 720, 59.94006f),
            new ModeInfo(8, 720, 480, 60.000004f),
            new ModeInfo(9, 3840, 2160, 50.0f),
            new ModeInfo(10, 3840, 2160, 30.000002f),
            new ModeInfo(11, 3840, 2160, 25.0f),
            new ModeInfo(12, 3840, 2160, 24.000002f),
            new ModeInfo(13, 1920, 1080, 30.000002f),
            new ModeInfo(14, 1920, 1080, 25.0f),
            new ModeInfo(15, 1920, 1080, 24.000002f),
            new ModeInfo(16, 1280, 720, 50.0f),
            new ModeInfo(17, 1920, 1080, 50.0f),
            new ModeInfo(18, 3840, 2160, 60.000004f),
            new ModeInfo(19, 1920, 1080, 60.000004f),
            new ModeInfo(20, 3840, 2160, 59.94006f));

    private ShieldModes() {}
}
