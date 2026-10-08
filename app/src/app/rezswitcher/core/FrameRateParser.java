package app.rezswitcher.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts the decoder's recent frame rate from an NVIDIA "NVMEDIA: FrameRate" logcat line. */
public final class FrameRateParser {
    private static final Pattern FRAME_RATE =
            Pattern.compile("NVMEDIA: FrameRate\\(for last \\d+ frames\\) = ([0-9]+(?:\\.[0-9]+)?)");

    private FrameRateParser() {}

    /** Returns frames per second, or 0 if the line is not a frame-rate line. */
    public static double parse(String line) {
        if (line == null) return 0;
        Matcher m = FRAME_RATE.matcher(line);
        if (!m.find()) return 0;
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
