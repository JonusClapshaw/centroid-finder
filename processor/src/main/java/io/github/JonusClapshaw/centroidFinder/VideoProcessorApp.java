package io.github.JonusClapshaw.centroidFinder;

import java.io.UncheckedIOException;

/**
 * Entry point for video processing.
 *
 * Current scope: parse and validate command-line arguments.
 *
 * Expected command:
 *   java -jar videoprocessor.jar <inputPath> <outputCsv> <targetColor> <threshold>
 */
public class VideoProcessorApp {

    public static void main(String[] args) {
        try {
            ParsedArgs parsedArgs = parseArgs(args);
            VideoProcessor processor = new VideoProcessor();
            processor.process(
                parsedArgs.inputPath(),
                parsedArgs.outputCsvPath(),
                parsedArgs.targetColor(),
                parsedArgs.threshold()
            );
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.exit(1);
        } catch (UncheckedIOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    static ParsedArgs parseArgs(String[] args) {
        if (args == null || args.length != 4) {
            throw new IllegalArgumentException(
                    "Expected exactly 4 arguments: <inputPath> <outputCsv> <targetColor> <threshold>");
        }

        String inputPath = args[0] == null ? "" : args[0].trim();
        String outputCsvPath = args[1] == null ? "" : args[1].trim();
        String targetColor = args[2] == null ? "" : args[2].trim();

        if (inputPath.isBlank()) {
            throw new IllegalArgumentException("inputPath must not be blank.");
        }
        if (outputCsvPath.isBlank()) {
            throw new IllegalArgumentException("outputCsvPath must not be blank.");
        }

        int threshold;
        try {
            threshold = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "threshold must be an integer, got: \"" + args[3] + "\"");
        }

        if (threshold < 0) {
            throw new IllegalArgumentException("threshold must be non-negative.");
        }

        String normalizedColor = targetColor.startsWith("#") ? targetColor.substring(1) : targetColor;
        if (normalizedColor.length() != 6 || !normalizedColor.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("targetColor must be a 6-digit RGB hex value.");
        }


        return new ParsedArgs(inputPath, outputCsvPath, targetColor, threshold);
    }
    
    record ParsedArgs(String inputPath, String outputCsvPath, String targetColor, int threshold) {
    }
}
