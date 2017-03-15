package ru.nikitenkogleb.mpegencoder;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicProtectedClassMembers;

/**
 * The {@link InputSurface} Instrumentation Test.
 *
 * @author Nikitenko Gleb
 * @since 1.0, 13/03/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("WeakerAccess, unused")
public class InputSurfaceAndroidTest extends BaseInstrumentationTest {

    /** Default texture level. */
    private static final int TEXTURE_LEVEL = GLES20.GL_TEXTURE1;

    /** The test frame size. */
    private static final int FRAME_SIZE = 2;
    /** The test presentation time. */
    private static final int PRESENTATION_TIME = 1000000;

    /** The EGL Surface. */
    @NonNull
    private int[] mShader = new int[5];

    /** The EGL Display. */
    @Nullable
    private EGLDisplay mEglDisplay = null;

    /** The EGL Context. */
    @Nullable
    private EGLContext mEglContext = null;

    /** The EGL Surface. */
    @Nullable
    private EGLSurface mEglSurface = null;

    /** The Surface Texture. */
    @Nullable
    private SurfaceTexture mSurfaceTexture = null;

    /** The Surface. */
    @Nullable
    private Surface mSurface = null;

    /** The GL-Texture. */
    private int mTexture;

    /** Common "before" functionality. */
    @Before
    public final void setUp() throws Exception {
        mEglDisplay = GLTools.newDisplay();

        final EGLConfig eglConfig = GLTools.newConfig(mEglDisplay, true);
        mEglContext = GLTools.newContext(mEglDisplay, eglConfig);
        mEglSurface = GLTools.newSurface(mEglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE);
        GLTools.makeCurrent(mEglDisplay, mEglSurface, mEglContext);

        mTexture = GLTools.newTexture(TEXTURE_LEVEL);
        mSurfaceTexture = new SurfaceTexture(mTexture, true);
        mSurface = new Surface(mSurfaceTexture);
    }

    /** Common "after" functionality. */
    @After
    public final void tearDown() throws Exception {

        assert mSurface != null;
        mSurface.release();

        assert mSurfaceTexture != null;
        mSurfaceTexture.release();

        try {
            GLTools.closeTexture(mTexture, TEXTURE_LEVEL);
        } catch (Throwable throwable) {
            logw(Log.getStackTraceString(throwable));
        }

        assert mEglDisplay != null;
        assert mEglSurface != null;
        GLTools.closeSurface(mEglDisplay, mEglSurface);

        assert mEglContext != null;
        GLTools.closeContext(mEglDisplay, mEglContext);
        GLTools.closeDisplay(mEglDisplay);
    }

    /**
     * Test for {@link InputSurface} creating and destroying.
     *
     * @throws Exception by some fails
     */
    @Test
    public final void testMain() throws Exception {
        final ByteBuffer inputBuffer = ByteBuffer.allocate(FRAME_SIZE * FRAME_SIZE * 2);
        final ByteBuffer outputBuffer = ByteBuffer.allocateDirect(FRAME_SIZE * FRAME_SIZE * 4);

        assert mSurface != null;
        final InputSurface inputSurface =
                InputSurface.create(mSurface, inputBuffer, FRAME_SIZE, FRAME_SIZE)
                        //.autoSwap() //Special for testing
                        .build();

        // 1-st pass
        Bitmap bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.RGB_565);
        bitmap.setPixel(0, 0, Color.RED); bitmap.setPixel(1, 0, Color.GREEN);
        bitmap.setPixel(0, 1, Color.BLUE); bitmap.setPixel(1, 1, Color.YELLOW);
        bitmap.copyPixelsToBuffer(inputBuffer); bitmap.recycle();

        inputSurface.draw(PRESENTATION_TIME);
        inputBuffer.rewind();

        GLES20.glReadPixels(0, 0, FRAME_SIZE, FRAME_SIZE,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, outputBuffer);
        bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(outputBuffer);
        outputBuffer.rewind();
        //bitmap.eraseColor(Color.BLACK);

        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 0)}, new int[]{Color.BLUE});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 0)}, new int[]{Color.YELLOW});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 1)}, new int[]{Color.RED});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 1)}, new int[]{Color.GREEN});
        bitmap.recycle();

        // 2-nd pass
        bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.RGB_565);
        bitmap.setPixel(0, 0, Color.YELLOW); bitmap.setPixel(1, 0, Color.BLUE);
        bitmap.setPixel(0, 1, Color.GREEN); bitmap.setPixel(1, 1, Color.RED);
        bitmap.copyPixelsToBuffer(inputBuffer); bitmap.recycle();

        inputSurface.draw(PRESENTATION_TIME);
        inputBuffer.rewind();

        GLES20.glReadPixels(0, 0, FRAME_SIZE, FRAME_SIZE,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, outputBuffer);
        bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(outputBuffer);
        outputBuffer.rewind();
        //bitmap.eraseColor(Color.BLACK);

        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 0)}, new int[]{Color.GREEN});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 0)}, new int[]{Color.RED});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 1)}, new int[]{Color.YELLOW});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 1)}, new int[]{Color.BLUE});
        bitmap.recycle();

        inputSurface.close();
        outputBuffer.clear();
        inputBuffer.clear();
    }


}