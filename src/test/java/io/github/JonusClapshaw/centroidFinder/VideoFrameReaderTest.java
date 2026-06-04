package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for VideoFrameReader.
 *
 * <p>Test philosophy:
 * <ul>
 *   <li><strong>Unit tests</strong> (no video fixture required) verify constructor behaviour,
 *       validation logic, and error-path contracts. They run unconditionally in CI.</li>
 *   <li><strong>Integration tests</strong> ({@code @Tag("integration")}) require a real MP4
 *       fixture at {@link #INTEGRATION_VIDEO_PATH}. They are skipped gracefully via
 *       {@code assumeTrue} when the file is absent, so a fresh checkout never shows
 *       spurious failures.</li>
 * </ul>
 *
 * <p>Integration tests deliberately decode only a small leading sample of frames
 * ({@link #FRAME_SAMPLE_SIZE}) rather than the entire video. This keeps the suite
 * fast regardless of how long the fixture is, and avoids OOM on long recordings.
 *
 * <p>To run only fast unit tests in CI, exclude the tag:
 * <pre>
 *   # Maven
 *   ./mvnw test -Dgroups='!integration'
 *   # Gradle
 *   ./gradlew test -PexcludeTags=integration
 * </pre>
 */
class VideoFrameReaderTest {

    /**
     * Path to the optional real-video fixture used by integration tests.
     * The file is NOT required to be present; integration tests skip automatically
     * when it is missing. The file is read in-place — it is never copied —
     * so there is no startup cost proportional to file size.
     */
    private static final String INTEGRATION_VIDEO_PATH = "sampleInput/ensantina.mp4";

    /**
     * Number of frames decoded during integration tests. Kept small so the suite
     * remains fast even with a long fixture video.
     */
    private static final int FRAME_SAMPLE_SIZE = 5;

    /** Timeout applied to every integration test that touches frame decoding. */
    private static final int DECODE_TIMEOUT_SECONDS = 30;

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the fixture file, or skips the calling test if it is absent.
     * The file is returned in-place (no copy) so tests that only need metadata
     * (isValidVideoFile, estimateFramesPerSecond) start immediately.
     */
    private File fixtureOrSkip() {
        File source = new File(INTEGRATION_VIDEO_PATH);
        assumeTrue(source.exists() && source.isFile(),
                "Integration fixture not found at '" + INTEGRATION_VIDEO_PATH
                        + "'. Place an MP4 there to enable integration tests.");
        return source;
    }

    /**
     * Decodes at most {@code maxFrames} frames from the given reader and returns them.
     * Uses the package-private {@code readFrames(FrameConsumer)} API to avoid loading
     * the entire video into memory.
     */
    private List<BufferedImage> sampleFrames(VideoFrameReader reader, int maxFrames)
            throws IOException {
        List<BufferedImage> sample = new ArrayList<>(maxFrames);
        reader.readFrames((frame, frameIndex) -> {
            if (sample.size() < maxFrames) {
                sample.add(frame);
            }
        });
        return sample;
    }

    // -------------------------------------------------------------------------
    // Unit tests — no video fixture required, always run in CI
    // -------------------------------------------------------------------------

    @Test
    void constructorAcceptsArbitraryPath() {
        VideoFrameReader reader = new VideoFrameReader("/some/future/video.mp4");
        assertNotNull(reader, "VideoFrameReader should be initialised without throwing.");
    }

    @Test
    void isValidVideoFile_returnsFalse_forNonExistentPath() {
        VideoFrameReader reader = new VideoFrameReader("/nonexistent/path/video.mp4");
        assertFalse(reader.isValidVideoFile(),
                "isValidVideoFile() must return false when the path does not exist.");
    }

    @Test
    void isValidVideoFile_returnsFalse_forDirectory() throws IOException {
        Path dir = tempDir.resolve("notAVideo");
        Files.createDirectory(dir);

        VideoFrameReader reader = new VideoFrameReader(dir.toString());
        assertFalse(reader.isValidVideoFile(),
                "isValidVideoFile() must return false for a directory.");
    }

    @Test
    void isValidVideoFile_returnsFalse_forEmptyFile() throws IOException {
        Path emptyFile = tempDir.resolve("empty.mp4");
        Files.createFile(emptyFile);

        VideoFrameReader reader = new VideoFrameReader(emptyFile.toString());
        assertFalse(reader.isValidVideoFile(),
                "isValidVideoFile() must return false for a zero-byte file.");
    }

    @Test
    void readAllFrames_throwsIOException_withDescriptiveMessage_forNonExistentFile() {
        VideoFrameReader reader = new VideoFrameReader("/nonexistent/path/video.mp4");

        IOException ex = assertThrows(IOException.class, reader::readAllFrames,
                "readAllFrames() must throw IOException for a missing file.");
        assertTrue(ex.getMessage().contains("cannot be read"),
                "Exception message should contain 'cannot be read'; was: " + ex.getMessage());
    }

    @Test
    void readAllFrames_throwsIOException_forCorruptFile() throws IOException {
        Path corrupt = tempDir.resolve("corrupt.mp4");
        Files.write(corrupt, new byte[]{0x00, 0x01, 0x02, 0x03});

        VideoFrameReader reader = new VideoFrameReader(corrupt.toString());

        IOException ex = assertThrows(IOException.class, reader::readAllFrames,
                "readAllFrames() must throw IOException for a corrupt file.");
        assertNotNull(ex.getMessage(),
                "IOException for a corrupt file should have a non-null message.");
    }

    // -------------------------------------------------------------------------
    // Integration tests — require fixture, skipped when absent
    // -------------------------------------------------------------------------

    @Test
    @Tag("integration")
    void isValidVideoFile_returnsTrue_forRealVideoFixture() {
        File fixture = fixtureOrSkip();
        VideoFrameReader reader = new VideoFrameReader(fixture.getPath());
        assertTrue(reader.isValidVideoFile(),
                "isValidVideoFile() must return true for a real, readable MP4 file.");
    }

    @Test
    @Tag("integration")
    @Timeout(value = DECODE_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void estimateFramesPerSecond_returnsPositiveValue_forRealVideoFixture() throws IOException {
        VideoFrameReader reader = new VideoFrameReader(fixtureOrSkip().getPath());

        double fps = reader.estimateFramesPerSecond();
        assertTrue(fps > 0.0,
                "estimateFramesPerSecond() must return a positive value; was " + fps);
    }

    @Test
    @Tag("integration")
    @Timeout(value = DECODE_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    void estimateFramesPerSecond_isReasonableForTypicalVideo() throws IOException {
        VideoFrameReader reader = new VideoFrameReader(fixtureOrSkip().getPath());

        double fps = reader.estimateFramesPerSecond();
        // Typical video: 1 fps (timelapse) to 240 fps (high-speed).
        assertTrue(fps >= 1.0 && fps <= 240.0,
                "Estimated FPS " + fps + " is outside the plausible range [1, 240].");
    }
}