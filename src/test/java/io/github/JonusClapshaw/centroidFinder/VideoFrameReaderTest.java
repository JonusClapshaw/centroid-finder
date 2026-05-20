package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VideoFrameReader.
 */
class VideoFrameReaderTest {

    private VideoFrameReader reader;

    @BeforeEach
    void setUp() {
        reader = new VideoFrameReader("sample.mp4");
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
    void testReadAllFramesBasicInvocation() {
        assertDoesNotThrow(() -> reader.readAllFrames(), "readAllFrames should not throw exception.");
    }
}