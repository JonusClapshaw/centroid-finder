import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class CentroidFinderTest {

    private final DfsBinaryGroupFinder finder = new DfsBinaryGroupFinder();

    // -------------------------------------------------------------------------
    // findConnectedGroups tests
    // -------------------------------------------------------------------------

    @Test
    public void testSinglePixelGroup() {
        int[][] image = {
            {0, 0, 0},
            {0, 1, 0},
            {0, 0, 0}
        };
        List<Group> groups = finder.findConnectedGroups(image);
        assertEquals(1, groups.size());
        assertEquals(new Group(1, new Coordinate(1, 1)), groups.get(0));
    }

    @Test
    public void testNoGroups() {
        int[][] image = {
            {0, 0, 0},
            {0, 0, 0},
            {0, 0, 0}
        };
        List<Group> groups = finder.findConnectedGroups(image);
        assertTrue(groups.isEmpty());
    }

    @Test
    public void testAllOnes() {
        int[][] image = {
            {1, 1},
            {1, 1}
        };
        List<Group> groups = finder.findConnectedGroups(image);
        assertEquals(1, groups.size());
        assertEquals(new Group(4, new Coordinate(0, 0)), groups.get(0));
    }

    @Test
    public void testTwoSeparateGroups() {
        int[][] image = {
            {1, 0, 1},
            {1, 0, 1},
            {0, 0, 0}
        };
        // Left group: (0,0),(0,1) -> size=2, centroid=(0,0)
        // Right group: (2,0),(2,1) -> size=2, centroid=(2,0)
        List<Group> groups = finder.findConnectedGroups(image);
        assertEquals(2, groups.size());
    }

    @Test
    public void testDescendingOrderBySize() {
        int[][] image = {
            {1, 1, 0, 1},
            {1, 0, 0, 0},
            {0, 0, 0, 1}
        };
        // Large group (size 3) should come first, small groups after
        List<Group> groups = finder.findConnectedGroups(image);
        for (int i = 0; i < groups.size() - 1; i++) {
            assertTrue(groups.get(i).compareTo(groups.get(i + 1)) >= 0);
        }
    }

    @Test
    public void testNullImageThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> finder.findConnectedGroups(null));
    }

    @Test
    public void testNullRowThrowsNullPointerException() {
        int[][] image = {
            {1, 0},
            null
        };
        assertThrows(NullPointerException.class, () -> finder.findConnectedGroups(image));
    }

    @Test
    public void testSingleRow() {
        int[][] image = {{1, 1, 0, 1, 1, 1}};
        List<Group> groups = finder.findConnectedGroups(image);
        // Group 1: (0,0),(1,0) -> size=2, centroid=(0,0)
        // Group 2: (3,0),(4,0),(5,0) -> size=3, centroid=(4,0)
        assertEquals(2, groups.size());
        assertEquals(3, groups.get(0).size()); // larger group first
        assertEquals(2, groups.get(1).size());
    }

    @Test
    public void testSingleColumn() {
        int[][] image = {{1}, {1}, {0}, {1}};
        List<Group> groups = finder.findConnectedGroups(image);
        // Group 1: (0,0),(0,1) -> size=2, centroid=(0,0)
        // Group 2: (0,3) -> size=1, centroid=(0,3)
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).size()); // larger group first
        assertEquals(1, groups.get(1).size());
    }

    @Test
    public void testLargeIntricateMap() {
        int[][] image = {
            {0, 1, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 0, 0, 0, 1, 1, 1, 0},
            {0, 0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 1, 0, 1, 0, 0},
            {1, 0, 0, 1, 1, 0, 1, 1, 0},
            {0, 0, 0, 1, 1, 0, 0, 1, 0},
            {0, 1, 0, 0, 1, 1, 0, 1, 0},
            {0, 1, 1, 0, 0, 1, 0, 0, 0},
            {0, 1, 1, 0, 0, 1, 0, 0, 0}
        };
        List<Group> groups = finder.findConnectedGroups(image);
 
        // 6 separate connected groups
        assertEquals(6, groups.size());
 
        // Verify descending order throughout
        for (int i = 0; i < groups.size() - 1; i++) {
            assertTrue(groups.get(i).compareTo(groups.get(i + 1)) >= 0);
        }
 
        // Group 1 (largest): size=9, centroid=(4,5)
        assertEquals(new Group(9, new Coordinate(4, 5)), groups.get(0));
        // Group 2: size=5, centroid=(6,4) — ranked above group 3 due to higher x
        assertEquals(new Group(5, new Coordinate(6, 4)), groups.get(1));
        // Group 3: size=5, centroid=(1,7)
        assertEquals(new Group(5, new Coordinate(1, 7)), groups.get(2));
        // Group 4: size=3, centroid=(6,1)
        assertEquals(new Group(3, new Coordinate(6, 1)), groups.get(3));
        // Group 5: size=2, centroid=(1,0)
        assertEquals(new Group(2, new Coordinate(1, 0)), groups.get(4));
        // Group 6 (smallest): size=1, centroid=(0,4)
        assertEquals(new Group(1, new Coordinate(0, 4)), groups.get(5));
    }
}