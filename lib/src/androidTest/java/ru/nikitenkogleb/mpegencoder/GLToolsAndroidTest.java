package ru.nikitenkogleb.mpegencoder;

import org.junit.Assert;
import org.junit.Test;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicProtectedClassMembers;

/**
 * The {@link GLTools} Instrumentation Test.
 *
 * @author Nikitenko Gleb
 * @since 1.0, 10/03/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("WeakerAccess, unused")
public final class GLToolsAndroidTest extends BaseInstrumentationTest {

    /** Default texture level. */
    private static final int TEXTURE_LEVEL = GLES20.GL_TEXTURE0;

    /** The test frame size. */
    private static final int FRAME_SIZE = 2;
    /** The test presentation time. */
    private static final int PRESENTATION_TIME = 1000000;

    /**
     * Test for {@link GLTools#newDisplay()} and {@link GLTools#closeDisplay(EGLDisplay)}.
     *
     * @throws Exception by some fails
     */
    @Test
    public final void testDisplay() throws Exception {
        GLTools.closeDisplay(GLTools.newDisplay());
    }

    /**
     * Test for {@link GLTools#newConfig(EGLDisplay, boolean)}
     * @throws Exception by some fails
     */
    @Test
    public final void testConfigWindow() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        GLTools.newConfig(eglDisplay, false);
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#newConfig(EGLDisplay, boolean)}
     * @throws Exception by some fails
     */
    @Test
    public final void testConfigPBuffer() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        GLTools.newConfig(eglDisplay, true);
        GLTools.closeDisplay(eglDisplay);
    }


    /**
     * Test for {@link GLTools#newContext(EGLDisplay, EGLConfig)} and
     * {@link GLTools#closeContext(EGLDisplay, EGLContext)}.
     * @throws Exception by some fails
     */
    @Test
    public final void testContextWindow() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, false);
        GLTools.closeContext(eglDisplay, GLTools.newContext(eglDisplay, eglConfig));
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#newContext(EGLDisplay, EGLConfig)} and
     * {@link GLTools#closeContext(EGLDisplay, EGLContext)}.
     * @throws Exception by some fails
     */
    @Test
    public final void testContextPBuffer() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        GLTools.closeContext(eglDisplay, GLTools.newContext(eglDisplay, eglConfig));
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#newSurface(EGLDisplay, EGLConfig, int, int)} and
     * {@link GLTools#closeSurface(EGLDisplay, EGLSurface)}.
     * @throws Exception by some fails
     */
    @Test
    public final void testSurface() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        final EGLContext eglContext = GLTools.newContext(eglDisplay, eglConfig);
        GLTools.closeSurface(eglDisplay,
                GLTools.newSurface(eglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE));
        GLTools.closeContext(eglDisplay, eglContext);
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#makeCurrent(EGLDisplay, EGLSurface, EGLContext)}.
     * @throws Exception by some fails
     */
    @Test
    public final void testMakeCurrent() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        final EGLContext eglContext = GLTools.newContext(eglDisplay, eglConfig);
        final EGLSurface eglSurface =
                GLTools.newSurface(eglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE);
        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);
        GLTools.closeSurface(eglDisplay, eglSurface);
        GLTools.closeContext(eglDisplay, eglContext);
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#swapBuffers(EGLDisplay, EGLSurface)}.
     * @throws Exception by some fails
     */
    @Test
    public final void testSwapBuffers() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        final EGLContext eglContext = GLTools.newContext(eglDisplay, eglConfig);
        final EGLSurface eglSurface =
                GLTools.newSurface(eglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE);
        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);
        try {
            GLTools.swapBuffers(eglDisplay, eglSurface);
        } catch (RuntimeException exception) {

            final int txt = GLTools.newTexture(TEXTURE_LEVEL);
            final SurfaceTexture surfaceTexture = new SurfaceTexture(txt, true);
            final Surface surface = new Surface(surfaceTexture);
            final EGLSurface window = GLTools.newSurface(eglDisplay, eglConfig, surface);

            GLTools.makeCurrent(eglDisplay, window, eglContext);
            GLTools.swapBuffers(eglDisplay, window);

            GLTools.closeSurface(eglDisplay, window);
            surface.release();
            surfaceTexture.release();
            GLTools.closeTexture(txt, TEXTURE_LEVEL);
        }

        GLTools.closeSurface(eglDisplay, eglSurface);
        GLTools.closeContext(eglDisplay, eglContext);
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#setPresentationTime(EGLDisplay, EGLSurface, long)} .
     * @throws Exception by some fails
     */
    @Test
    public final void testSetPresentationTime() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        final EGLContext eglContext = GLTools.newContext(eglDisplay, eglConfig);
        final EGLSurface eglSurface =
                GLTools.newSurface(eglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE);
        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);

        final int txt = GLTools.newTexture(TEXTURE_LEVEL);
        final SurfaceTexture surfaceTexture = new SurfaceTexture(txt, true);
        final Surface surface = new Surface(surfaceTexture);
        final EGLSurface window = GLTools.newSurface(eglDisplay, eglConfig, surface);

        GLTools.setPresentationTime(eglDisplay, window, PRESENTATION_TIME);

        GLTools.closeSurface(eglDisplay, window);
        surface.release();
        surfaceTexture.release();
        GLTools.closeTexture(txt, TEXTURE_LEVEL);

        GLTools.closeSurface(eglDisplay, eglSurface);
        GLTools.closeContext(eglDisplay, eglContext);
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#newTexture(int)} and {@link GLTools#closeTexture(int, int)}.
     * @throws Exception by some fails
     */
    @Test
    public final void testTexture() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        final EGLContext eglContext = GLTools.newContext(eglDisplay, eglConfig);
        final EGLSurface eglSurface =
                GLTools.newSurface(eglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE);

        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);

        GLTools.closeTexture(GLTools.newTexture(TEXTURE_LEVEL), TEXTURE_LEVEL);

        GLTools.closeSurface(eglDisplay, eglSurface);
        GLTools.closeContext(eglDisplay, eglContext);
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#newShader(int[])} and {@link GLTools#closeShader(int[])}.
     * @throws Exception by some fails
     */
    @Test
    public final void testShader() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        final EGLContext eglContext = GLTools.newContext(eglDisplay, eglConfig);
        final EGLSurface eglSurface =
                GLTools.newSurface(eglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE);

        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);

        final int[] attrs = new int[5];
        GLTools.newShader(attrs);
        GLTools.closeShader(attrs);

        GLTools.closeSurface(eglDisplay, eglSurface);
        GLTools.closeContext(eglDisplay, eglContext);
        GLTools.closeDisplay(eglDisplay);
    }

    /**
     * Test for {@link GLTools#drawFrame(Buffer, int, int, int)}.
     * @throws Exception by some fails
     */
    @Test
    public final void testDrawFrame() throws Exception {
        final EGLDisplay eglDisplay = GLTools.newDisplay();
        final EGLConfig eglConfig = GLTools.newConfig(eglDisplay, true);
        final EGLContext eglContext = GLTools.newContext(eglDisplay, eglConfig);
        final EGLSurface eglSurface =
                GLTools.newSurface(eglDisplay, eglConfig, FRAME_SIZE, FRAME_SIZE);

        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);

        final int[] attrs = new int[5];
        GLTools.newShader(attrs);
        final int texture = GLTools.newTexture(TEXTURE_LEVEL);

        // 1-st pass
        Bitmap bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.RGB_565);
        bitmap.setPixel(0, 0, Color.RED); bitmap.setPixel(1, 0, Color.GREEN);
        bitmap.setPixel(0, 1, Color.BLUE); bitmap.setPixel(1, 1, Color.YELLOW);
        ByteBuffer buffer = ByteBuffer.allocate(FRAME_SIZE * FRAME_SIZE * 2);
        bitmap.copyPixelsToBuffer(buffer); bitmap.recycle();

        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);
        GLTools.drawFrame(buffer, FRAME_SIZE, FRAME_SIZE, 0); buffer.clear();
        //GLTools.swapBuffers(eglDisplay, eglSurface);

        buffer = ByteBuffer.allocateDirect(FRAME_SIZE * FRAME_SIZE * 4);
        GLES20.glReadPixels(0, 0, FRAME_SIZE, FRAME_SIZE,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

        bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer); buffer.clear(); //bitmap.eraseColor(Color.BLACK);

        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 0)}, new int[]{Color.BLUE});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 0)}, new int[]{Color.YELLOW});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 1)}, new int[]{Color.RED});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 1)}, new int[]{Color.GREEN});
        bitmap.recycle();

        // 2-nd pass
        bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.RGB_565);
        bitmap.setPixel(0, 0, Color.YELLOW); bitmap.setPixel(1, 0, Color.BLUE);
        bitmap.setPixel(0, 1, Color.GREEN); bitmap.setPixel(1, 1, Color.RED);
        buffer = ByteBuffer.allocate(FRAME_SIZE * FRAME_SIZE * 2);
        bitmap.copyPixelsToBuffer(buffer); bitmap.recycle();

        GLTools.makeCurrent(eglDisplay, eglSurface, eglContext);
        GLTools.drawFrame(buffer, FRAME_SIZE, FRAME_SIZE, 0); buffer.clear();
        //GLTools.swapBuffers(eglDisplay, eglSurface);

        buffer = ByteBuffer.allocateDirect(FRAME_SIZE * FRAME_SIZE * 4);
        GLES20.glReadPixels(0, 0, FRAME_SIZE, FRAME_SIZE,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);

        bitmap = Bitmap.createBitmap(FRAME_SIZE, FRAME_SIZE, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer); buffer.clear(); //bitmap.eraseColor(Color.BLACK);

        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 0)}, new int[]{Color.GREEN});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 0)}, new int[]{Color.RED});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(0, 1)}, new int[]{Color.YELLOW});
        Assert.assertArrayEquals(new int[]{bitmap.getPixel(1, 1)}, new int[]{Color.BLUE});
        bitmap.recycle();

        GLTools.closeTexture(texture, TEXTURE_LEVEL);
        GLTools.closeShader(attrs);

        GLTools.closeSurface(eglDisplay, eglSurface);
        GLTools.closeContext(eglDisplay, eglContext);
        GLTools.closeDisplay(eglDisplay);
    }

}