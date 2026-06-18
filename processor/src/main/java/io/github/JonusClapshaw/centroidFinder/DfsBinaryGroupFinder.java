package io.github.JonusClapshaw.centroidFinder;

import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;

public class DfsBinaryGroupFinder implements BinaryGroupFinder {
   /**
    * Finds connected pixel groups of 1s in an integer array representing a binary image.
    * 
    * The input is a non-empty rectangular 2D array containing only 1s and 0s.
    * If the array or any of its subarrays are null, a NullPointerException
    * is thrown. If the array is otherwise invalid, an IllegalArgumentException
    * is thrown.
    *
    * Pixels are considered connected vertically and horizontally, NOT diagonally.
    * The top-left cell of the array (row:0, column:0) is considered to be coordinate
    * (x:0, y:0). Y increases downward and X increases to the right. For example,
    * (row:4, column:7) corresponds to (x:7, y:4).
    *
    * The method returns a list of sorted groups. The group's size is the number 
    * of pixels in the group. The centroid of the group
    * is computed as the average of each of the pixel locations across each dimension.
    * For example, the x coordinate of the centroid is the sum of all the x
    * coordinates of the pixels in the group divided by the number of pixels in that group.
    * Similarly, the y coordinate of the centroid is the sum of all the y
    * coordinates of the pixels in the group divided by the number of pixels in that group.
    * The division should be done as INTEGER DIVISION.
    *
    * The groups are sorted in DESCENDING order according to Group's compareTo method.
    * 
    * @param image a rectangular 2D array containing only 1s and 0s
    * @return the found groups of connected pixels in descending order
    */
    @Override
    public List<Group> findConnectedGroups(int[][] image) {
        if (image == null) throw new NullPointerException();
        if (image.length == 0) throw new IllegalArgumentException();
        if (image[0] == null) throw new NullPointerException();
        if (image[0].length == 0) throw new IllegalArgumentException();
        int width = image[0].length;
        for (int[] row : image) {
            if (row == null) throw new NullPointerException();
            if (row.length != width) throw new IllegalArgumentException();
        }
        boolean[][] visited = new boolean[image.length][width];
        List<Group> groups = new ArrayList<>();
        for (int row = 0; row < image.length; row++) {
            for (int col = 0; col < image[row].length; col++) {
                if (image[row][col] == 1 && !visited[row][col]) {
                    groups.add(dfs(row, col, image, visited));
                }
            }
        }
        groups.sort((a, b) -> b.compareTo(a));
        return groups;
    }

    private static Group dfs(int curR, int curC, int[][] image, boolean[][] visited) {
        int[] stats = new int[3]; // [sumX, sumY, size]
        collectPixels(curR, curC, image, visited, stats);
        int size = stats[2];
        return new Group(size, new Coordinate(stats[0] / size, stats[1] / size));
    }

    private static final int[][] MOVES = new int[][] {
        {-1, 0}, // UP
        {1, 0},  // DOWN
        {0, -1}, // LEFT
        {0, 1}   // RIGHT
    };

    private static void collectPixels(int startRow, int startCol, int[][] image, boolean[][] visited, int[] stats) {
    Deque<int[]> stack = new ArrayDeque<>();
    stack.push(new int[]{startRow, startCol});

    while (!stack.isEmpty()) {
        int[] current = stack.pop();
        int row = current[0];
        int col = current[1];

        if (row < 0 || row >= image.length || col < 0 || col >= image[0].length
                || visited[row][col] || image[row][col] == 0) {
            continue;
        }

        visited[row][col] = true;
        stats[0] += col; // sumX
        stats[1] += row; // sumY
        stats[2]++;      // size

        for (int[] move : MOVES) {
            stack.push(new int[]{row + move[0], col + move[1]});
        }
    }
}
    
}
