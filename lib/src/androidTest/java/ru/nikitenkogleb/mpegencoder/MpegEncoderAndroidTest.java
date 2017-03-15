package ru.nikitenkogleb.mpegencoder;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;

import java.io.File;
import java.nio.ByteBuffer;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicProtectedClassMembers;
import ru.nikitenkogleb.mpegencoder.MpegEncoder.Motion;

/**
 * The {@link MpegEncoder} Instrumentation Test.
 *
 * @author Nikitenko Gleb.
 * @since 1.0, 12/03/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("WeakerAccess, unused")
public class MpegEncoderAndroidTest extends BaseInstrumentationTest {

    /** Where to put the output file (note: /sdcard requires WRITE_EXTERNAL_STORAGE permission) */
    private static final File OUTPUT_DIR = Environment.getExternalStorageDirectory();

    /** The test output file name */
    private static final String OUTPUT_FILE_NAME = "output.mp4";

    /** H.264 Advanced Video Coding. */
    private static final String MIME_TYPE = "video/avc";

    /** The test width of video. */
    //private static final int WIDTH = 22;
    private static final int WIDTH = 352;
    /** The test height of video. */
    //private static final int HEIGHT = 16;
    private static final int HEIGHT = 288;
    /** The test frame-rate of video (8fps). */
    private static final int FRAME_RATE = 8;
    //private static final int FRAME_RATE = 16;

    /** The test iframe interval of video (1 second between I-frames). */
    private static final int IFRAME_INTERVAL = 1;

    /** The one seconds of video. */
    @SuppressWarnings("PointlessArithmeticExpression")
    private static final int NUM_FRAMES = FRAME_RATE * 1;

    /** The input buffer. */
    private static final ByteBuffer INPUT_BUFFER =
            ByteBuffer.allocate(WIDTH * HEIGHT * 2);

    /** Low motion by default. */
    @Motion
    private static final int MOTION = Motion.LOW;

    /** The video rotate. */
    private static final int ROTATE = 0;

    /** The output file. */
    @Nullable
    private File mOutputFile = null;

    /** Common "before" functionality. */
    @Before
    public final void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getContext();

        final File file = new File(context.getFilesDir()/*OUTPUT_DIR*/, OUTPUT_FILE_NAME);
        Assert.assertFalse(!file.createNewFile() && (!file.delete() || !file.createNewFile()));
        mOutputFile = file;
    }

    /** Common "after" functionality. */
    @After
    public final void tearDown() throws Exception {
        assert mOutputFile != null;
        Assert.assertTrue(mOutputFile.delete());
    }

    /**
     * Test for {@link MpegEncoder} creating and destroying.
     *
     * @throws Exception by some fails
     */
    @Test
    public final void testMain() throws Exception {
        assert mOutputFile != null;
        final String path = mOutputFile.getAbsolutePath();
        MpegEncoder
                .from(INPUT_BUFFER, WIDTH, HEIGHT)
                .fps(FRAME_RATE).motion(MOTION)
                .orientation(ROTATE)
                .to(path, WIDTH, HEIGHT)
                .close();
    }

    /**
     * Test for {@link MpegEncoder} drawing.
     *
     * @throws Exception by some fails
     */
    @Test
    public final void testDraw() throws Exception {

        assert mOutputFile != null;
        final String path = mOutputFile.getAbsolutePath();

        final Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);

        final MpegEncoder encoder =
                MpegEncoder
                        .from(INPUT_BUFFER, WIDTH, HEIGHT)
                        .fps(FRAME_RATE).motion(MOTION)
                        .orientation(ROTATE)
                        .to(path, WIDTH, HEIGHT);

        for (int i = 0; i < NUM_FRAMES; i++) {
            final int color = i * 3;
            bitmap.eraseColor(Color.rgb(255 - color, 0, color));
            INPUT_BUFFER.rewind();
            bitmap.copyPixelsToBuffer(INPUT_BUFFER);
            encoder.draw();
        }

        encoder.close();

        bitmap.recycle();
    }
}