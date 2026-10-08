package app.rezswitcher.core;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

public class AfrControllerTest {
    private static final String NETFLIX = "com.netflix.ninja";
    private static final String LAUNCHER = "com.spocky.projengmenu";

    private final List<String> calls = new ArrayList<>();
    private final AfrController.ModeSink sink = new AfrController.ModeSink() {
        @Override public void requestMode(int modeId) { calls.add("request:" + modeId); }
        @Override public void clearMode() { calls.add("clear"); }
    };
    private final AfrController.ModeResolver resolver =
            fps -> ModeSelector.selectModeId(ShieldModes.ALL, 3840, 2160, fps);

    private AfrController controller;

    private AfrController newController(Set<String> excluded) {
        return new AfrController(sink, resolver, excluded);
    }

    @Before
    public void setUp() {
        controller = newController(Collections.<String>emptySet());
        controller.onForegroundChanged(NETFLIX, 0);
        calls.clear();
    }

    @Test
    public void firstReadingAppliesImmediately() {
        controller.onFrameRate(23.976, 5_000);
        assertEquals(Arrays.asList("request:3"), calls);
        assertEquals(3, controller.activeMode());
    }

    @Test
    public void repeatedSameReadingDoesNotRerequest() {
        controller.onFrameRate(23.976, 5_000);
        controller.onFrameRate(23.976, 10_000);
        assertEquals(Arrays.asList("request:3"), calls);
    }

    @Test
    public void changeNeedsTwoConsecutiveReadings() {
        controller.onFrameRate(23.976, 5_000);
        controller.onFrameRate(29.97, 10_000);
        assertEquals(Arrays.asList("request:3"), calls);
        controller.onFrameRate(29.97, 15_000);
        assertEquals(Arrays.asList("request:3", "request:20"), calls);
        assertEquals(20, controller.activeMode());
    }

    @Test
    public void readingMatchingActiveModeResetsPendingChange() {
        controller.onFrameRate(23.976, 5_000);
        controller.onFrameRate(29.97, 10_000);
        controller.onFrameRate(23.976, 15_000);
        controller.onFrameRate(29.97, 20_000);
        assertEquals(Arrays.asList("request:3"), calls);
    }

    @Test
    public void foregroundChangeClears() {
        controller.onFrameRate(23.976, 5_000);
        controller.onForegroundChanged(LAUNCHER, 6_000);
        assertEquals(Arrays.asList("request:3", "clear"), calls);
        assertEquals(0, controller.activeMode());
    }

    @Test
    public void sameForegroundReportedAgainDoesNotClear() {
        controller.onFrameRate(23.976, 5_000);
        controller.onForegroundChanged(NETFLIX, 6_000);
        assertEquals(Arrays.asList("request:3"), calls);
    }

    @Test
    public void foregroundChangeWithNoActiveModeDoesNothing() {
        controller.onForegroundChanged(LAUNCHER, 6_000);
        assertEquals(Collections.<String>emptyList(), calls);
    }

    @Test
    public void idleTimeoutClears() {
        controller.onFrameRate(23.976, 5_000);
        controller.onTick(64_999);
        assertEquals(Arrays.asList("request:3"), calls);
        controller.onTick(65_000);
        assertEquals(Arrays.asList("request:3", "clear"), calls);
    }

    @Test
    public void tickWithoutActiveModeDoesNothing() {
        controller.onTick(1_000_000);
        assertEquals(Collections.<String>emptyList(), calls);
    }

    @Test
    public void unknownFrameRateIgnored() {
        controller.onFrameRate(15.0, 5_000);
        assertEquals(Collections.<String>emptyList(), calls);
    }

    @Test
    public void excludedPackageIgnored() {
        controller = newController(new HashSet<>(Arrays.asList("de.cyberdream.dreamepg.tvh.tv.player")));
        controller.onForegroundChanged("de.cyberdream.dreamepg.tvh.tv.player", 0);
        controller.onFrameRate(50.0, 5_000);
        assertEquals(Collections.<String>emptyList(), calls);
    }

    @Test
    public void forceOverridesAndSuspendsAutomaticDecisions() {
        controller.onFrameRate(23.976, 5_000);
        controller.forceMode(20);
        controller.onFrameRate(23.976, 10_000);
        controller.onFrameRate(23.976, 15_000);
        controller.onForegroundChanged(LAUNCHER, 16_000);
        controller.onTick(200_000);
        assertEquals(Arrays.asList("request:3", "request:20"), calls);
        assertEquals(20, controller.activeMode());
    }

    @Test
    public void clearForceReleasesAndAutomaticResumes() {
        controller.onFrameRate(23.976, 5_000);
        controller.forceMode(20);
        controller.clearForce();
        controller.onFrameRate(23.976, 10_000);
        assertEquals(Arrays.asList("request:3", "request:20", "clear", "request:3"), calls);
        assertEquals(3, controller.activeMode());
    }
}
