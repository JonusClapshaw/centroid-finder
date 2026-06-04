package io.github.JonusClapshaw.centroidFinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSummaryAppCliTest {

    @TempDir
    Path tempDir;

    @Test
    void main_whenMissingArgs_printsUsageAndReturns() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(out));

            ImageSummaryApp.main(new String[]{"only-one-arg"});

            String output = out.toString();
            assertTrue(output.contains("Usage: java ImageSummaryApp <input_image> <hex_target_color> <threshold>"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void main_whenThresholdIsInvalid_printsErrorAndReturns() throws IOException {
        Path inputImagePath = tempDir.resolve("input.png");
        BufferedImage input = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(input, "png", inputImagePath.toFile());

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(err));

            ImageSummaryApp.main(new String[]{inputImagePath.toString(), "FFFFFF", "not-an-int"});

            String errorOutput = err.toString();
            assertTrue(errorOutput.contains("Threshold must be an integer."));
        } finally {
            System.setErr(originalErr);
        }
    }

    @Test
    void main_whenHexColorIsInvalid_printsErrorAndReturns() throws IOException {
        Path inputImagePath = tempDir.resolve("input.png");
        BufferedImage input = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(input, "png", inputImagePath.toFile());

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream originalErr = System.err;
        try {
            System.setErr(new PrintStream(err));

            ImageSummaryApp.main(new String[]{inputImagePath.toString(), "ZZZZZZ", "1"});

            String errorOutput = err.toString();
            assertTrue(errorOutput.contains("Invalid hex target color. Please provide a color in RRGGBB format."));
        } finally {
            System.setErr(originalErr);
        }
    }
}
