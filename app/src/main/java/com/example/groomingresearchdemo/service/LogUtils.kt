package com.example.groomingresearchdemo.service

import android.util.Log
import com.example.groomingresearchdemo.AppConstants

/**
 * One narrow logging entry point. Every Log call in the app goes through here so it is easy to
 * audit that nothing sensitive ever leaks to Logcat. The rule this file enforces by convention:
 * log lifecycle, arm state, package match, root availability, structural counts, and exception
 * types — never node text, never message content, never user identifiers.
 */
object LogUtils {
    private const val TAG = AppConstants.LOG_TAG

    fun i(message: String) = Log.i(TAG, message)
    fun w(message: String, t: Throwable? = null) = Log.w(TAG, message, t)
    fun e(message: String, t: Throwable? = null) = Log.e(TAG, message, t)
}
