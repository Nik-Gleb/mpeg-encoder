package ru.nikitenkogleb.mpegencoder;

import android.graphics.PointF;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicClassMembers;
import proguard.annotation.KeepPublicProtectedClassMembers;

/**
 * The simple Mpeg encoder.
 *
 * @author Nikitenko Gleb
 * @since 1.0, 12/03/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("WeakerAccess, unused")
public final class MpegEncoder implements Closeable {

    /** The log-cat tag. */
    private static final String TAG = "MpegEncoder";

    /** Verbose mode logging. */
    private static final boolean VERBOSE =
            BuildConfig.DEBUG || Log.isLoggable(TAG, Log.VERBOSE);
    /** Warning mode logging. */
    private static final boolean WARNINGS =
            BuildConfig.DEBUG || Log.isLoggable(TAG, Log.WARN);

    /** H.264 Advanced Video Coding. */
    private static final String MIME_TYPE = "video/avc";

    /** The encoder data waiting timeout. */
    private static final int TIMEOUT_ENCODER_WAIT = 1000;

    /** No output buffer. */
    private static final int INFO_NO_OUTPUT_AVAILABLE_YET = -4;

    /** The calc bitrate factor. */
    private static final float FACTOR = 0.07f;

    /** The media codec instance. */
    @NonNull
    private final MediaCodec mEncoder;

    /** The source surface/ */
    @NonNull
    private final Surface mSurface;

    /** The input surface. */
    @NonNull
    private final InputSurface mInputSurface;

    /** The media muxer. */
    @NonNull
    private final MediaMuxer mMuxer;

    /** Allocate one of these up front so we don't need to do it every time. */
    @NonNull
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();

    /** The output buffer. */
    @NonNull
    private final ByteBuffer[] mOutputBuffers;

    /** The frame rate of the video. */
    private final int mFrameRate;

    /** The frame index. */
    private int mFrameIndex = 0;

    /** Video Track Id. */
    private int mTrackId = -1;

    /** The object was released. */
    private boolean mReleased;

    /** Constructs a new {@link MpegEncoder} */
    private MpegEncoder(@NonNull Builder builder, @NonNull MediaFormat format,
            @NonNull String path) throws IOException {
        checkState();

        mFrameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE);

        mEncoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mSurface = mEncoder.createInputSurface();
        mInputSurface =
                InputSurface.create (
                        mSurface,
                        builder.inputBuffer,
                        builder.width,
                        builder.height
                )
                        .autoSwap()
                        .build();

        mMuxer = new MediaMuxer(path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        if (builder.mLocation != null) {
            mMuxer.setLocation(builder.mLocation.x, builder.mLocation.y);
        }
        if (builder.mOrientation != 0) {
            mMuxer.setOrientationHint(builder.mOrientation);
        }

        mOutputBuffers = start();
    }

    /** {@inheritDoc} */
    @Override
    public final void close() {
        checkState();
        stop();
        if (mTrackId != -1) {
            mTrackId = -1;
            mMuxer.stop();
        }
        mMuxer.release();
        mInputSurface.close();
        mSurface.release();
        mEncoder.release();
        mReleased = true;
    }

    /** Start encoding. */
    @SuppressWarnings("deprecation")
    private ByteBuffer[] start() {
        mEncoder.start();
        return mEncoder.getOutputBuffers();
    }

    /** Stop encoding. */
    private void stop() {
        if (mTrackId != -1)
            drainEncoder(true);
        mEncoder.stop();
    }

    /**
     * Called to draw the current frame.
     * <p>
     * This method is responsible for drawing the current frame.
     */
    public final void draw() {
        drainEncoder(false);
        final long presentationTime = mFrameIndex++ * 1000000000L / mFrameRate;
        mInputSurface.draw(presentationTime);
    }

    /**
     * Extracts all pending data from the encoder.
     * <p>
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     */
    @SuppressWarnings("deprecation")
    private void drainEncoder(boolean eos) {
        checkState();

        //logv("Drain encoder: " + eos);
        if (eos) {
            //logv("Sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        int encoderStatus;
        while ((encoderStatus = getEncoderStatus(eos)) != MediaCodec.INFO_TRY_AGAIN_LATER) {
            switch (encoderStatus) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    throw new RuntimeException("Output buffers changed twice");
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    if (mTrackId != -1) {
                        throw new RuntimeException("Format changed twice");
                    }
                    // Now that we have the Magic Goodies, start the muxer
                    final MediaFormat format = mEncoder.getOutputFormat();
                    //logv("Encoder output format changed: " + format);
                    mTrackId = mMuxer.addTrack(format);
                    mMuxer.start();
                    break;
                default:
                    if (encoderStatus >= 0) {
                        final ByteBuffer encodedData = mOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            throw new RuntimeException (
                                    "EncoderOutputBuffer " + encoderStatus + " was null"
                            );
                        }
                        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // The codec config data was pulled out and fed to the muxer when we got
                            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                            //logv("ignoring BUFFER_FLAG_CODEC_CONFIG");
                            mBufferInfo.size = 0;
                        }
                        if (mBufferInfo.size != 0) {
                            if (mTrackId == -1) {
                                throw new RuntimeException("Muxer hasn't started");
                            }
                            // Adjust the ByteBuffer values to match BufferInfo (not needed?)
                            encodedData.position(mBufferInfo.offset);
                            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                            mMuxer.writeSampleData(mTrackId, encodedData, mBufferInfo);
                            //logv("Sent " + mBufferInfo.size + " bytes to muxer");
                        }
                        mEncoder.releaseOutputBuffer(encoderStatus, false);
                    } else {
                        //noinspection StatementWithEmptyBody
                        if (encoderStatus != INFO_NO_OUTPUT_AVAILABLE_YET) {
                            logw("unexpected encoder status: " + encoderStatus);
                        }
                    }
                    break;
            }
        }
    }

    /** @return true when wile should be handled */
    private int getEncoderStatus(boolean endOfStream) {
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            //logv(endOfStream ? "End of stream reached" : "reached end of stream unexpectedly");
            return MediaCodec.INFO_TRY_AGAIN_LATER;
        } else {
            final int status = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_ENCODER_WAIT);
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (endOfStream) {
                    //logv("No output available, spinning to await EOS");
                    return INFO_NO_OUTPUT_AVAILABLE_YET;
                } else {
                    return MediaCodec.INFO_TRY_AGAIN_LATER;
                }
            } else {
                return status;
            }
        }
    }

    /** {@inheritDoc} */
    protected final void finalize() throws Throwable {
        try {
            if (!mReleased) {
                close();
                throw new RuntimeException (
                        "\nA resource was acquired at attached stack trace but never released." +
                                "\nSee java.io.Closeable for info on avoiding resource leaks."
                );
            }
        } finally {
            super.finalize();
        }
    }

    /**
     * Calculate the optimal bitrate.
     *
     * @param area the frame area
     * @param rate the frame rate
     * @param motion the video motion
     *
     * @return optimal bitrate
     */
    private static int calcBitRate(int area, int rate, @Motion int motion) {
        return Math.round(area * rate * motion * FACTOR);
    }

    /** Check current state. */
    private void checkState() {
        if (mReleased) {
            throw new IllegalStateException();
        }
    }

    /** Log verbose. */
    private static void logv(@NonNull String msg) {
        log(Log.VERBOSE, VERBOSE, msg);
    }

    /** Log warning. */
    private static void logw(@NonNull String msg) {
        log(Log.WARN, WARNINGS, msg);
    }

    /** Log warning. */
    private static void logw(@NonNull Throwable throwable) {
        log(Log.WARN, WARNINGS, Log.getStackTraceString(throwable));
    }

    /**
     * Common log-helper.
     *
     * @param level the log-level
     * @param enable log-enabled flag
     * @param msg the message
     */
    private static void log(int level, boolean enable, @NonNull String msg) {
        if (enable) {
            Log.println(level, TAG, msg);
        }
    }

    /**
     * Create a {@link Builder} suitable for building a {@link MpegEncoder}.
     *
     * @param buffer the input buffer
     * @param width The width of the content (in pixels)
     * @param height The height of the content (in pixels)
     *
     * @return a {@link Builder}
     */
    @NonNull
    public static Builder from(@NonNull ByteBuffer buffer, int width, int height) {
        return new Builder(buffer, width, height);
    }

    /**
     * Used to add parameters to a {@link MpegEncoder}.
     *
     * The {@link Builder} is first created by calling
     * {@link #from(ByteBuffer, int, int)}.
     *
     * The where methods can then be used to add parameters to the builder.
     * See the specific methods to find for which {@link Builder} type each is allowed.
     * Call {@link #to(String, int, int)} to from the {@link MpegEncoder}
     * once all the
     * parameters have been supplied.
     *
     * @author Nikitenko Gleb
     * @since 1.0, 07/03/2017
     */
    @Keep
    @KeepPublicProtectedClassMembers
    @SuppressWarnings("WeakerAccess, unused")
    public static final class Builder {

        /** The input buffer. */
        @NonNull
        final ByteBuffer inputBuffer;
        /** The horizontal size of input frames. */
        final int width;
        /** The vertical size of input frames. */
        final int height;

        /** The frame-rate of video. */
        private int mFPS = 15;
        /** The i-frame interval of video. */
        private int mIFrame = 1;

        /** The motion of video. */
        @Motion
        private int mMotion = Motion.LOW;
        /** The location. */
        private PointF mLocation = null;
        /** The orientation. */
        private int mOrientation = 0;

        /**
         * Constructs a new {@link Builder}.
         *
         * @param input the input buffer
         * @param width The width of the content (in pixels)
         * @param height The height of the content (in pixels)
         */
        private Builder(@NonNull ByteBuffer input, int width, int height) {
            this.width = width;
            this.height = height;
            this.inputBuffer = input;
        }

        /**
         * Set and store the geodata (latitude and longitude) in the output file.
         * The geodata is stored in udta box if the output format is
         * {@link MediaMuxer.OutputFormat#MUXER_OUTPUT_MPEG_4}, and is ignored for
         * other output formats. The geodata is stored according to ISO-6709 standard.
         *
         * @param latitude Latitude in degrees. Its value must be in the range [-90, 90].
         * @param longitude Longitude in degrees. Its value must be in the range [-180, 180].
         * @return this builder, to allow for chaining.
         */
        @NonNull
        public final Builder location(float latitude, float longitude) {
            mLocation = new PointF(latitude, longitude);
            return this;
        }

        /**
         * Sets the orientation hint for output video playback.
         * Calling this
         * method will not rotate the video frame when muxer is generating the file,
         * but add a composition matrix containing the rotation angle in the output
         * video if the output format is
         * {@link MediaMuxer.OutputFormat#MUXER_OUTPUT_MPEG_4} so that a video player can
         * choose the proper orientation for playback. Note that some video players
         * may choose to ignore the composition matrix in a video during playback.
         * By default, the rotation degree is 0.</p>
         * @param degrees the angle to be rotated clockwise in degrees.
         *                The supported angles are 0, 90, 180, and 270 degrees.
         * @return this builder, to allow for chaining.
         */
        @NonNull
        public final Builder orientation(int degrees) {
            mOrientation = degrees;
            return this;
        }

        /** The frame-rate of video. */
        @NonNull
        public final Builder fps(int fps) {
            mFPS = fps;
            return this;
        }

        /** The i-frame interval in seconds. */
        @NonNull
        public final Builder iFrame(int sec) {
            mIFrame = sec;
            return this;
        }

        /** The motion-mode of video. */
        @NonNull
        public final Builder motion(@Motion int motion) {
            mMotion = motion;
            return this;
        }

        /** Create a {@link MpegEncoder} from this {@link Builder}. */
        @NonNull
        public final MpegEncoder to(@NonNull String path, int width, int height)  {

            final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE,
                    calcBitRate(width * height, mFPS, mMotion));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, mFPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIFrame);

            try {
                return new MpegEncoder(this, format, path);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    /**
     * Predefined terminate result codes.
     *
     * @author Nikitenko Gleb
     * @since 1.0, 06/10/2016
     */
    @IntDef({Motion.LOW, Motion.MEDIUM, Motion.HIGH, Motion.EXTRA_HIGH})
    @Retention(RetentionPolicy.SOURCE)
    @Keep@KeepPublicClassMembers
    @SuppressWarnings("WeakerAccess, unused")
    public @interface Motion {
        /** Low motion. */
        int LOW         = 1;
        /** Medium motion. */
        int MEDIUM      = 2;
        /** High motion. */
        int HIGH        = 3;
        /** Extra high motion. */
        int EXTRA_HIGH  = 4;
    }

}
