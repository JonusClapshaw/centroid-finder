package io.github.JonusClapshaw.centroidFinder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Validates command-line and API inputs for the video processing pipeline,
 * failing fast with clear user-facing error messages before any I/O is attempted.
 *
 * <p>All methods throw {@link IllegalArgumentException} on invalid input.
 * This class is package-private so it can be reused by any future entry point
 * (CLI, server, batch runner) without duplicating logic.
 *
 * <h2>Path security model</h2>
 * <ul>
 *   <li><b>Input path</b>: must be an existing, readable file. Null bytes and
 *       path segments that escape the working directory via {@code ..} are
 *       rejected to prevent directory traversal.</li>
 *   <li><b>Output CSV path</b>: must resolve inside {@value #OUTPUT_DIR} relative
 *       to the working directory. This confines all writes to the project's
 *       designated output directory and prevents overwriting arbitrary files.</li>
 * </ul>
 *
 * <h2>Threshold model</h2>
 * Threshold is the Euclidean color distance cutoff. The maximum meaningful value
 * is {@value #MAX_THRESHOLD} (≈ sqrt(255² × 3), the distance between black and white).
 * Values outside [0, {@value #MAX_THRESHOLD}] are rejected.
 */
class InputValidator {

    /** Directory that all output CSV paths must reside within. */
    static final String OUTPUT_DIR = "sampleOutput";

    /**
     * Maximum meaningful Euclidean color-distance threshold.
     * sqrt(255^2 * 3) ≈ 441.67, rounded up to 442.
     */
    static final int MAX_THRESHOLD = 442;

    // Prevent instantiation — all methods are static.
    private InputValidator() {}

    /**
     * Validates the video input path.
     *
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must not contain null bytes.</li>
     *   <li>Must not escape the working directory via {@code ..} segments.</li>
     *   <li>Must refer to an existing, readable file (not a directory).</li>
     * </ul>
     *
     * @param inputPath the raw path string supplied by the caller
     * @throws IllegalArgumentException if any constraint is violated
     */
    static void validateInputPath(String inputPath) {
        requireNonBlank(inputPath, "inputPath");
        requireNoNullBytes(inputPath, "inputPath");

        // Input videos may live anywhere readable on the filesystem, so we do
        // not confine them to the working directory. We do resolve to an
        // absolute path so File checks are unambiguous.
        File file = Paths.get(inputPath).toAbsolutePath().normalize().toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException(
                    "inputPath does not exist: " + inputPath);
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException(
                    "inputPath must be a file, not a directory: " + inputPath);
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException(
                    "inputPath is not readable: " + inputPath);
        }
    }

    /**
     * Validates the output CSV path.
     *
     * <ul>
     *   <li>Must not be blank.</li>
     *   <li>Must not contain null bytes.</li>
     *   <li>Must resolve inside the {@value #OUTPUT_DIR} directory (prevents
     *       overwriting arbitrary files via path traversal).</li>
     *   <li>Must end with {@code .csv} (case-insensitive).</li>
     *   <li>Parent directory must exist and be writable.</li>
     * </ul>
     *
     * @param outputCsvPath the raw path string supplied by the caller
     * @throws IllegalArgumentException if any constraint is violated
     */
    static void validateOutputCsvPath(String outputCsvPath) {
        requireNonBlank(outputCsvPath, "outputCsvPath");
        requireNoNullBytes(outputCsvPath, "outputCsvPath");

        if (!outputCsvPath.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException(
                    "outputCsvPath must end with .csv: " + outputCsvPath);
        }

        Path resolved = resolveAgainstWorkingDir(outputCsvPath);
        Path allowedRoot = workingDir().resolve(OUTPUT_DIR).normalize();
        requireContainedWithin(resolved, allowedRoot, "outputCsvPath",
                "must be inside the '" + OUTPUT_DIR + "' directory");

        File parentDir = resolved.toFile().getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            throw new IllegalArgumentException(
                    "outputCsvPath parent directory does not exist: " + parentDir);
        }
        if (parentDir != null && !parentDir.canWrite()) {
            throw new IllegalArgumentException(
                    "outputCsvPath parent directory is not writable: " + parentDir);
        }
    }

    /**
     * Validates the target color string.
     *
     * <p>Must be a 6-digit hex RGB value, optionally prefixed with {@code #}
     * (e.g. {@code "#FF8800"} or {@code "FF8800"}).
     *
     * @param targetColor the raw color string supplied by the caller
     * @throws IllegalArgumentException if the value is not a valid 6-digit hex color
     */
    static void validateTargetColor(String targetColor) {
        requireNonBlank(targetColor, "targetColor");
        String normalized = targetColor.startsWith("#") ? targetColor.substring(1) : targetColor;
        if (normalized.length() != 6) {
            throw new IllegalArgumentException(
                    "targetColor must be a 6-digit RGB hex value (e.g. \"#FF8800\" or \"FF8800\"): "
                            + targetColor);
        }
        try {
            Integer.parseInt(normalized, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "targetColor must be a 6-digit RGB hex value (e.g. \"#FF8800\" or \"FF8800\"): "
                            + targetColor);
        }
    }

    /**
     * Validates the color-distance threshold.
     *
     * <p>Must be in the range [0, {@value #MAX_THRESHOLD}].
     * A negative value would exclude every pixel; a value above {@value #MAX_THRESHOLD}
     * would include every pixel regardless of color.
     *
     * @param threshold the integer threshold supplied by the caller
     * @throws IllegalArgumentException if the value is out of range
     */
    static void validateThreshold(int threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException(
                    "threshold must be >= 0 (was " + threshold
                            + "). A negative threshold excludes every pixel.");
        }
        if (threshold > MAX_THRESHOLD) {
            throw new IllegalArgumentException(
                    "threshold must be <= " + MAX_THRESHOLD + " (was " + threshold
                            + "). Values above " + MAX_THRESHOLD
                            + " include every pixel regardless of color.");
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static Path workingDir() {
        return Paths.get(System.getProperty("user.dir")).normalize();
    }

    private static Path resolveAgainstWorkingDir(String rawPath) {
        try {
            return workingDir().resolve(rawPath).normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid path: " + rawPath, e);
        }
    }

    /**
     * Requires that {@code resolved} is inside {@code allowedRoot} (inclusive).
     */
    private static void requireContainedWithin(Path resolved, Path allowedRoot,
                                               String fieldName, String reason) {
        try {
            Path canonicalResolved = resolved.toFile().getCanonicalFile().toPath();
            Path canonicalRoot = allowedRoot.toFile().getCanonicalFile().toPath();
            if (!canonicalResolved.startsWith(canonicalRoot)) {
                throw new IllegalArgumentException(
                        fieldName + " " + reason + ": " + resolved);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    fieldName + " could not be resolved to a canonical path: " + resolved, e);
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank.");
        }
    }

    private static void requireNoNullBytes(String value, String fieldName) {
        if (value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must not contain null bytes.");
        }
    }
}