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
    void processFrames_writesLargestCentroidOncePerSecond() throws IOException {
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
            "0.000,1,0"
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
        void processFrames_samplesAtOneSecondIntervals() throws IOException {
        VideoProcessor processor = new VideoProcessor();
        Path outputFile = tempDir.resolve("fps-based-timestamps.csv");

        processor.processFrames(
            List.of(
                    frameWithWhitePixelAt(0, 0),
                    frameWithWhitePixelAt(1, 1),
                    frameWithWhitePixelAt(2, 2),
                    frameWithWhitePixelAt(0, 1),
                    frameWithWhitePixelAt(1, 2)
            ),
            outputFile.toString(),
            "FFFFFF",
            1,
            2.0);

        List<String> lines = Files.readAllLines(outputFile);
        assertEquals(List.of(
            "timestamp,x,y",
            "0.000,0,0",
            "1.000,2,2",
            "2.000,1,2"
        ), lines);
        }

        @Test
        void processFrames_acceptsLowerCaseHexWithHashPrefix() throws IOException {
        VideoProcessor processor = new VideoProcessor();
        Path outputFile = tempDir.resolve("lowercase-hex.csv");

        processor.processFrames(
            List.of(frameWithWhitePixelAt(2, 1)),
            outputFile.toString(),
            "#ffffff",
            1,
            30.0);

        List<String> lines = Files.readAllLines(outputFile);
        assertEquals(List.of(
            "timestamp,x,y",
            "0.000,2,1"
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
    void processFrames_rejectsNonHexTargetColor() {
        VideoProcessor processor = new VideoProcessor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> processor.processFrames(List.of(), "unused.csv", "GGGGGG", 1, 30.0));

        assertEquals("targetColor must be a 6-digit RGB hex value.", exception.getMessage());
    }

    @Test
    void processFrames_rejectsNonPositiveFramesPerSecond() {
        VideoProcessor processor = new VideoProcessor();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> processor.processFrames(List.of(), "unused.csv", "FFFFFF", 1, 0.0));

        assertEquals("framesPerSecond must be greater than 0.", exception.getMessage());
    }

}