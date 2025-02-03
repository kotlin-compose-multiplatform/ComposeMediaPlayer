package io.github.kdroidfilter.composemediaplayer.util

import io.github.kdroidfilter.composemediaplayer.toUriString
import io.github.vinceglb.filekit.PlatformFile

actual fun PlatformFile.getUri(): String {
    return this.toUriString()
}