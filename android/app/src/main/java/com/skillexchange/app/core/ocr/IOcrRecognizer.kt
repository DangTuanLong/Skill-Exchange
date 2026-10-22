package com.skillexchange.app.core.ocr

import android.net.Uri

interface IOcrRecognizer {
    suspend fun recognizeText(imageUri: Uri): Result<String>
}
