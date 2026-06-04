package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InputValidator}.
 *
 * <p>Tests are self-contained: files needed for "happy path" checks are created
 * in {@code @TempDir} and do not require any project fixtures to be present.
 *
 * <p>Path-containment tests for {@code validateOutputCsvPath} use a temp directory
 * as a stand-in for {@code sampleOutput/}, exercised via the package-private
 * {@link InputValidator#OUTPUT_DIR} constant. Because the working directory in a
 * test JVM is the project root, tests that exercise the real {@code sampleOutput/}
 * anchor are limited to structural/traversal checks that do not require the
 * directory to exist on disk.
 */
class InputValidatorTest {

    @TempDir
    Path tempDir;

    // =========================================================================
    // validateInputPath
    // =========================================================================

    @Test
    void validateInputPath_passes_forExistingReadableFile() throws IOException {
        Path file = tempDir.resolve("video.mp4");
        Files.createFile(file);
        // Absolute path to a real file — must not throw.
        assertDoesNotThrow(() -> InputValidator.validateInputPath(file.toString()));
    }

    @Test
    void validateInputPath_throws_forNullValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInputPath(null));
        assertTrue(ex.getMessage().contains("inputPath"), ex.getMessage());
    }

    @Test
    void validateInputPath_throws_forBlankString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInputPath("   "));
        assertTrue(ex.getMessage().contains("inputPath"), ex.getMessage());
    }

    @Test
    void validateInputPath_throws_forEmptyString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInputPath(""));
        assertTrue(ex.getMessage().contains("inputPath"), ex.getMessage());
    }

    @Test
    void validateInputPath_throws_forNullByte() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInputPath("video\0.mp4"));
        assertTrue(ex.getMessage().contains("null bytes"), ex.getMessage());
    }

    @Test
    void validateInputPath_throws_forNonExistentFile() {
        // Use an absolute path that is guaranteed not to exist.
        String absent = tempDir.resolve("does_not_exist.mp4").toString();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInputPath(absent));
        assertTrue(ex.getMessage().contains("does not exist"), ex.getMessage());
    }

    @Test
    void validateInputPath_throws_forDirectory() throws IOException {
        Path dir = tempDir.resolve("aDirectory");
        Files.createDirectory(dir);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateInputPath(dir.toString()));
        assertTrue(ex.getMessage().contains("must be a file"), ex.getMessage());
    }

    // Note: validateInputPath does NOT restrict which directory input files may
    // come from — videos can legitimately live anywhere on the filesystem.
    // Path-traversal confinement applies only to validateOutputCsvPath.

    // =========================================================================
    // validateOutputCsvPath
    // =========================================================================

    @Test
    void validateOutputCsvPath_throws_forNullValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateOutputCsvPath(null));
        assertTrue(ex.getMessage().contains("outputCsvPath"), ex.getMessage());
    }

    @Test
    void validateOutputCsvPath_throws_forBlankString() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateOutputCsvPath("   "));
        assertTrue(ex.getMessage().contains("outputCsvPath"), ex.getMessage());
    }

    @Test
    void validateOutputCsvPath_throws_forNullByte() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateOutputCsvPath("sampleOutput/out\0.csv"));
        assertTrue(ex.getMessage().contains("null bytes"), ex.getMessage());
    }

    @Test
    void validateOutputCsvPath_throws_forNonCsvExtension() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateOutputCsvPath("sampleOutput/output.txt"));
        assertTrue(ex.getMessage().contains(".csv"), ex.getMessage());
    }

    @Test
    void validateOutputCsvPath_throws_forNoExtension() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateOutputCsvPath("sampleOutput/output"));
        assertTrue(ex.getMessage().contains(".csv"), ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "../output.csv",
        "../../output.csv",
        "sampleOutput/../../etc/crontab.csv",
        "/tmp/evil.csv",
        "otherDir/output.csv"
    })
    void validateOutputCsvPath_throws_forPathOutsideOutputDir(String badPath) {
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateOutputCsvPath(badPath),
                "Expected path outside sampleOutput/ to be rejected: " + badPath);
    }

    // =========================================================================
    // validateTargetColor
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {"FF8800", "#FF8800", "000000", "#000000", "ffffff", "ABCDEF"})
    void validateTargetColor_passes_forValidHexColors(String color) {
        assertDoesNotThrow(() -> InputValidator.validateTargetColor(color));
    }

    @Test
    void validateTargetColor_throws_forNull() {
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateTargetColor(null));
    }

    @Test
    void validateTargetColor_throws_forBlank() {
        assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateTargetColor("   "));
    }

    @Test
    void validateTargetColor_throws_forTooShort() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateTargetColor("FF88"));
        assertTrue(ex.getMessage().contains("6-digit"), ex.getMessage());
    }

    @Test
    void validateTargetColor_throws_forTooLong() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateTargetColor("FF880011"));
        assertTrue(ex.getMessage().contains("6-digit"), ex.getMessage());
    }

    @Test
    void validateTargetColor_throws_forNonHexCharacters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateTargetColor("GGHH00"));
        assertTrue(ex.getMessage().contains("6-digit"), ex.getMessage());
    }

    @Test
    void validateTargetColor_throws_forHashOnly() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateTargetColor("#"));
        assertTrue(ex.getMessage().contains("6-digit"), ex.getMessage());
    }

    // =========================================================================
    // validateThreshold
    // =========================================================================

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100, 255, 441, InputValidator.MAX_THRESHOLD})
    void validateThreshold_passes_forValidRange(int threshold) {
        assertDoesNotThrow(() -> InputValidator.validateThreshold(threshold));
    }

    @Test
    void validateThreshold_throws_forNegativeValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateThreshold(-1));
        assertTrue(ex.getMessage().contains(">= 0"), ex.getMessage());
    }

    @Test
    void validateThreshold_throws_forLargeNegativeValue() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateThreshold(Integer.MIN_VALUE));
        assertTrue(ex.getMessage().contains(">= 0"), ex.getMessage());
    }

    @Test
    void validateThreshold_throws_forValueAboveMax() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateThreshold(InputValidator.MAX_THRESHOLD + 1));
        assertTrue(ex.getMessage().contains("<= " + InputValidator.MAX_THRESHOLD), ex.getMessage());
    }

    @Test
    void validateThreshold_throws_forMaxInt() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> InputValidator.validateThreshold(Integer.MAX_VALUE));
        assertTrue(ex.getMessage().contains("<= " + InputValidator.MAX_THRESHOLD), ex.getMessage());
    }

    // =========================================================================
    // VideoProcessorApp.parseArgs integration — validate the wiring
    // =========================================================================

    /** Creates a real, readable input file so parseArgs validation reaches the field under test. */
    private String realInputFile() throws IOException {
        Path file = tempDir.resolve("input.mp4");
        Files.createFile(file);
        return file.toString();
    }

    @Test
    void parseArgs_throws_forWrongArgCount() {
        assertThrows(IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(new String[]{"only", "three", "args"}));
    }

    @Test
    void parseArgs_throws_forNonIntegerThreshold() throws IOException {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(
                        new String[]{realInputFile(), "sampleOutput/out.csv", "#FF8800", "notANumber"}));
        assertTrue(ex.getMessage().contains("threshold"), ex.getMessage());
    }

    @Test
    void parseArgs_throws_forNegativeThreshold() throws IOException {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(
                        new String[]{realInputFile(), "sampleOutput/out.csv", "#FF8800", "-1"}));
        assertTrue(ex.getMessage().contains(">= 0"), ex.getMessage());
    }

    @Test
    void parseArgs_throws_forBlankInputPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(
                        new String[]{"   ", "sampleOutput/out.csv", "#FF8800", "100"}));
        assertTrue(ex.getMessage().contains("inputPath"), ex.getMessage());
    }

    @Test
    void parseArgs_throws_forBlankOutputPath() throws IOException {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(
                        new String[]{realInputFile(), "   ", "#FF8800", "100"}));
        assertTrue(ex.getMessage().contains("outputCsvPath"), ex.getMessage());
    }

    @Test
    void parseArgs_throws_forInvalidColor() throws IOException {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> VideoProcessorApp.parseArgs(
                        new String[]{realInputFile(), "sampleOutput/out.csv", "ZZZZZZ", "100"}));
        assertTrue(ex.getMessage().contains("6-digit"), ex.getMessage());
    }
}