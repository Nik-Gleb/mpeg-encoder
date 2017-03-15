/*
 * MainActivity.java
 * app
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2017 Gleb Nikitenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.nikitenkogleb.mpegencoder.demo;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.VideoView;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Locale;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicProtectedClassMembers;
import ru.nikitenkogleb.mpegencoder.MpegEncoder;
import ru.nikitenkogleb.mpegencoder.MpegEncoder.Motion;


/**
 * @author Nikitenko Gleb
 * @since 1.0, 09/02/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("unused")
public final class MainActivity extends AppCompatActivity {

    /** The log-cat tag. */
    private static final String TAG = "MainActivity";

    /** The template of input images file. */
    private static final String INPUT_IMAGE_FILE_FORMAT = "frame_%03d.webp";

    /** The test output file name */
    private static final String OUTPUT_FILE_NAME = "output.mp4";

    /** H.264 Advanced Video Coding. */
    private static final String MIME_TYPE = "video/avc";

    /** The test width of video. */
    private static final int WIDTH = 1280;
    /** The test height of video. */
    private static final int HEIGHT = 720;
    /** The test frame-rate of video (8fps). */
    private static final int FRAME_RATE = 25;

    /** The test iframe interval of video (1 second between I-frames). */
    private static final int IFRAME_INTERVAL = 1;

    /** The one seconds of video. */
    @SuppressWarnings("PointlessArithmeticExpression")
    private static final int NUM_FRAMES = 131;

    /** The input buffer. */
    private static final ByteBuffer INPUT_BUFFER =
            ByteBuffer.allocate(WIDTH * HEIGHT * 2);

    /** The content view. */
    @Nullable
    private VideoView mContentView = null;

    /** The parsing task. */
    @Nullable
    private ParseTask mParseTask = null;

    /** Is video playing. */
    private boolean mNowPlaying = false;

    /** {@inheritDoc} */
    @Override
    protected final void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContentView = new VideoView(this);
        mContentView.setVisibility(View.GONE);
        mContentView.setOnPreparedListener(new OnPreparedListener());

        final FrameLayout.LayoutParams layoutParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER);
        mContentView.setLayoutParams(layoutParams);

        setContentView(mContentView);

        startParseTask(new File(getFilesDir(), OUTPUT_FILE_NAME).getAbsolutePath());

        //Total methods in lib-release.aar: 157 (0,24% used)
        //Total fields in lib-release.aar:  48 (0,07% used)
        // 27762 bytes
    }

    /** {@inheritDoc} */
    @Override
    protected final void onDestroy() {

        stopParseTask();
        stopVideo();

        assert mContentView != null;
        mContentView.setOnPreparedListener(null);
        mContentView = null;

        super.onDestroy();
    }

    /** {@inheritDoc} */
    @Override
    protected final void onResume() {
        super.onResume();
        resumeVideo();
    }

    /** {@inheritDoc} */
    @Override
    protected final void onPause() {
        pauseVideo();
        super.onPause();
    }

    /** Starts the video playback. */
    private void startVideo(@NonNull String filePath) {
        if (mNowPlaying) {
            throw new IllegalStateException("Video already playing");
        } else {
            mNowPlaying = true;
            assert mContentView != null;
            mContentView.setVisibility(View.VISIBLE);
            mContentView.setVideoPath(filePath);
            mContentView.start();
        }
    }

    /** Stops the video playback. */
    private void stopVideo() {
        if (mNowPlaying) {
            mNowPlaying = false;
            assert mContentView != null;
            mContentView.stopPlayback();
        }
    }

    /** Pause the video playback. */
    private void pauseVideo() {
        if (mNowPlaying) {
            assert mContentView != null;
            mContentView.stopPlayback();
        }
    }

    /** Stops the video playback. */
    private void resumeVideo() {
        if (mNowPlaying) {
            assert mContentView != null;
            mContentView.start();
        }
    }

    /** Start parse task. */
    private void startParseTask(@NonNull String fileName) {
        stopParseTask();
        mParseTask = new ParseTask(this, fileName);
    }

    /** Cancel Parse Task. */
    private void stopParseTask() {
        if (mParseTask != null) {
            mParseTask.close();
            mParseTask = null;
        }
    }


    /** The mp4 encoding task. */
    private static final class ParseTask extends AsyncTask<Void, Void, Void> implements Closeable {

        /** The main activity weak reference. */
        @NonNull
        private final WeakReference<MainActivity> mMainActivityWeakReference;

        /** The assets manager. */
        @NonNull
        private final AssetManager mAssetManager;

        /** The output-file path. */
        @NonNull
        private final String mFilePath;

        /**
         * Constructs a new {@link ParseTask} with a {@link MainActivity} reference.
         *
         * @param activity the {@link MainActivity} instance
         * @param filePath the output file path
         */
        ParseTask(@NonNull MainActivity activity, @NonNull String filePath) {
            mMainActivityWeakReference = new WeakReference<>(activity);
            mFilePath = filePath; mAssetManager = activity.getAssets();
            execute();
        }

        /** {@inheritDoc} */
        @Override
        public final void close() {
            mMainActivityWeakReference.clear();
            cancel(false);
        }

        /** {@inheritDoc} */
        @Override
        protected final Void doInBackground(Void... params) {

            final String[] fileNames = new String[NUM_FRAMES];
            for (int i = 0; i < NUM_FRAMES; i++) {
                fileNames[i] = String.format(Locale.getDefault(), INPUT_IMAGE_FILE_FORMAT, i);
            }

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, options.inPreferredConfig);
            options.inTempStorage = new byte[16384];

            final MpegEncoder encoder =
                    MpegEncoder
                            .from(INPUT_BUFFER, WIDTH, HEIGHT)
                            .fps(FRAME_RATE).motion(Motion.LOW)
                            .to(mFilePath, WIDTH, HEIGHT);

            for (int i = 0; i < NUM_FRAMES; i++) {
                if (isCancelled()) {
                    break;
                }

                try (final InputStream is = mAssetManager.open(fileNames[i])) {
                    final Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
                    if (bitmap != null) {
                        INPUT_BUFFER.rewind();
                        bitmap.copyPixelsToBuffer(INPUT_BUFFER);
                    }
                } catch (IOException exception) {
                    Log.w(TAG, exception);
                }

                encoder.draw();

            }

            encoder.close();

            options.inBitmap.recycle();

            return null;
        }

        /** {@inheritDoc} */
        @Override
        protected final void onPostExecute(@NonNull Void result) {
            super.onPostExecute(result);

            final MainActivity mainActivity = mMainActivityWeakReference.get();
            if (mainActivity != null) {
                mainActivity.startVideo(mFilePath);
            }

            close();
        }
    }


    /** The {@link android.media.MediaPlayer.OnPreparedListener} implementation. */
    private static final class OnPreparedListener implements MediaPlayer.OnPreparedListener {

        /**
         * Called when the media file is ready for playback.
         *
         * @param mediaPlayer the MediaPlayer that is ready for playback
         */
        @Override
        public final void onPrepared(@NonNull MediaPlayer mediaPlayer) {
            mediaPlayer.setLooping(true);
        }
    }

}
