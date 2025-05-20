package com.neel.grepshot.data.model

import android.net.Uri

data class ScreenshotWithText(
    val uri: Uri,
    val name: String,
    val extractedText: String
)