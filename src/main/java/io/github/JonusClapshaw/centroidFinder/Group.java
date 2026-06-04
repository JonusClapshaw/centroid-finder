package io.github.JonusClapshaw.centroidFinder;

/**
 * Represents a group of contiguous pixels in an image.
 * The top-left cell of the array (row:0, column:0) is considered to be coordinate (x:0, y:0).
 * Y increases downward and X increases to the right. For example, (row:4, column:7)
 * corresponds to (x:7, y:4).
 * 
 * A group's size indicates the number of pixels in the group.
 * The centroid of the group is computed as the average of the pixel coordinates in each
 * dimension using integer division.
 * This means the x coordinate of the centroid is the sum of all x values divided by the
 * number of pixels in the group, and similarly for the y coordinate.
 * 
 * Groups are naturally ordered in ASCENDING order by this class's {@link #compareTo} method:
 * size first, then x coordinate of the centroid, then y coordinate of the centroid.
 * Callers that want the canonical descending presentation (largest group first, ties broken
 * by descending x then descending y) must reverse this order, for example via
 * {@code groups.sort((a, b) -> b.compareTo(a))} or {@code Comparator.reverseOrder()}.
 */
public record Group(int size, Coordinate centroid) implements Comparable<Group> {

    /**
     * Compares this group with the specified group for order, in ASCENDING natural order.
     * The comparison is based first on size (smaller size = smaller group), then on the
     * x coordinate of the centroid (smaller x = smaller group), and finally on the y
     * coordinate of the centroid (smaller y = smaller group).
     *
     * <p>The canonical presentation order for a list of groups is the reverse of this:
     * largest size first, ties broken by descending x, then descending y.
     * Reverse this comparator to achieve that order.
     *
     * @param other the group to be compared with this group
     * @return a negative integer, zero, or a positive integer if this group is less than,
     *         equal to, or greater than the specified group
     */
    @Override
    public int compareTo(Group other) {
        int comp = Integer.compare(this.size(), other.size());
        if (comp != 0) {
            return comp;
        }
        comp = Integer.compare(this.centroid().x(), other.centroid().x());
        if (comp != 0) {
            return comp;
        }
        return Integer.compare(this.centroid().y(), other.centroid().y());
    }

    /**
     * Returns a string representing this group in comma-separated values (CSV) format.
     * The format is "size,x,y", where size is the group's size and x and y are the
     * centroid coordinates.
     *
     * @return a CSV row string representing the group's size and centroid coordinates
     */
    public String toCsvRow() {
        return String.format("%d,%d,%d", this.size(), this.centroid().x(), this.centroid().y());
    }
}
