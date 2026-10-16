package com.skillexchange.api.services.nlp

import com.skillexchange.api.models.ErrorCodes
import io.ktor.http.HttpStatusCode
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class DocumentParseException(
    val statusCode: HttpStatusCode,
    val errorCode: String,
    override val message: String
) : Exception(message)

object DocumentParser {
    const val MAX_FILE_BYTES: Long = 2 * 1024 * 1024L // 2MB
    const val MAX_TEXT_CHARS: Int = 10_000

    private val ZIP_MAGIC_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
    private val ZIP_EMPTY_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x05.toByte(), 0x06.toByte())
    private val ZIP_SPANNED_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x07.toByte(), 0x08.toByte())

    fun parseTextDocument(
        inputStream: InputStream,
        fileName: String,
        maxBytes: Long = MAX_FILE_BYTES,
        maxChars: Int = MAX_TEXT_CHARS
    ): String {
        val lowerName = fileName.lowercase()
        if (lowerName.endsWith(".zip")) {
            throw DocumentParseException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.UNSUPPORTED_FILE_TYPE,
                "Định dạng tệp không được hỗ trợ. Ứng dụng không hỗ trợ tệp nén ZIP; chỉ hỗ trợ tệp văn bản .txt và .md."
            )
        }

        if (!lowerName.endsWith(".txt") && !lowerName.endsWith(".md")) {
            throw DocumentParseException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.UNSUPPORTED_FILE_TYPE,
                "Định dạng tệp không được hỗ trợ. Chỉ hỗ trợ tệp văn bản .txt và .md."
            )
        }

        val buffer = ByteArray(8192)
        var totalBytesRead = 0L
        val byteCollector = ByteArrayOutputStream()

        var bytesRead: Int
        var isFirstChunk = true

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            totalBytesRead += bytesRead
            if (totalBytesRead > maxBytes) {
                throw DocumentParseException(
                    HttpStatusCode.PayloadTooLarge,
                    ErrorCodes.PAYLOAD_TOO_LARGE,
                    "Dung lượng tệp vượt quá giới hạn 2MB."
                )
            }

            if (isFirstChunk) {
                isFirstChunk = false
                if (bytesRead >= 4) {
                    val first4 = buffer.sliceArray(0..3)
                    if (first4.contentEquals(ZIP_MAGIC_HEADER) ||
                        first4.contentEquals(ZIP_EMPTY_HEADER) ||
                        first4.contentEquals(ZIP_SPANNED_HEADER)
                    ) {
                        throw DocumentParseException(
                            HttpStatusCode.UnprocessableEntity,
                            ErrorCodes.UNSUPPORTED_FILE_TYPE,
                            "Định dạng tệp không được hỗ trợ. Ứng dụng không hỗ trợ tệp nén ZIP; chỉ hỗ trợ tệp văn bản .txt và .md."
                        )
                    }
                }
            }

            byteCollector.write(buffer, 0, bytesRead)
        }

        val allBytes = byteCollector.toByteArray()
        if (allBytes.isEmpty()) {
            throw DocumentParseException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.EMPTY_FILE,
                "Tệp không có nội dung văn bản."
            )
        }

        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val charBuffer = try {
            decoder.decode(ByteBuffer.wrap(allBytes))
        } catch (e: CharacterCodingException) {
            throw DocumentParseException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.INVALID_ENCODING,
                "Tệp không phải định dạng văn bản UTF-8 hợp lệ."
            )
        }

        val text = charBuffer.toString().removePrefix("\uFEFF")
        if (text.isBlank()) {
            throw DocumentParseException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.EMPTY_FILE,
                "Tệp không có nội dung văn bản."
            )
        }

        if (text.length > maxChars) {
            throw DocumentParseException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.TEXT_TOO_LONG,
                "Nội dung văn bản vượt quá giới hạn 10.000 ký tự (hiện tại: ${text.length} ký tự). Vui lòng rút gọn tài liệu."
            )
        }

        return text
    }
}
