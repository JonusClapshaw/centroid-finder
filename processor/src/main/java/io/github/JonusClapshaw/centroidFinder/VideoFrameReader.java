package io.github.JonusClapshaw.centroidFinder;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jcodec.api.JCodecException;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.api.awt.AWTFrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.scale.AWTUtil;

/**
 * Reads frames from an MP4 video file.
 */
public class VideoFrameReader {
    @FunctionalInterface
    interface FrameConsumer {
        void accept(BufferedImage frame, int frameIndex) throws IOException;
    }

    @FunctionalInterface
    interface TimestampedFrameConsumer {
        void accept(BufferedImage frame, int frameIndex, double timestampSeconds) throws IOException;
    }

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
        readFrames((frame, frameIndex) -> frames.add(frame));
        return frames;
    }

    double estimateFramesPerSecond() throws IOException {
        File videoFile = new File(videoPath);
        if (!isValidVideoFile()) {
            throw new IOException("Video file does not exist or cannot be read: " + videoPath);
        }

        try (SeekableByteChannel channel = NIOUtils.readableChannel(videoFile)) {
            AWTFrameGrab frameGrab = AWTFrameGrab.createAWTFrameGrab(channel);
            DemuxerTrackMeta meta = frameGrab.getVideoTrack().getMeta();
            int totalFrames = meta.getTotalFrames();
            double totalDuration = meta.getTotalDuration();

            if (totalFrames <= 0 || totalDuration <= 0.0) {
                return 1.0;
            }
            return totalFrames / totalDuration;
        } catch (JCodecException exception) {
            throw new IOException("Unable to decode video: " + videoPath, exception);
        }
    }

    void readFrames(FrameConsumer frameConsumer) throws IOException {
        readFramesWithTimestamps((frame, frameIndex, timestampSeconds) -> frameConsumer.accept(frame, frameIndex));
    }

    void readFramesWithTimestamps(TimestampedFrameConsumer frameConsumer) throws IOException {
        File videoFile = new File(videoPath);
        if (!isValidVideoFile()) {
            throw new IOException("Video file does not exist or cannot be read: " + videoPath);
        }

        try (SeekableByteChannel channel = NIOUtils.readableChannel(videoFile)) {
            AWTFrameGrab frameGrab = AWTFrameGrab.createAWTFrameGrab(channel);
            double nextSampleTimestamp = 0.0;
            double firstTimestamp = -1.0;
            int frameIndex = 0;
            PictureWithMetadata frame;


            while (true) {
                long grabStart = System.currentTimeMillis();
                frame = frameGrab.getNativeFrameWithMetadata();

                if (frame == null) break;

                long decodeStart = System.currentTimeMillis();
                double timestamp = frame.getTimestamp();
                if (firstTimestamp < 0.0) {
                    firstTimestamp = timestamp;
                }
                double relativeTimestamp = timestamp - firstTimestamp;

                if (relativeTimestamp + 1e-9 >= nextSampleTimestamp) {
                    long processStart = System.currentTimeMillis();
                    frameConsumer.accept(AWTUtil.toBufferedImage(frame.getPicture()), frameIndex, relativeTimestamp);
                    nextSampleTimestamp = relativeTimestamp + 1.0;
                }

                frameIndex++;
            }
        } catch (JCodecException exception) {
            throw new IOException("Unable to decode video: " + videoPath, exception);
        }
    }

    /**
     * Validates that the video file exists and is readable.
     * 
     * @return true if the file is valid, false otherwise
     */
    public boolean isValidVideoFile() {
        File file = new File(videoPath);
        return file.exists() && file.isFile() && file.canRead() && file.length() > 0;
    }
}