import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DistanceImageBinarizerTest {

    private static class SpyColorDistanceFinder implements ColorDistanceFinder {
        private final List<int[]> calls = new ArrayList<>();
        private final List<Double> scriptedResults = new ArrayList<>();

        public void scriptResult(double value) {
            scriptedResults.add(value);
        }

        public List<int[]> getCalls() {
            return calls;
        }

        @Override
        public double distance(int colorA, int colorB) {
            calls.add(new int[] {colorA, colorB});
            if (scriptedResults.isEmpty()) {
                throw new IllegalStateException("No scripted distance available for call " + calls.size());
            }
            return scriptedResults.remove(0);
        }
    }

    @Test
    public void testToBinaryArrayUsesDistanceFinderForEachPixel() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        spy.scriptResult(10.0);
        spy.scriptResult(100.0);
        spy.scriptResult(1.0);
        spy.scriptResult(99.0);

        int targetColor = 0x112233;
        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, targetColor, 50);

        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00111111);
        image.setRGB(1, 0, 0x00222222);
        image.setRGB(0, 1, 0x00333333);
        image.setRGB(1, 1, 0x00444444);

        int[][] binary = binarizer.toBinaryArray(image);

        assertNotNull(binary);
        assertEquals(2, binary.length);
        assertEquals(2, binary[0].length);
        assertEquals(4, spy.getCalls().size());

        // Expected threshold behavior (< threshold => 1, otherwise 0).
        int[][] expected = {
            {1, 0},
            {1, 0}
        };
        assertArrayEquals(expected, binary);

        // Verify target color is passed for every call.
        for (int[] call : spy.getCalls()) {
            assertEquals(targetColor, call[1]);
        }
    }

    @Test
    public void testToBinaryArrayMasksOutAlphaBitsBeforeDistanceCalculation() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        spy.scriptResult(0.0);

        int targetColor = 0xABCDEF;
        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, targetColor, 10);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF123456);

        binarizer.toBinaryArray(image);

        assertEquals(1, spy.getCalls().size());
        int[] firstCall = spy.getCalls().get(0);
        assertEquals(0x123456, firstCall[0]);
        assertEquals(targetColor, firstCall[1]);
    }

    @Test
    public void testToBufferedImageConvertsZeroToBlackAndOneToWhite() {
        DistanceImageBinarizer binarizer = new DistanceImageBinarizer((a, b) -> 0.0, 0x000000, 1);

        int[][] binary = {
            {0, 1},
            {1, 0}
        };

        BufferedImage image = binarizer.toBufferedImage(binary);

        assertNotNull(image);
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());

        assertEquals(0x000000, image.getRGB(0, 0) & 0x00FFFFFF);
        assertEquals(0x00FFFFFF, image.getRGB(1, 0) & 0x00FFFFFF);
        assertEquals(0x00FFFFFF, image.getRGB(0, 1) & 0x00FFFFFF);
        assertEquals(0x000000, image.getRGB(1, 1) & 0x00FFFFFF);
    }

    @Test
    public void testToBinaryArrayUsesStrictlyLessThanThreshold() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        spy.scriptResult(49.999);
        spy.scriptResult(50.0);
        spy.scriptResult(50.001);

        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, 0x000000, 50);

        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00010101);
        image.setRGB(1, 0, 0x00020202);
        image.setRGB(2, 0, 0x00030303);

        int[][] binary = binarizer.toBinaryArray(image);
        int[][] expected = {{1, 0, 0}};
        assertArrayEquals(expected, binary);
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    public void testSinglePixelImageBelowThreshold() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        spy.scriptResult(0.0);

        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, 0xFF0000, 1);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);

        int[][] binary = binarizer.toBinaryArray(image);

        assertEquals(1, binary.length);
        assertEquals(1, binary[0].length);
        assertEquals(1, binary[0][0]);
    }

    @Test
    public void testSinglePixelImageAboveThreshold() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        spy.scriptResult(200.0);

        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, 0xFF0000, 50);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x0000FF);

        int[][] binary = binarizer.toBinaryArray(image);

        assertEquals(0, binary[0][0]);
    }

    @Test
    public void testAllPixelsBelowThresholdProducesAllOnes() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        for (int i = 0; i < 4; i++) spy.scriptResult(0.0);

        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, 0x000000, 100);

        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        int[][] binary = binarizer.toBinaryArray(image);
        int[][] expected = {{1, 1}, {1, 1}};
        assertArrayEquals(expected, binary);
    }

    @Test
    public void testAllPixelsAboveThresholdProducesAllZeros() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        for (int i = 0; i < 4; i++) spy.scriptResult(999.0);

        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, 0xFFFFFF, 100);

        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        int[][] binary = binarizer.toBinaryArray(image);
        int[][] expected = {{0, 0}, {0, 0}};
        assertArrayEquals(expected, binary);
    }

    @Test
    public void testZeroThresholdAlwaysProducesBlack() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        // Even a distance of 0 is not < 0, so every pixel should be 0.
        spy.scriptResult(0.0);

        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, 0x000000, 0);

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);

        int[][] binary = binarizer.toBinaryArray(image);

        assertEquals(0, binary[0][0]);
    }

    @Test
    public void testNonSquareImageDimensionsArePreserved() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        for (int i = 0; i < 12; i++) spy.scriptResult(0.0);

        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, 0x000000, 10);

        // 4 wide, 3 tall
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);

        int[][] binary = binarizer.toBinaryArray(image);

        assertEquals(3, binary.length);
        assertEquals(4, binary[0].length);
    }

    @Test
    public void testToBufferedImageSinglePixelBlack() {
        DistanceImageBinarizer binarizer = new DistanceImageBinarizer((a, b) -> 0.0, 0x000000, 1);

        int[][] binary = {{0}};

        BufferedImage image = binarizer.toBufferedImage(binary);

        assertNotNull(image);
        assertEquals(1, image.getWidth());
        assertEquals(1, image.getHeight());
        assertEquals(0x000000, image.getRGB(0, 0) & 0x00FFFFFF);
    }

    @Test
    public void testToBufferedImageSinglePixelWhite() {
        DistanceImageBinarizer binarizer = new DistanceImageBinarizer((a, b) -> 0.0, 0x000000, 1);

        int[][] binary = {{1}};

        BufferedImage image = binarizer.toBufferedImage(binary);

        assertNotNull(image);
        assertEquals(0x00FFFFFF, image.getRGB(0, 0) & 0x00FFFFFF);
    }

    @Test
    public void testToBufferedImageDimensionsMatchInputArray() {
        DistanceImageBinarizer binarizer = new DistanceImageBinarizer((a, b) -> 0.0, 0x000000, 1);

        // 5 wide, 2 tall
        int[][] binary = {
            {0, 1, 0, 1, 0},
            {1, 0, 1, 0, 1}
        };

        BufferedImage image = binarizer.toBufferedImage(binary);

        assertNotNull(image);
        assertEquals(5, image.getWidth());
        assertEquals(2, image.getHeight());
    }

    @Test
    public void testDistanceFinderIsCalledWithTargetColorForEachPixelInNonSquareImage() {
        SpyColorDistanceFinder spy = new SpyColorDistanceFinder();
        for (int i = 0; i < 6; i++) spy.scriptResult(0.0);

        int targetColor = 0xAA1122;
        DistanceImageBinarizer binarizer = new DistanceImageBinarizer(spy, targetColor, 10);

        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);

        binarizer.toBinaryArray(image);

        assertEquals(6, spy.getCalls().size());
        for (int[] call : spy.getCalls()) {
            assertEquals(targetColor, call[1]);
        }
    }
}