package io.github.JonusClapshaw.centroidFinder;

import java.io.PrintWriter;

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
        
    }

    static ParsedArgs parseArgs(String[] args) {
        if (args == null || args.length != 4) {
            throw new IllegalArgumentException("Expected exactly 4 arguments.");
        }

        String inputPath = args[0];
        String outputCsvPath = args[1];
        String targetColor = args[2];
        int threshold;
        try {
            threshold = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("threshold must be an integer.");
        }


        return new ParsedArgs(inputPath, outputCsvPath, targetColor, threshold);
    }
    
    record ParsedArgs(String inputPath, String outputCsvPath, String targetColor, int threshold) {
    }
}
