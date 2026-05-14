import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EuclideanColorDistanceTest {

    private static final double EPSILON = 1e-9;
    private final EuclideanColorDistance distanceFinder = new EuclideanColorDistance();

    @Test
    public void testSameColorHasZeroDistance() {
        double distance = distanceFinder.distance(0x12AB34, 0x12AB34);
        assertEquals(0.0, distance, EPSILON);
    }

    @Test
    public void testOnlyRedDiffersBy255() {
        double distance = distanceFinder.distance(0xFF0000, 0x000000);
        assertEquals(255.0, distance, EPSILON);
    }

    @Test
    public void testBlackToWhiteDistance() {
        double distance = distanceFinder.distance(0x000000, 0xFFFFFF);
        assertEquals(Math.sqrt(3 * 255 * 255), distance, EPSILON);
    }

    @Test
    public void testDistanceIsSymmetric() {
        double forward = distanceFinder.distance(0x123456, 0x654321);
        double backward = distanceFinder.distance(0x654321, 0x123456);
        assertEquals(forward, backward, EPSILON);
    }

    @Test
    public void testKnownDistanceExample() {
        // |R|=15, |G|=25, |B|=45 => sqrt(15^2 + 25^2 + 45^2)
        double distance = distanceFinder.distance(0x0A141E, 0x192D4B);
        assertEquals(Math.sqrt(2875), distance, EPSILON);
    }

    @Test
    public void testAlphaChannelBitsAreIgnored() {
        // Same RGB values with different alpha bits should produce zero distance.
        double distance = distanceFinder.distance(0xFF336699, 0x00336699);
        assertEquals(0.0, distance, EPSILON);
    }
}