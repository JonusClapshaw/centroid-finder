package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VideoProcessorAppTest {

    @Test
    public void testParseArgsWithValidCommandLineValues() {
        String[] args = {"input.mp4", "output.csv", "red", "40"};

        VideoProcessorApp.ParsedArgs parsed = VideoProcessorApp.parseArgs(args);

        assertEquals("input.mp4", parsed.inputPath());
        assertEquals("output.csv", parsed.outputCsvPath());
        assertEquals("red", parsed.targetColor());
        assertEquals(40, parsed.threshold());
    }

    @Test
    public void testParseArgsWithHexColorValue() {
        String[] args = {"video.mp4", "result.csv", "FF0000", "15"};

        VideoProcessorApp.ParsedArgs parsed = VideoProcessorApp.parseArgs(args);

        assertEquals("FF0000", parsed.targetColor());
        assertEquals(15, parsed.threshold());
    }

    @Test
    public void testParseArgsThrowsWhenArgsIsNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(null)
        );

        assertEquals("Expected exactly 4 arguments.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenTooFewArguments() {
        String[] args = {"input.mp4", "output.csv", "red"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("Expected exactly 4 arguments.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenTooManyArguments() {
        String[] args = {"input.mp4", "output.csv", "red", "40", "extra"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("Expected exactly 4 arguments.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenThresholdIsNotInteger() {
        String[] args = {"input.mp4", "output.csv", "red", "forty"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("threshold must be an integer.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenInputPathIsBlank() {
        String[] args = {"", "output.csv", "FF0000", "40"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("inputPath must not be blank.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenOutputPathIsBlank() {
        String[] args = {"input.mp4", "   ", "FF0000", "40"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("outputCsvPath must not be blank.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenThresholdIsNegative() {
        String[] args = {"input.mp4", "output.csv", "FF0000", "-1"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("threshold must be non-negative.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenTargetColorIsWrongLength() {
        String[] args = {"input.mp4", "output.csv", "FFF", "40"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("targetColor must be a 6-digit RGB hex value.", ex.getMessage());
    }

    @Test
    public void testParseArgsThrowsWhenTargetColorIsNonHex() {
        String[] args = {"input.mp4", "output.csv", "GGGGGG", "40"};

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(args)
        );

        assertEquals("targetColor must be a 6-digit RGB hex value.", ex.getMessage());
    }
}
