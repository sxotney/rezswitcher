package app.rezswitcher.core;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

public class PauseResumeSinkTest {
    /** Manual clock: tasks run only when advance() passes their due time. */
    private static final class FakeScheduler implements PauseResumeSink.Scheduler {
        private final class Task implements PauseResumeSink.Cancellable {
            final long due;
            final Runnable runnable;
            Task(long due, Runnable runnable) { this.due = due; this.runnable = runnable; }
            @Override public void cancel() { tasks.remove(this); }
        }

        private final List<Task> tasks = new ArrayList<>();
        private long now;

        @Override
        public PauseResumeSink.Cancellable schedule(Runnable task, long delayMs) {
            Task t = new Task(now + delayMs, task);
            tasks.add(t);
            return t;
        }

        void advance(long ms) {
            long target = now + ms;
            while (true) {
                Task next = null;
                for (Task t : tasks) if (t.due <= target && (next == null || t.due < next.due)) next = t;
                if (next == null) break;
                tasks.remove(next);
                now = next.due;
                next.runnable.run();
            }
            now = target;
        }
    }

    private static final class FakeMedia implements PauseResumeSink.MediaControl {
        boolean playing;
        int pauses;
        int resumes;
        @Override public boolean pausePlaying() {
            if (!playing) return false;
            playing = false;
            pauses++;
            return true;
        }
        @Override public void resumePaused() {
            resumes++;
            playing = true;
        }
    }

    private static final class FakeDisplay implements PauseResumeSink.DisplayState {
        boolean change = true;
        final List<Integer> asked = new ArrayList<>();
        @Override public boolean willChangeDisplay(int modeId) {
            asked.add(modeId);
            return change;
        }
    }

    private final List<String> calls = new ArrayList<>();
    private final AfrController.ModeSink inner = new AfrController.ModeSink() {
        @Override public void requestMode(int modeId) { calls.add("request:" + modeId); }
        @Override public void clearMode() { calls.add("clear"); }
    };
    private FakeScheduler scheduler;
    private FakeMedia media;
    private FakeDisplay display;
    private PauseResumeSink sink;

    @Before
    public void setUp() {
        scheduler = new FakeScheduler();
        media = new FakeMedia();
        display = new FakeDisplay();
        sink = new PauseResumeSink(inner, media, display, scheduler);
    }

    @Test
    public void nothingPlayingSwitchesImmediatelyWithoutResume() {
        sink.requestMode(3);
        assertEquals(Arrays.asList("request:3"), calls);
        sink.onHdmiAudioReconnected();
        scheduler.advance(20_000);
        assertEquals(0, media.resumes);
    }

    @Test
    public void resumesShortlyAfterAudioReconnect() {
        media.playing = true;
        sink.requestMode(3);
        assertEquals(1, media.pauses);
        scheduler.advance(499);
        assertEquals(Collections.<String>emptyList(), calls);
        scheduler.advance(1);
        assertEquals(Arrays.asList("request:3"), calls);
        scheduler.advance(3_000);
        assertEquals(0, media.resumes);
        sink.onHdmiAudioReconnected();
        scheduler.advance(499);
        assertEquals(0, media.resumes);
        scheduler.advance(1);
        assertEquals(1, media.resumes);
    }

    @Test
    public void fallsBackToTimeoutWhenNoReconnectSeen() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(500);
        scheduler.advance(7_999);
        assertEquals(0, media.resumes);
        scheduler.advance(1);
        assertEquals(1, media.resumes);
    }

    @Test
    public void reconnectBeforeTheSwitchIsIgnored() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(100);
        sink.onHdmiAudioReconnected();
        scheduler.advance(400);
        assertEquals(Arrays.asList("request:3"), calls);
        scheduler.advance(1_000);
        assertEquals(0, media.resumes);
        scheduler.advance(7_000);
        assertEquals(1, media.resumes);
    }

    @Test
    public void clearIsWrappedToo() {
        media.playing = true;
        sink.clearMode();
        scheduler.advance(500);
        assertEquals(Arrays.asList("clear"), calls);
        sink.onHdmiAudioReconnected();
        scheduler.advance(500);
        assertEquals(1, media.resumes);
    }

    @Test
    public void newRequestDuringLeadReplacesPendingSwitch() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(200);
        sink.requestMode(20);
        scheduler.advance(500);
        assertEquals(Arrays.asList("request:20"), calls);
        sink.onHdmiAudioReconnected();
        scheduler.advance(500);
        assertEquals(1, media.resumes);
    }

    @Test
    public void newRequestWhileAwaitingRestartsTheWaitAndResumesOnce() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(1_500);
        sink.requestMode(20);
        sink.onHdmiAudioReconnected();
        scheduler.advance(500);
        assertEquals(Arrays.asList("request:3", "request:20"), calls);
        assertEquals(0, media.resumes);
        sink.onHdmiAudioReconnected();
        scheduler.advance(500);
        assertEquals(1, media.resumes);
        scheduler.advance(20_000);
        assertEquals(1, media.resumes);
    }

    @Test
    public void afterCycleCompletesLaterReconnectsAndIdleSwitchesDoNothingExtra() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(500);
        sink.onHdmiAudioReconnected();
        scheduler.advance(500);
        assertEquals(1, media.resumes);
        media.playing = false;
        sink.requestMode(20);
        assertEquals(Arrays.asList("request:3", "request:20"), calls);
        sink.onHdmiAudioReconnected();
        scheduler.advance(20_000);
        assertEquals(1, media.resumes);
    }

    @Test
    public void requestThatDoesNotChangeDisplaySkipsPause() {
        media.playing = true;
        display.change = false;
        sink.requestMode(9);
        assertEquals(Arrays.asList("request:9"), calls);
        assertEquals(Arrays.asList(9), display.asked);
        assertEquals(0, media.pauses);
        scheduler.advance(20_000);
        assertEquals(0, media.resumes);
    }

    @Test
    public void noChangeAfterCancelledSwitchResumesAfterGrace() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(200);
        display.change = false;
        sink.clearMode();
        assertEquals(Arrays.asList("clear"), calls);
        assertEquals(Arrays.asList(3, 0), display.asked);
        scheduler.advance(499);
        assertEquals(0, media.resumes);
        scheduler.advance(1);
        assertEquals(1, media.resumes);
    }

    @Test
    public void noChangeAfterAppliedSwitchKeepsWaitingForReconnect() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(500);
        display.change = false;
        sink.clearMode();
        assertEquals(Arrays.asList("request:3", "clear"), calls);
        scheduler.advance(2_000);
        assertEquals(0, media.resumes);
        sink.onHdmiAudioReconnected();
        scheduler.advance(500);
        assertEquals(1, media.resumes);
    }

    @Test
    public void shutdownDuringLeadReleasesAndResumesWithoutSwitching() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(200);
        sink.shutdown();
        assertEquals(Arrays.asList("clear"), calls);
        assertEquals(1, media.resumes);
        scheduler.advance(20_000);
        assertEquals(Arrays.asList("clear"), calls);
        assertEquals(1, media.resumes);
    }

    @Test
    public void shutdownWhileAwaitingReleasesAndResumesOnce() {
        media.playing = true;
        sink.requestMode(3);
        scheduler.advance(1_000);
        sink.shutdown();
        assertEquals(Arrays.asList("request:3", "clear"), calls);
        assertEquals(1, media.resumes);
        sink.onHdmiAudioReconnected();
        scheduler.advance(20_000);
        assertEquals(1, media.resumes);
    }
}
