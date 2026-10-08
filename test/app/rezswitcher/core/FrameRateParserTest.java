package app.rezswitcher.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FrameRateParserTest {
    @Test
    public void parsesThreadtimeLine() {
        String line = "10-07 22:36:54.285  3411 14027 D NvOsDebugPrintf: NVMEDIA: FrameRate(for last 120 frames) = 23.976043, AvgFrameRate = 23.976043 ";
        assertEquals(23.976043, FrameRateParser.parse(line), 1e-6);
    }

    @Test
    public void parsesBriefLineAndUsesRecentValueNotAverage() {
        String line = "D/NvOsDebugPrintf( 3411): NVMEDIA: FrameRate(for last 120 frames) = 50.000000, AvgFrameRate = 45.333335";
        assertEquals(50.0, FrameRateParser.parse(line), 1e-6);
    }

    @Test
    public void returnsZeroForUnrelatedLine() {
        assertEquals(0.0, FrameRateParser.parse("D NvOsDebugPrintf: NvRmStreamFree: WARN: pStream is NULL"), 0.0);
    }

    @Test
    public void returnsZeroForNull() {
        assertEquals(0.0, FrameRateParser.parse(null), 0.0);
    }
}
