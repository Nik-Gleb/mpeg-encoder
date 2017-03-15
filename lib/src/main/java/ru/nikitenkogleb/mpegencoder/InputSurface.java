package ru.nikitenkogleb.mpegencoder;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.Surface;

import java.io.Closeable;
import java.nio.ByteBuffer;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicProtectedClassMembers;

/**
 * @author Nikitenko Gleb
 * @since 1.0, 13/03/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("WeakerAccess, unused")
final class InputSurface implements Closeable {

    /** The log-cat tag. */
    private static final String TAG = "MpegEncoder";

    /** Verbose mode logging. */
    private static final boolean VERBOSE =
            BuildConfig.DEBUG || Log.isLoggable(TAG, Log.VERBOSE);

    /** Warning mode logging. */
    private static final boolean WARNINGS =
            BuildConfig.DEBUG || Log.isLoggable(TAG, Log.WARN);

    /** Default texture level. */
    private static final int TEXTURE_LEVEL = GLES20.GL_TEXTURE0;

    /** The EGL Surface. */
    @NonNull
    private final int[] mShader = new int[5];

    /** The horizontal size of input frames. */
    private final int mWidth;

    /** The vertical size of input frames. */
    private final int mHeight;

    /** The GL-Texture. */
    private final int mTexture;

    /** The frame buffer. */
    @NonNull
    private final ByteBuffer mByteBuffer;

    /** The EGL Display. */
    @NonNull
    private final EGLDisplay mEglDisplay;

    /** The EGL Context. */
    @NonNull
    private final EGLContext mEglContext;

    /** The EGL Surface. */
    @NonNull
    private final EGLSurface mEglSurface;

    /** AutoSwap mode flag. */
    private final boolean mAutoSwap;


    /** The object was released. */
    private boolean mReleased;

    /** Constructs a new {@link InputSurface} */
    private InputSurface(@NonNull Builder builder) {
        checkState();

        mAutoSwap = builder.autoSwap;
        mWidth = builder.width;
        mHeight = builder.height;
        mByteBuffer = builder.byteBuffer;

        mEglDisplay = GLTools.newDisplay();

        final EGLConfig eglConfig = GLTools.newConfig(mEglDisplay, false);
        mEglContext = GLTools.newContext(mEglDisplay, eglConfig);
        mEglSurface = GLTools.newSurface(mEglDisplay, eglConfig, builder.surface);

        if (mAutoSwap)
            GLTools.makeCurrent(mEglDisplay, mEglSurface, mEglContext);

        GLTools.newShader(mShader);
        mTexture = GLTools.newTexture(TEXTURE_LEVEL);

        logv("Input surface created");
    }

    /** {@inheritDoc} */
    @Override
    public final void close() {
        checkState();

        GLTools.closeTexture(mTexture, TEXTURE_LEVEL);
        GLTools.closeShader(mShader);

        GLTools.closeSurface(mEglDisplay, mEglSurface);
        GLTools.closeContext(mEglDisplay, mEglContext);
        GLTools.closeDisplay(mEglDisplay);

        mReleased = true;
        logv("Input surface destroyed");
    }

    /**
     * Called to draw the current frame.
     * <p>
     * This method is responsible for drawing the current frame.
     */
    public final void draw(long nSec) {
        //if (mAutoSwap)
        //    GLTools.makeCurrent(mEglDisplay, mEglSurface, mEglContext);
        GLTools.drawFrame(mByteBuffer, mWidth, mHeight, 0);
        GLTools.setPresentationTime(mEglDisplay, mEglSurface, nSec);

        // Submit it to the encoder.  The eglSwapBuffers call will block if the input
        // is full, which would be bad if it stayed full until we dequeued an output
        // buffer (which we can't do, since we're stuck here).  So long as we fully drain
        // the encoder before supplying additional input, the system guarantees that we
        // can supply another frame without blocking.
        if (mAutoSwap)
            GLTools.swapBuffers(mEglDisplay, mEglSurface);
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
     * Create a {@link Builder} suitable for building a {@link InputSurface}.
     *
     * @param surface the source surface
     * @param frameBuffer the frame buffer
     * @param width horizontal size of input frames
     * @param height vertical size of input frames
     *
     * @return a {@link Builder}
     */
    @NonNull
    public static Builder create(@NonNull Surface surface, @NonNull ByteBuffer frameBuffer,
            int width, int height) {
        return new Builder(surface, frameBuffer, width, height);
    }

    /**
     * Used to add parameters to a {@link InputSurface}.
     *
     * The {@link Builder} is first created by calling {@link #create(Surface, ByteBuffer, int, int)}.
     *
     * The where methods can then be used to add parameters to the builder.
     * See the specific methods to find for which {@link Builder} type each is allowed.
     * Call {@link #build()} to from the {@link InputSurface}
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

        /** The input surface. */
        @NonNull
        private final Surface surface;

        /** The frame buffer. */
        @NonNull
        private final ByteBuffer byteBuffer;

        /** The horizontal size of input frames. */
        private final int width;
        /** The vertical size of input frames. */
        private final int height;

        /** AutoSwap mode flag. */
        private boolean autoSwap = false;

        /**
         * Constructs a new {@link Builder}.
         *
         * @param surface the source surface
         * @param width horizontal size of input frames
         * @param height vertical size of input frames
         */
        public Builder(@NonNull Surface surface, @NonNull ByteBuffer frameBuffer,
                int width, int height) {
            this.surface = surface;
            this.width = width;
            this.height = height;
            this.byteBuffer = frameBuffer;
        }

        /** Enable auto-swap mode */
        @NonNull
        public final Builder autoSwap() {
            autoSwap = true;
            return this;
        }
        /** Create a {@link InputSurface} from this {@link Builder}. */
        @NonNull
        public final InputSurface build()  {
            return new InputSurface(this);
        }
    }
}
