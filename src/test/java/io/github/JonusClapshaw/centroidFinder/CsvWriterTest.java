package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CsvWriter.
 *
 * Each test writes to a temporary file provided by JUnit's @TempDir,
 * then reads the file back and asserts on its contents.
 */
class CsvWriterTest {

    @TempDir
    Path tempDir;

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Returns all lines from the temp CSV file as a list of strings.
     */
    private List<String> writtenLines(Path file) throws IOException {
        return Files.readAllLines(file);
    }

    /**
     * Builds a Group with the given size and centroid coordinates.
     */
    private Group group(int size, int x, int y) {
        return new Group(size, new Coordinate(x, y));
    }

    // -------------------------------------------------------------------------
    // Constructor / header
    // -------------------------------------------------------------------------

    @Test
    void constructor_writesHeaderAsFirstLine() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            // no rows written
        }

        List<String> lines = writtenLines(file);
        assertEquals("timestamp,x,y", lines.get(0));
    }

    @Test
    void constructor_createsFileWithOnlyHeaderWhenNoRowsWritten() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            // intentionally empty
        }

        List<String> lines = writtenLines(file);
        assertEquals(1, lines.size(), "File should contain only the header row");
    }

    // -------------------------------------------------------------------------
    // writeRow — centroid found
    // -------------------------------------------------------------------------

    @Test
    void writeRow_singleGroup_writesCorrectCentroidCoordinates() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.0, List.of(group(10, 142, 87)));
        }

        List<String> lines = writtenLines(file);
        assertEquals("0.000,142,87", lines.get(1));
    }

    @Test
    void writeRow_multipleGroups_writesLargestGroupCentroid() throws IOException {
        Path file = tempDir.resolve("output.csv");

        // Groups must be in descending order (as ImageGroupFinder would return them).
        // Largest group first — CsvWriter should pick groups.get(0).
        List<Group> groups = List.of(
                group(50, 200, 300),   // largest — should be written
                group(20, 100, 150),
                group(5,  10,  10)
        );

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(1.0, groups);
        }

        List<String> lines = writtenLines(file);
        assertEquals("1.000,200,300", lines.get(1));
    }

    @Test
    void writeRow_centroidAtOrigin_writesZeroCoordinates() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.5, List.of(group(1, 0, 0)));
        }

        List<String> lines = writtenLines(file);
        assertEquals("0.500,0,0", lines.get(1));
    }

    // -------------------------------------------------------------------------
    // writeRow — no centroid (empty / null list)
    // -------------------------------------------------------------------------

    @Test
    void writeRow_emptyList_writesNegativeOneCoordinates() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.033, List.of());
        }

        List<String> lines = writtenLines(file);
        assertEquals("0.033,-1,-1", lines.get(1));
    }

    @Test
    void writeRow_nullList_writesNegativeOneCoordinates() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.033, null);
        }

        List<String> lines = writtenLines(file);
        assertEquals("0.033,-1,-1", lines.get(1));
    }

    // -------------------------------------------------------------------------
    // writeRow — timestamp formatting
    // -------------------------------------------------------------------------

    @Test
    void writeRow_timestampFormattedToThreeDecimalPlaces() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            // 1/30 fps = 0.03333... should round to 0.033
            writer.writeRow(1.0 / 30.0, List.of(group(5, 10, 20)));
        }

        List<String> lines = writtenLines(file);
        assertTrue(lines.get(1).startsWith("0.033,"),
                "Timestamp should be formatted to 3 decimal places; got: " + lines.get(1));
    }

    @Test
    void writeRow_zeroTimestamp_formattedAsThreeDecimalZeroes() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.0, List.of(group(5, 10, 20)));
        }

        List<String> lines = writtenLines(file);
        assertTrue(lines.get(1).startsWith("0.000,"),
                "Zero timestamp should be written as 0.000; got: " + lines.get(1));
    }

    @Test
    void writeRow_largeTimestamp_formattedCorrectly() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(3600.0, List.of(group(5, 10, 20)));
        }

        List<String> lines = writtenLines(file);
        assertTrue(lines.get(1).startsWith("3600.000,"),
                "Large timestamp should not use scientific notation; got: " + lines.get(1));
    }

    // -------------------------------------------------------------------------
    // Multiple rows
    // -------------------------------------------------------------------------

    @Test
    void writeRow_multipleRows_allRowsWrittenInOrder() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.000, List.of(group(10, 142, 87)));
            writer.writeRow(0.033, List.of(group(10, 145, 89)));
            writer.writeRow(0.067, List.of());
            writer.writeRow(0.100, List.of(group(10, 152, 94)));
        }

        List<String> lines = writtenLines(file);
        assertEquals(5, lines.size(), "Should have header + 4 data rows");
        assertEquals("timestamp,x,y", lines.get(0));
        assertEquals("0.000,142,87",  lines.get(1));
        assertEquals("0.033,145,89",  lines.get(2));
        assertEquals("0.067,-1,-1",   lines.get(3));
        assertEquals("0.100,152,94",  lines.get(4));
    }

    @Test
    void writeRow_mixOfCentroidsAndMisses_correctRowCount() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.0,   List.of(group(8, 50, 60)));
            writer.writeRow(0.033, null);
            writer.writeRow(0.067, List.of());
            writer.writeRow(0.100, List.of(group(12, 55, 65)));
        }

        List<String> lines = writtenLines(file);
        // header + 4 data rows = 5 total
        assertEquals(5, lines.size());
    }

    // -------------------------------------------------------------------------
    // AutoCloseable / file integrity
    // -------------------------------------------------------------------------

    @Test
    void close_fileIsReadableAfterClose() throws IOException {
        Path file = tempDir.resolve("output.csv");

        CsvWriter writer = new CsvWriter(file.toString());
        writer.writeRow(0.0, List.of(group(5, 10, 20)));
        writer.close();

        // Should be able to read without exception
        List<String> lines = writtenLines(file);
        assertFalse(lines.isEmpty(), "File should be readable and non-empty after close");
    }

    @Test
    void tryWithResources_fileIsWrittenCompletelyOnExit() throws IOException {
        Path file = tempDir.resolve("output.csv");

        try (CsvWriter writer = new CsvWriter(file.toString())) {
            writer.writeRow(0.0, List.of(group(10, 100, 200)));
        }

        List<String> lines = writtenLines(file);
        assertEquals(2, lines.size(), "File should have header + 1 data row after try-with-resources exits");
        assertEquals("0.000,100,200", lines.get(1));
    }
}