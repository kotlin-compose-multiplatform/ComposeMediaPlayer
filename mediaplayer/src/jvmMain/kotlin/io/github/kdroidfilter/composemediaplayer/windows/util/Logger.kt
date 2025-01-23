package io.github.kdroidfilter.composemediaplayer.windows.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Simple logger with timestamps.
 */
class Logger(private val tag: String) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun log(message: String) {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        println("[$timestamp] $tag: $message")
    }

    fun error(message: String, throwable: Throwable? = null) {
        val timestamp = LocalDateTime.now().format(dateFormatter)
        System.err.println("[$timestamp] $tag ERROR: $message")
        throwable?.printStackTrace()
    }
}