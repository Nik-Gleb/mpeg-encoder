package ru.nikitenkogleb.mpegencoder;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicProtectedClassMembers;

/**
 * Common {@link android.opengl.EGL14} utils.
 *
 * @author Nikitenko Gleb
 * @since 1.0, 10/03/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("WeakerAccess, unused")
final class GLTools {

    /** The log-cat tag. */
    private static final String TAG = "GLTools";

    /** Verbose mode logging. */
    private static final boolean VERBOSE =
            BuildConfig.DEBUG || Log.isLoggable(TAG, Log.VERBOSE);
    /** Warning mode logging. */
    private static final boolean WARNINGS =
            BuildConfig.DEBUG || Log.isLoggable(TAG, Log.WARN);

    /** Android-specific extension. */
    private static final int RECORDABLE_ANDROID = 0x3142;

    /** Float size in bytes. */
    private static final int FLOAT_SIZE_BYTES = 4;

    /** Shader str-constants. */
    private static final String
            V_POSITION = "aPosition", V_COORDINATE = "aCoordinate",
            V_MVP_MATRIX = "uMVPMatrix", V_ST_MATRIX = "uSTMatrix",
            F_COORDINATE = "vCoordinate", F_TEXTURE = "sTexture";


    /** Triangle vertices stride bytes. */
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    /** Triangle vertices data pos. */
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    /** Triangle vertices data uv offset. */
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    /** Triangle vertices. */
    private static final FloatBuffer TRIANGLE_VERTICES = createVertices();

    /** The vertex shader. */
    private static final String VSHADER =
            "uniform mat4 " + V_MVP_MATRIX + ";\n" +
                    "uniform mat4 " + V_ST_MATRIX + ";\n" +
                    "attribute vec4 " + V_POSITION + ";\n" +
                    "attribute vec4 " + V_COORDINATE + ";\n" +
                    "varying vec2 " + F_COORDINATE + ";\n" +
                    "void main() {\n" +
                    "  gl_Position = " + V_MVP_MATRIX + " * " + V_POSITION + ";\n" +
                    "  " + F_COORDINATE + " = (" + V_ST_MATRIX + " * " + V_COORDINATE + ").xy;\n" +
                    "}\n";

    /** The fragment shader. */
    private static final String FSHADER =
            //"#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
                    "varying vec2 " + F_COORDINATE + ";\n" +
                    //"uniform samplerExternalOES " + F_TEXTURE + ";\n" +
                    "uniform sampler2D " + F_TEXTURE + ";\n" +
                    "void main() {\n" +
                    "  vec2 flipped = vec2(" + F_COORDINATE + ".x, 1.0 - " + F_COORDINATE + ".y);" +
                    "  gl_FragColor = texture2D(" + F_TEXTURE + ", flipped);\n" +
                    "}\n";

    /**
     * The caller should be prevented from constructing objects of this class.
     * Also, this prevents even the native class from calling this constructor.
     **/
    private GLTools() {throw new AssertionError();}

    /** @return from a new EGL display connection */
    @NonNull
    public static EGLDisplay newDisplay() {
        final EGLDisplay result = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (result == EGL14.EGL_NO_DISPLAY) {
            logError();
            throw new RuntimeException("Unable to get EGL14 display");
        } else {
            final int[] v = new int[2];
            if (!EGL14.eglInitialize(result, v, 0, v, 1)) {
                try {
                    logError();
                    throw new RuntimeException("Unable to initialize EGL14 display");
                } finally {
                    closeDisplay(result);
                }
            } else {
                logDebug(getDisplayString(result) + " created (EGL" + v[0] + "" + v[1] + ")");
                return result;
            }
        }
    }

    /** Log GL-Debug. */
    private static void logDebug(@NonNull String msg) {
        if (VERBOSE) {
            Log.v(TAG, msg);
        }
    }

    /** Log GL-Error. */
    private static void logError() {
        final int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            if (WARNINGS) {
                Log.w(TAG, android.opengl.GLUtils.getEGLErrorString(error));
            }
        }
    }

    /**
     * @param display EGL display connection
     * @return the EGL display handle
     */
    @NonNull
    private static String getDisplayString(@NonNull EGLDisplay display) {
        return "Display(" + getDisplayHandle(display) + ")";
    }

    /**
     * @param display EGL display connection
     * @return the EGL display handle
     */
    private static long getDisplayHandle(@NonNull EGLDisplay display) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getDisplayHandleLollipop(display);
        } else {
            return getDisplayHandleBase(display);
        }
    }

    /**
     * @param display EGL display connection
     * @return the EGL display handle
     */
    @SuppressWarnings("deprecation")
    private static long getDisplayHandleBase(@NonNull EGLDisplay display) {
        return display.getHandle();
    }

    /**
     * @param display EGL display connection
     * @return the EGL display handle
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static long getDisplayHandleLollipop(@NonNull EGLDisplay display) {
        return display.getNativeHandle();
    }

    /**
     * Close an EGL display connection.
     * @param display an EGL display connection instance
     */
    public static void closeDisplay(@NonNull EGLDisplay display) {
        if (!EGL14.eglTerminate(display)) {
            logError();
            throw new RuntimeException("Unable to terminate EGL14 " + getDisplayString(display));
        } else {
            logDebug(getDisplayString(display) + " destroyed");
        }
    }

    /**
     * @param display an EGL display connection instance
     * @param usePBuffer do not accept rendering through the native window system
     * @return frame buffer configuration that defines the frame buffer resource available to the
     * rendering context.
     **/
    @NonNull
    public static EGLConfig newConfig(@NonNull EGLDisplay display, boolean usePBuffer) {
        final int surfaceTypeKey = usePBuffer ? EGL14.EGL_SURFACE_TYPE : RECORDABLE_ANDROID;
        final int surfaceTypeValue = usePBuffer ? EGL14.EGL_PBUFFER_BIT : 1;
        final EGLConfig[] configs = new EGLConfig[1];
        final int[] numConfigs = new int[1];
        if (!EGL14.eglChooseConfig(display,
                new int[] {
                        EGL14.EGL_RED_SIZE, 5,
                        EGL14.EGL_GREEN_SIZE, 6,
                        EGL14.EGL_BLUE_SIZE, 5,
                        /*EGL14.EGL_RED_SIZE, 8,
                        EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8,*/

                        EGL14.EGL_RENDERABLE_TYPE,
                        EGL14.EGL_OPENGL_ES2_BIT,
                        surfaceTypeKey, surfaceTypeValue,
                        EGL14.EGL_NONE }, 0,
                configs, 0, configs.length, numConfigs, 0) && numConfigs[0] == 1) {
                logError();
                throw new RuntimeException("Unable to from EGL14 config");
        } else {
            final EGLConfig result = configs[0];
            logDebug(getConfigString(result) + " created");
            return result;
        }
    }

    /**
     * @param config EGL config
     * @return the EGL config handle
     */
    @NonNull
    private static String getConfigString(@NonNull EGLConfig config) {
        return "Config(" + getConfigHandle(config) + ")";
    }

    /**
     * @param config EGL config
     * @return the EGL config handle
     */
    private static long getConfigHandle(@NonNull EGLConfig config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getConfigHandleLollipop(config);
        } else {
            return getConfigHandleBase(config);
        }
    }

    /**
     * @param config EGL config
     * @return the EGL config handle
     */
    @SuppressWarnings("deprecation")
    private static long getConfigHandleBase(@NonNull EGLConfig config) {
        return config.getHandle();
    }

    /**
     * @param config EGL config
     * @return the EGL config handle
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static long getConfigHandleLollipop(@NonNull EGLConfig config) {
        return config.getNativeHandle();
    }

    /**
     * @param display an EGL display connection instance
     * @return a new EGL rendering context
     **/
    @NonNull
    public static EGLContext newContext(@NonNull EGLDisplay display, @NonNull EGLConfig config) {
        final EGLContext result = EGL14.eglCreateContext (display, config, EGL14.EGL_NO_CONTEXT,
                new int[] {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE }, 0);
        if (result == EGL14.EGL_NO_CONTEXT) {
            logError();
            throw new RuntimeException("Unable to from EGL14 context");
        } else {
            logDebug(getContextString(result) + " created");
            return result;
        }
    }

    /**
     * @param context EGL context
     * @return the EGL context handle
     */
    @NonNull
    private static String getContextString(@NonNull EGLContext context) {
        return "Context(" + getContextHandle(context) + ")";
    }

    /**
     * @param context EGL context
     * @return the EGL context handle
     */
    private static long getContextHandle(@NonNull EGLContext context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getContextHandleLollipop(context);
        } else {
            return getContextHandleBase(context);
        }
    }

    /**
     * @param context EGL context
     * @return the EGL context handle
     */
    @SuppressWarnings("deprecation")
    private static long getContextHandleBase(@NonNull EGLContext context) {
        return context.getHandle();
    }

    /**
     * @param context EGL context
     * @return the EGL context handle
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static long getContextHandleLollipop(@NonNull EGLContext context) {
        return context.getNativeHandle();
    }

    /**
     * Close an EGL rendering context.
     * @param display an EGL display connection instance
     * @param context an EGL rendering context
     */
    public static void closeContext(@NonNull EGLDisplay display, @NonNull EGLContext context) {
        if (!EGL14.eglDestroyContext(display, context)) {
            logError();
            throw new RuntimeException("Unable to terminate EGL14 " + getContextString(context));
        } else {
            if (!EGL14.eglReleaseThread()) logError();
            logDebug(getContextString(context) + " destroyed");
        }
    }

    /**
     * @param display an EGL display connection instance
     * @param width horizontal size of virtual surface
     * @param height vertical size of virtual surface
     * @param config a frame buffer configuration
     * @return a new EGL window surface
     **/
    @NonNull
    public static EGLSurface newSurface(@NonNull EGLDisplay display, @NonNull EGLConfig config,
            int width, int height) {
        final EGLSurface result = EGL14.eglCreatePbufferSurface(display, config,
                new int[] {EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE}, 0);
        if (result == EGL14.EGL_NO_SURFACE) {
            logError();
            throw new RuntimeException("Unable to from EGL14 context");
        } else {
            logDebug(getSurfaceString(result) + " created");
            return result;
        }
    }

    /**
     * @param surface EGL surface
     * @return the EGL surface handle
     */
    @NonNull
    private static String getSurfaceString(@NonNull EGLSurface surface) {
        return "Surface(" + getSurfaceHandle(surface) + ")";
    }

    /**
     * @param surface EGL surface
     * @return the EGL surface handle
     */
    private static long getSurfaceHandle(@NonNull EGLSurface surface) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return getSurfaceHandleLollipop(surface);
        } else {
            return getSurfaceHandleBase(surface);
        }
    }

    /**
     * @param surface EGL surface
     * @return the EGL surface handle
     */
    @SuppressWarnings("deprecation")
    private static long getSurfaceHandleBase(@NonNull EGLSurface surface) {
        return surface.getHandle();
    }

    /**
     * @param surface EGL surface
     * @return the EGL surface handle
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private static long getSurfaceHandleLollipop(@NonNull EGLSurface surface) {
        return surface.getNativeHandle();
    }

    /**
     * @param display an EGL display connection instance
     * @param surface a native window.
     * @param config a frame buffer configuration
     * @return a new EGL window surface
     **/
    @NonNull
    public static EGLSurface newSurface(@NonNull EGLDisplay display, @NonNull EGLConfig config,
            @NonNull Surface surface) {
        final EGLSurface result = EGL14.eglCreateWindowSurface(display, config, surface,
                new int[] {EGL14.EGL_NONE}, 0);
        if (result == EGL14.EGL_NO_SURFACE) {
            logError();
            throw new RuntimeException("Unable to from EGL14 context");
        } else {
            logDebug(getSurfaceString(result) + " created");
            return result;
        }
    }

    /**
     * Close an EGL rendering context.
     * @param display an EGL display connection instance
     * @param surface an EGL rendering surface
     */
    public static void closeSurface(@NonNull EGLDisplay display, @NonNull EGLSurface surface) {
        if (!EGL14.eglDestroySurface(display, surface)) {
            logError();
            throw new RuntimeException("Unable to terminate EGL14 " + getSurfaceString(surface));
        } else {
            logDebug(getSurfaceString(surface) + " destroyed");
        }
    }

    /**
     * Make an EGL rendering context as current.
     * @param display an EGL display connection instance
     * @param surface an EGL rendering surface
     * @param context an EGL rendering context
     */
    public static void makeCurrent(@NonNull EGLDisplay display, @NonNull EGLSurface surface,
            @NonNull EGLContext context) {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            logError();
            throw new RuntimeException("Unable to make " + getContextString(context) + " current");
        } else {
            logDebug(getContextString(context) + " set as current");
        }
    }

    /**
     * Calls eglSwapBuffers. Use this to "publish" the current frame.
     * @param display an EGL display connection instance
     * @param surface an EGL rendering surface
     */
    public static void swapBuffers(@NonNull EGLDisplay display, @NonNull EGLSurface surface) {
        //noinspection StatementWithEmptyBody
        if (!EGL14.eglSwapBuffers(display, surface)) {
            logError();
            throw new RuntimeException("Unable to swap buffers. " +
                    getDisplayString(display) + "; " + getSurfaceString(surface));
        } else {/*
            logDebug("Swap buffers. " +
                    getDisplayString(display) + "; " + getSurfaceString(surface));*/
        }
    }

    /**
     * Sends the presentation time stamp to EGL.
     * @param display an EGL display connection instance
     * @param surface an EGL rendering surface
     * @param nSecs time is expressed in nanoseconds.
     */
    public static void setPresentationTime(@NonNull EGLDisplay display,
            @NonNull EGLSurface surface, long nSecs) {
        //noinspection StatementWithEmptyBody
        if (!EGLExt.eglPresentationTimeANDROID(display, surface, nSecs)) {
            logError();
            throw new RuntimeException("Unable to set presentation time (" + nSecs +"ns)." +
                    getDisplayString(display) + "; " + getSurfaceString(surface));
        } else {/*
            logDebug("Set presentation time (" + nSecs +"ns)." +
                    getDisplayString(display) + "; " + getSurfaceString(surface));*/
        }
    }

    /**
     * Create a GLES texture
     * @return new gl-texture
     */
    public static int newTexture(int level) {
        /*GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);*/

        // generate one texture pointer and bind it as an external texture.
        final int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        checkError();

        GLES20.glActiveTexture(level);
        checkError();

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
        checkError();

        // No mip-mapping with camera source.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        checkError();

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        checkError();

        // Clamp to edge is only option.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        checkError();
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        checkError();

        final int result = textures[0];
        logDebug("Texture " + result + " created");

        return result;
    }

    /** Log GL-Error. */
    private static void checkError() {
        final int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            throw new RuntimeException(GLUtils.getEGLErrorString(error));
        }
    }

    /**
     * Release a texture.
     * @param texture existing texture
     */
    public static void closeTexture(int texture, int level) {
        GLES20.glActiveTexture(level);
        checkError();

        try {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            checkError();
        } finally {
            GLES20.glDeleteTextures(1, new int[] {texture}, 0);
            checkError();
        }

        logDebug("Texture " + texture + " destroyed");
    }



    /** Create a shader */
    public static void newShader(@NonNull int[] attrs) {
        if (attrs.length != 5) {
            throw new IllegalArgumentException("Must be 5 int-array");
        }

        /*
         * 0 - Program
         * 1 - Vertex Shader
         * 2 - Fragment Shader
         * 3 - Position Handle
         * 4 - Texture Handle
         **/

        attrs[0] = GLES20.glCreateProgram();
        checkError();

        GLES20.glAttachShader(attrs[0], (attrs[1] = loadShader(GLES20.GL_VERTEX_SHADER, VSHADER)));
        GLES20.glAttachShader(attrs[0], (attrs[2] = loadShader(GLES20.GL_FRAGMENT_SHADER, FSHADER)));
        GLES20.glLinkProgram(attrs[0]); GLES20.glUseProgram(attrs[0]);
        checkError();

        attrs[3] = GLES20.glGetAttribLocation(attrs[0], V_POSITION);
        attrs[4] = GLES20.glGetAttribLocation(attrs[0], V_COORDINATE);
        final int uMVPMatrixHandle = GLES20.glGetUniformLocation(attrs[0], V_MVP_MATRIX);
        final int uSTMatrixHandle = GLES20.glGetUniformLocation(attrs[0], V_ST_MATRIX);
        checkError();

        TRIANGLE_VERTICES.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(attrs[3], 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, TRIANGLE_VERTICES);
        checkError();

        TRIANGLE_VERTICES.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(attrs[4], 4, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, TRIANGLE_VERTICES);
        checkError();

        GLES20.glEnableVertexAttribArray(attrs[3]);
        GLES20.glEnableVertexAttribArray(attrs[4]);
        checkError();

        float[] MVPMatrix = new float[16]; Matrix.setIdentityM(MVPMatrix, 0);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, MVPMatrix, 0);
        float[] STMatrix = new float[16]; Matrix.setIdentityM(STMatrix, 0);
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, STMatrix, 0);
        checkError();

        logDebug("Shader created");
    }

    /** Close the shader. */
    public static void closeShader(@NonNull int[] attrs) {
        if (attrs.length != 5) {
            throw new IllegalArgumentException("Must be 5 int-array");
        }

        /*
         * 0 - Program
         * 1 - Vertex Shader
         * 2 - Fragment Shader
         * 3 - Position Handle
         * 4 - Texture Handle
         **/
        GLES20.glDisableVertexAttribArray(attrs[3]);
        GLES20.glDisableVertexAttribArray(attrs[4]);
        checkError();

        GLES20.glUseProgram(0);
        checkError();

        GLES20.glDetachShader(attrs[0], attrs[1]);
        GLES20.glDeleteShader(attrs[1]);
        checkError();

        GLES20.glDetachShader(attrs[0], attrs[2]);
        GLES20.glDeleteShader(attrs[2]);
        GLES20.glDeleteProgram(attrs[0]);
        checkError();

        //GLES20.glFinish();
        logDebug("Shader destroyed");
    }

    /**
     * Called to draw the current frame.
     * <p>
     * This method is responsible for drawing the current frame.
     */
    public static void drawFrame(@NonNull Buffer pixels, int width, int height, int border) {
        pixels.rewind();
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB,
                width, height, border, GLES20.GL_RGB,
                GLES20.GL_UNSIGNED_SHORT_5_6_5, pixels);
        checkError();

        //GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkError();

        //logDebug("Frame was drew");
    }

    /**
     * Load/compile shader.
     * @param type the type of shader
     * @param source the source of shader
     * @return the shader id
     */
    private static int loadShader(int type, @NonNull String source) {
        final int result = GLES20.glCreateShader(type);
        GLES20.glShaderSource(result, source);
        GLES20.glCompileShader(result); return result;
    }

    /**
     * Creates triangle vertices buffer.
     *
     * @return the triangle vertices buffer
     */
    private static FloatBuffer createVertices() {
        final float[] vertices = {
                // X,   Y,    Z,    U,    V
                -1.0f, -1.0f, 0.0f, 0.0f, 0.f,
                 1.0f, -1.0f, 0.0f, 1.0f, 0.f,
                -1.0f,  1.0f, 0.0f, 0.0f, 1.f,
                 1.0f,  1.0f, 0.0f, 1.0f, 1.f,
        };
        final FloatBuffer result =
                ByteBuffer.allocateDirect(vertices.length * FLOAT_SIZE_BYTES)
                        .order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(vertices).position(0);
        return result;
    }
}
