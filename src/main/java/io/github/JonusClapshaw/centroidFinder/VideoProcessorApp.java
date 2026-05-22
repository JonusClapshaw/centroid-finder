package io.github.JonusClapshaw.centroidFinder;

import java.io.IOException;
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
        try {
            ParsedArgs parsedArgs = parseArgs(args);
            VideoProcessor processor = new VideoProcessor();
            try (CsvWriter writer = new CsvWriter(parsedArgs.outputCsvPath())) {
                processor.process(
                    parsedArgs.inputPath(),
                    parsedArgs.targetColor(),
                    parsedArgs.threshold()
                    // add writer method later once VideoProcessor is completed
                );
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
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
