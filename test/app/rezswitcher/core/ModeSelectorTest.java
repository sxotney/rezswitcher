package app.rezswitcher.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ModeSelectorTest {
    private static int select4k(double fps) {
        return ModeSelector.selectModeId(ShieldModes.ALL, 3840, 2160, fps);
    }

    @Test public void film23976() { assertEquals(3, select4k(23.976043)); }
    @Test public void film24() { assertEquals(12, select4k(24.0)); }
    @Test public void pal25UsesFiftyHz() { assertEquals(9, select4k(25.0)); }
    @Test public void pal50() { assertEquals(9, select4k(50.0)); }
    @Test public void ntsc2997UsesFiveNineFour() { assertEquals(20, select4k(29.97003)); }
    @Test public void youtube30UsesSixty() { assertEquals(18, select4k(30.00003)); }
    @Test public void ntsc5994() { assertEquals(20, select4k(59.94)); }
    @Test public void sixty() { assertEquals(18, select4k(60.0)); }
    @Test public void unknownRateIsZero() { assertEquals(0, select4k(15.0)); }
    @Test public void respectsRequestedResolution() {
        assertEquals(5, ModeSelector.selectModeId(ShieldModes.ALL, 1920, 1080, 23.976));
    }
    @Test public void missingModeIsZero() {
        assertEquals(0, ModeSelector.selectModeId(ShieldModes.ALL, 1280, 720, 23.976));
    }
}
