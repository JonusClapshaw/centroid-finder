import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BinarizingImageGroupFinderTest {

    /**
     * A fake ImageBinarizer that always returns a fixed binary array,
     * and records whether toBinaryArray was called and how many times.
     */
    private static class FakeImageBinarizer implements ImageBinarizer {
        private final int[][] binaryArray;
        int callCount = 0;

        FakeImageBinarizer(int[][] binaryArray) {
            this.binaryArray = binaryArray;
        }

        @Override
        public int[][] toBinaryArray(BufferedImage image) {
            callCount++;
            return binaryArray;
        }

        @Override
        public BufferedImage toBufferedImage(int[][] image) {
            throw new UnsupportedOperationException("Not needed for these tests");
        }
    }

    /**
     * A fake BinaryGroupFinder that always returns a fixed list of groups,
     * and records the last binary array it was called with.
     */
    private static class FakeBinaryGroupFinder implements BinaryGroupFinder {
        private final List<Group> groups;
        int[][] lastReceivedArray;

        FakeBinaryGroupFinder(List<Group> groups) {
            this.groups = groups;
        }

        @Override
        public List<Group> findConnectedGroups(int[][] image) {
            lastReceivedArray = image;
            return groups;
        }
    }

    // -------------------------------------------------------------------------
    // BinarizingImageGroupFinder tests
    // -------------------------------------------------------------------------

    @Test
    public void testDelegatesResultFromGroupFinder() {
        int[][] binaryArray = {
            {0, 1, 0},
            {1, 1, 0},
            {0, 0, 0}
        };
        List<Group> expected = List.of(new Group(3, new Coordinate(0, 1)));

        FakeImageBinarizer binarizer = new FakeImageBinarizer(binaryArray);
        FakeBinaryGroupFinder groupFinder = new FakeBinaryGroupFinder(expected);
        BinarizingImageGroupFinder finder = new BinarizingImageGroupFinder(binarizer, groupFinder);

        List<Group> result = finder.findConnectedGroups(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB));

        assertEquals(expected, result);
    }

    @Test
    public void testBinaryArrayIsPassedToGroupFinder() {
        int[][] binaryArray = {
            {1, 0},
            {0, 1}
        };

        FakeImageBinarizer binarizer = new FakeImageBinarizer(binaryArray);
        FakeBinaryGroupFinder groupFinder = new FakeBinaryGroupFinder(List.of());
        BinarizingImageGroupFinder finder = new BinarizingImageGroupFinder(binarizer, groupFinder);

        finder.findConnectedGroups(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        assertSame(binaryArray, groupFinder.lastReceivedArray);
    }

    @Test
    public void testToBinaryArrayIsCalledOnce() {
        int[][] binaryArray = {{0, 0}, {0, 0}};

        FakeImageBinarizer binarizer = new FakeImageBinarizer(binaryArray);
        FakeBinaryGroupFinder groupFinder = new FakeBinaryGroupFinder(List.of());
        BinarizingImageGroupFinder finder = new BinarizingImageGroupFinder(binarizer, groupFinder);

        finder.findConnectedGroups(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB));

        assertEquals(1, binarizer.callCount);
    }

    @Test
    public void testReturnsEmptyListWhenNoGroupsFound() {
        int[][] binaryArray = {
            {0, 0, 0},
            {0, 0, 0},
            {0, 0, 0}
        };

        FakeImageBinarizer binarizer = new FakeImageBinarizer(binaryArray);
        FakeBinaryGroupFinder groupFinder = new FakeBinaryGroupFinder(List.of());
        BinarizingImageGroupFinder finder = new BinarizingImageGroupFinder(binarizer, groupFinder);

        List<Group> result = finder.findConnectedGroups(new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB));

        assertTrue(result.isEmpty());
    }

    @Test
    public void testReturnsMultipleGroupsInOrder() {
        int[][] binaryArray = {
            {1, 0, 0, 0, 1},
            {1, 0, 0, 0, 1},
            {1, 0, 0, 0, 0},
            {0, 0, 0, 0, 0},
            {0, 0, 1, 0, 0}
        };
        List<Group> expected = List.of(
            new Group(3, new Coordinate(0, 1)),
            new Group(2, new Coordinate(4, 0)),
            new Group(1, new Coordinate(2, 4))
        );

        FakeImageBinarizer binarizer = new FakeImageBinarizer(binaryArray);
        FakeBinaryGroupFinder groupFinder = new FakeBinaryGroupFinder(expected);
        BinarizingImageGroupFinder finder = new BinarizingImageGroupFinder(binarizer, groupFinder);

        List<Group> result = finder.findConnectedGroups(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB));

        assertEquals(3, result.size());
        assertEquals(expected, result);
    }
}