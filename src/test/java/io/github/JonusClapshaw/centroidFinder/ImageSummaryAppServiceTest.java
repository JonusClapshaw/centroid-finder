package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSummaryAppServiceTest {

    @TempDir
    Path tempDir;

    private Map<Path, byte[]> snapshotFiles(List<Path> paths) throws IOException {
        Map<Path, byte[]> snapshot = new HashMap<>();
        for (Path path : paths) {
            snapshot.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
        }
        return snapshot;
    }

    private void restoreFiles(Map<Path, byte[]> snapshot) throws IOException {
        for (Map.Entry<Path, byte[]> entry : snapshot.entrySet()) {
            Path path = entry.getKey();
            byte[] original = entry.getValue();
            if (original == null) {
                Files.deleteIfExists(path);
            } else {
                Files.write(path, original);
            }
        }
    }

    @Test
    void main_validInputs_writesExpectedGroupsAndBinarizedImage() throws IOException {
        Path inputImagePath = tempDir.resolve("input.png");
        BufferedImage input = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        input.setRGB(1, 2, 0x00FFFFFF);
        ImageIO.write(input, "png", inputImagePath.toFile());

        Path groupsCsvPath = Path.of("groups.csv");
        Path binarizedPath = Path.of("binarized.png");
        Map<Path, byte[]> snapshot = snapshotFiles(List.of(groupsCsvPath, binarizedPath));

        try {
            ImageSummaryApp.main(new String[]{inputImagePath.toString(), "FFFFFF", "1"});

            assertTrue(Files.exists(groupsCsvPath), "groups.csv should be created");
            assertTrue(Files.exists(binarizedPath), "binarized.png should be created");

            List<String> lines = Files.readAllLines(groupsCsvPath);
            assertEquals(List.of("1,1,2"), lines);

            BufferedImage binarized = ImageIO.read(binarizedPath.toFile());
            assertEquals(0x00FFFFFF, binarized.getRGB(1, 2) & 0x00FFFFFF);
            assertEquals(0x00000000, binarized.getRGB(0, 0) & 0x00FFFFFF);
        } finally {
            restoreFiles(snapshot);
        }
    }

    @Test
    void main_validInputs_overwritesExistingGroupsCsv() throws IOException {
        Path inputImagePath = tempDir.resolve("input.png");
        BufferedImage input = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        input.setRGB(0, 0, 0x00FFFFFF);
        ImageIO.write(input, "png", inputImagePath.toFile());

        Path groupsCsvPath = Path.of("groups.csv");
        Map<Path, byte[]> snapshot = snapshotFiles(List.of(groupsCsvPath));

        try {
            Files.writeString(groupsCsvPath, "stale-content");

            ImageSummaryApp.main(new String[]{inputImagePath.toString(), "FFFFFF", "1"});

            List<String> lines = Files.readAllLines(groupsCsvPath);
            assertFalse(lines.isEmpty());
            assertEquals("1,0,0", lines.get(0));
        } finally {
            restoreFiles(snapshot);
        }
    }
}
