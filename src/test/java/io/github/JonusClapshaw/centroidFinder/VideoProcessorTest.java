package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VideoProcessor.
 */
class VideoProcessorTest {
    @TempDir
    Path tempDir;

    private BufferedImage frameWithWhitePixelAt(int x, int y) {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(x, y, 0x00FFFFFF);
        return image;
    }

    @Test
    void processFrames_writesLargestCentroidForEachFrame() throws IOException {
        VideoProcessor processor = new VideoProcessor();
        Path outputFile = tempDir.resolve("output.csv");

        processor.processFrames(
                List.of(frameWithWhitePixelAt(1, 0), frameWithWhitePixelAt(2, 2)),
                outputFile.toString(),
                "#FFFFFF",
                1,
                30.0);

        List<String> lines = Files.readAllLines(outputFile);
        assertEquals(List.of(
                "timestamp,x,y",
                "0.000,1,0",
                "0.033,2,2"
        ), lines);
    }

    @Test
    void processFrames_writesNoCentroidWhenFrameHasNoMatchingPixels() throws IOException {
        VideoProcessor processor = new VideoProcessor();
        Path outputFile = tempDir.resolve("empty-frame.csv");
        BufferedImage blackFrame = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        processor.processFrames(
                List.of(blackFrame),
                outputFile.toString(),
                "FFFFFF",
                1,
                30.0);

        List<String> lines = Files.readAllLines(outputFile);
        assertEquals(List.of(
                "timestamp,x,y",
                "0.000,-1,-1"
        ), lines);
    }

    @Test
    void processFrames_rejectsInvalidTargetColor() {
        VideoProcessor processor = new VideoProcessor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> processor.processFrames(List.of(), "unused.csv", "#FFF", 1, 30.0));

        assertEquals("targetColor must be a 6-digit RGB hex value.", exception.getMessage());
    }

    @Test
    void samplingIntervalFrames_returnsThirtyForThirtyFpsInput() {
        VideoProcessor processor = new VideoProcessor();

        assertEquals(30, processor.samplingIntervalFrames(30.0, 1.0));
    }

    @Test
    void samplingIntervalFrames_neverReturnsLessThanOne() {
        VideoProcessor processor = new VideoProcessor();

        assertEquals(1, processor.samplingIntervalFrames(0.0, 1.0));
        assertEquals(1, processor.samplingIntervalFrames(0.5, 1.0));
    }
}