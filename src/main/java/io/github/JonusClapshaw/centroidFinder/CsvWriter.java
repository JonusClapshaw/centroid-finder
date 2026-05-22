package io.github.JonusClapshaw.centroidFinder;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Writes centroid tracking results to a CSV file.
 *
 * Each row contains the timestamp (in seconds since the start of the video),
 * and the x and y coordinates of the largest centroid found in that frame.
 * If no centroid was found, coordinates (-1, -1) are written.
 *
 * The output file is opened when this object is constructed and closed
 * when close() is called. Use in a try-with-resources block to ensure
 * the file is always closed properly.
 *
 * Example usage:
 * <pre>
 *   try (CsvWriter writer = new CsvWriter("output.csv")) {
 *       writer.writeRow(0.000, groups);
 *       writer.writeRow(0.033, groups);
 *   }
 * </pre>
 */
public class CsvWriter implements AutoCloseable {

    private static final String HEADER = "timestamp,x,y";
    private static final String NO_CENTROID_ROW = "%s,-1,-1";

    private final BufferedWriter writer;

    /**
     * Opens the output CSV file and writes the header row.
     *
     * @param outputCsvPath path to the output CSV file to create or overwrite
     * @throws IOException if the file cannot be opened for writing
     */
    public CsvWriter(String outputCsvPath) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(outputCsvPath));
        writer.write(HEADER);
        writer.newLine();
    }

    /**
     * Writes one row to the CSV for the given timestamp.
     *
     * If the provided list of groups is non-empty, the centroid of the largest
     * group (first element, since groups are sorted in descending order) is written.
     * If the list is null or empty, coordinates (-1, -1) are written instead.
     *
     * @param timestampSeconds seconds elapsed since the start of the video
     * @param groups           the list of groups found in this frame, sorted descending,
     *                         or null/empty if no groups were found
     * @throws IOException if the row cannot be written
     */
    public void writeRow(double timestampSeconds, List<Group> groups) throws IOException {
        String timestamp = formatTimestamp(timestampSeconds);

        if (groups == null || groups.isEmpty()) {
            writer.write(String.format(NO_CENTROID_ROW, timestamp));
        } else {
            Group largest = groups.get(0);
            int x = largest.centroid().x();
            int y = largest.centroid().y();
            writer.write(String.format("%s,%d,%d", timestamp, x, y));
        }

        writer.newLine();
    }

    /**
     * Flushes and closes the underlying writer.
     * Called automatically when used in a try-with-resources block.
     *
     * @throws IOException if the writer cannot be closed
     */
    @Override
    public void close() throws IOException {
        writer.close();
    }

    /**
     * Formats a timestamp in seconds to three decimal places (millisecond precision).
     * For example, 0.03333... becomes "0.033".
     *
     * @param timestampSeconds the timestamp in seconds
     * @return formatted timestamp string
     */
    private String formatTimestamp(double timestampSeconds) {
        return String.format("%.3f", timestampSeconds);
    }
}