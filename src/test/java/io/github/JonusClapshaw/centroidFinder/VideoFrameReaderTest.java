package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VideoFrameReader.
 */
class VideoFrameReaderTest {
    private static final String SAMPLE_VIDEO_PATH = "sampleInput/ensantina.mp4";

    private VideoFrameReader reader;

    @BeforeEach
    void setUp() {
        reader = new VideoFrameReader(SAMPLE_VIDEO_PATH);
    }

    @Test
    void testVideoFrameReaderInitialization() {
        assertNotNull(reader, "VideoFrameReader should be initialized.");
    }

    @Test
    void testIsValidVideoFileWithNonExistentFile() {
        VideoFrameReader invalidReader = new VideoFrameReader("/nonexistent/path/video.mp4");
        assertFalse(invalidReader.isValidVideoFile(), "Should return false for non-existent file.");
    }

    @Test
    void testIsValidVideoFileWithExistingSample() {
        assertTrue(reader.isValidVideoFile(), "Should return true for the checked-in sample video.");
    }

    @Test
    void testReadAllFramesReturnsDecodedFrames() throws IOException {
        List<BufferedImage> frames = reader.readAllFrames();

        assertFalse(frames.isEmpty(), "Sample video should decode into at least one frame.");
        assertNotNull(frames.get(0), "Decoded frame should not be null.");
    }

    @Test
    void testEstimateFramesPerSecondReturnsPositiveValue() throws IOException {
        double framesPerSecond = reader.estimateFramesPerSecond();

        assertTrue(framesPerSecond > 0.0, "Estimated FPS should be positive.");
    }

    @Test
    void testReadAllFramesThrowsForMissingFile() {
        VideoFrameReader invalidReader = new VideoFrameReader("/nonexistent/path/video.mp4");

        IOException exception = assertThrows(IOException.class, invalidReader::readAllFrames);
        assertTrue(exception.getMessage().contains("cannot be read"));
    }
}