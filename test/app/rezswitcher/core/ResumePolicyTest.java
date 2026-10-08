package app.rezswitcher.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ResumePolicyTest {
    private static final String NETFLIX = "com.netflix.ninja";
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    private static final String MUSIC = "com.example.music";

    @Test
    public void nothingMissingNeedsNoFallback() {
        assertFalse(ResumePolicy.useMediaKeyFallback(
                Collections.<String>emptySet(), Collections.<String>emptyList(), Arrays.asList(NETFLIX)));
    }

    @Test
    public void missingWithNoActiveSessionsUsesFallback() {
        assertTrue(ResumePolicy.useMediaKeyFallback(
                Arrays.asList(NETFLIX), Collections.<String>emptyList(), Arrays.asList(NETFLIX)));
    }

    @Test
    public void unrelatedActiveSessionBlocksFallback() {
        assertFalse(ResumePolicy.useMediaKeyFallback(
                Arrays.asList(NETFLIX), Arrays.asList(YOUTUBE), Arrays.asList(NETFLIX)));
    }

    @Test
    public void onlyOtherPausedAppsActiveStillUsesFallback() {
        assertTrue(ResumePolicy.useMediaKeyFallback(
                Arrays.asList(NETFLIX), Arrays.asList(MUSIC), Arrays.asList(NETFLIX, MUSIC)));
    }
}
