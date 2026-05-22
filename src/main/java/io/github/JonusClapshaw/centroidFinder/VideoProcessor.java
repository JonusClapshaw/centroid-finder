package io.github.JonusClapshaw.centroidFinder;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Orchestrates the video processing pipeline.
 */
public class VideoProcessor {
    private static final double DEFAULT_FRAMES_PER_SECOND = 30.0;

    public void process(String inputPath, String outputCsvPath, String targetColor, int threshold) {
        try {
            VideoFrameReader frameReader = new VideoFrameReader(inputPath);
            List<BufferedImage> frames = frameReader.readAllFrames();
            processFrames(frames, outputCsvPath, targetColor, threshold, DEFAULT_FRAMES_PER_SECOND);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to process video.", exception);
        }
    }

    void processFrames(List<BufferedImage> frames,
                       String outputCsvPath,
                       String targetColor,
                       int threshold,
                       double framesPerSecond) throws IOException {
        int parsedTargetColor = parseTargetColor(targetColor);
        ImageGroupFinder groupFinder = buildGroupFinder(parsedTargetColor, threshold);

        try (CsvWriter csvWriter = new CsvWriter(outputCsvPath)) {
            for (int index = 0; index < frames.size(); index++) {
                double timestampSeconds = index / framesPerSecond;
                List<Group> groups = groupFinder.findConnectedGroups(frames.get(index));
                csvWriter.writeRow(timestampSeconds, groups);
            }
        }
    }

    private ImageGroupFinder buildGroupFinder(int targetColor, int threshold) {
        return new BinarizingImageGroupFinder(
                new DistanceImageBinarizer(new EuclideanColorDistance(), targetColor, threshold),
                new DfsBinaryGroupFinder());
    }

    private int parseTargetColor(String targetColor) {
        String normalized = targetColor.startsWith("#") ? targetColor.substring(1) : targetColor;
        if (normalized.length() != 6) {
            throw new IllegalArgumentException("targetColor must be a 6-digit RGB hex value.");
        }

        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("targetColor must be a 6-digit RGB hex value.");
        }
    }
}