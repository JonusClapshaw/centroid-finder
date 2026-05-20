package io.github.JonusClapshaw.centroidFinder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads frames from an MP4 video file.
 */
public class VideoFrameReader {

    private final String videoPath;

    public VideoFrameReader(String videoPath) {
        this.videoPath = videoPath;
    }

    /**
     * Reads all frames from the video file.
     * 
     * @return a list of BufferedImage frames
     * @throws IOException if the video file cannot be read
     */
    public List<BufferedImage> readAllFrames() throws IOException {
        List<BufferedImage> frames = new ArrayList<>();

        // Stub: Implement frame reading logic using JCodec
        // This will extract frames from the MP4 file
        System.out.println("[Stub] Reading frames from: " + videoPath);

        return frames;
    }

    /**
     * Validates that the video file exists and is readable.
     * 
     * @return true if the file is valid, false otherwise
     */
    public boolean isValidVideoFile() {
        File file = new File(videoPath);
        return file.exists() && file.isFile() && file.canRead();
    }
}