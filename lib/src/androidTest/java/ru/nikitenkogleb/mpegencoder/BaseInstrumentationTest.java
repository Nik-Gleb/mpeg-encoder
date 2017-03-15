package ru.nikitenkogleb.mpegencoder;

import org.junit.runner.RunWith;

import android.support.annotation.NonNull;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import proguard.annotation.Keep;
import proguard.annotation.KeepPublicProtectedClassMembers;

/**
 * Base Android Instrumentation Test.
 *
 * @author Nikitenko Gleb
 * @since 1.0, 10/03/2017
 */
@Keep
@KeepPublicProtectedClassMembers
@SuppressWarnings("WeakerAccess, unused")
@RunWith(AndroidJUnit4.class)
public abstract class BaseInstrumentationTest {

    /** The log-cat tag. */
    private final String TAG = getClass().getSimpleName();

    /**
     * Log in verbose mode.
     * @param msg the log-message
     */
    protected final void logv(@NonNull String msg) {
        log(Log.VERBOSE, msg);
    }

    /**
     * Log in debug mode.
     * @param msg the log-message
     */
    protected final void logd(@NonNull String msg) {
        log(Log.DEBUG, msg);
    }

    /**
     * Log in info mode.
     * @param msg the log-message
     */
    protected final void logi(@NonNull String msg) {
        log(Log.INFO, msg);
    }

    /**
     * Log in warn mode.
     * @param msg the log-message
     */
    protected final void logw(@NonNull String msg) {
        log(Log.WARN, msg);
    }

    /**
     * Log in error mode.
     * @param msg the log-message
     */
    protected final void loge(@NonNull String msg) {
        log(Log.ERROR, msg);
    }

    /**
     * Common log-method with using {@link #TAG}.
     *
     * @param priority the log priority
     * @param msg the log message
     */
    private void log(int priority, @NonNull String msg) {
        if (/*BuildConfig.DEBUG ||*/ Log.isLoggable(TAG, priority)) {
            Log.println(priority, TAG, msg);
        }
    }
}
