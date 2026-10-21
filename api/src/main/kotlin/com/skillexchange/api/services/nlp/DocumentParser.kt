package com.skillexchange.api.services.nlp

import com.skillexchange.api.models.ErrorCodes
import io.ktor.http.HttpStatusCode
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch
import org.apache.pdfbox.text.PDFTextStripper
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

/**
 * Stripper hỗ trợ dừng sớm giữa các trang khi coroutine bị hủy hoặc timeout.
 */
class CancellablePdfTextStripper(
    private val checkCancelled: (() -> Unit)? = null
) : PDFTextStripper() {
    override fun startPage(page: PDPage?) {
        checkCancelled?.invoke()
        super.startPage(page)
    }
}

object DocumentParser {
    const val MAX_FILE_BYTES: Long = 2 * 1024 * 1024L // 2MB
    const val MAX_TEXT_CHARS: Int = 10_000
    const val MAX_PDF_PAGES: Int = 5
    const val MIN_PDF_TEXT_CHARS: Int = 20

    private val ZIP_MAGIC_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte())
    private val ZIP_EMPTY_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x05.toByte(), 0x06.toByte())
    private val ZIP_SPANNED_HEADER = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x07.toByte(), 0x08.toByte())
    private val PDF_MAGIC_HEADER = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte()) // %PDF

    fun parseDocument(
        inputStream: InputStream,
        fileName: String,
        maxBytes: Long = MAX_FILE_BYTES,
        maxChars: Int = MAX_TEXT_CHARS,
        maxPages: Int = MAX_PDF_PAGES,
        checkCancelled: (() -> Unit)? = null
    ): String {
        val lowerName = fileName.lowercase()

        // 1. Kiểm tra phần mở rộng tệp
        if (lowerName.endsWith(".zip")) {
            throw DocumentParseException(
                HttpStatusCode.UnsupportedMediaType,
                ErrorCodes.UNSUPPORTED_FILE_TYPE,
                "Định dạng tệp không được hỗ trợ. Ứng dụng không hỗ trợ tệp nén ZIP; chỉ hỗ trợ tệp văn bản .txt, .md và tài liệu .pdf."
            )
        }

        val isPdf = lowerName.endsWith(".pdf")
        val isText = lowerName.endsWith(".txt") || lowerName.endsWith(".md")

        if (!isPdf && !isText) {
            throw DocumentParseException(
                HttpStatusCode.UnsupportedMediaType,
                ErrorCodes.UNSUPPORTED_FILE_TYPE,
                "Định dạng tệp không được hỗ trợ. Ứng dụng chỉ hỗ trợ tệp văn bản .txt, .md và tài liệu .pdf."
            )
        }

        // 2. Đọc luồng giới hạn dung lượng bộ đệm (chống DoS / tràn RAM)
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
                    // Kiểm tra nếu là tệp ZIP giả mạo
                    if (first4.contentEquals(ZIP_MAGIC_HEADER) ||
                        first4.contentEquals(ZIP_EMPTY_HEADER) ||
                        first4.contentEquals(ZIP_SPANNED_HEADER)
                    ) {
                        throw DocumentParseException(
                            HttpStatusCode.UnsupportedMediaType,
                            ErrorCodes.UNSUPPORTED_FILE_TYPE,
                            "Định dạng tệp không được hỗ trợ. Ứng dụng không hỗ trợ tệp nén ZIP; chỉ hỗ trợ tệp văn bản .txt, .md và tài liệu .pdf."
                        )
                    }

                    // Nếu đuôi là .pdf thì 4 byte đầu bắt buộc phải là %PDF
                    if (isPdf && !first4.contentEquals(PDF_MAGIC_HEADER)) {
                        throw DocumentParseException(
                            HttpStatusCode.UnsupportedMediaType,
                            ErrorCodes.UNSUPPORTED_FILE_TYPE,
                            "Tệp không phải định dạng PDF hợp lệ (sai mã nhận dạng magic bytes)."
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

        return if (isPdf) {
            parsePdfBytes(allBytes, maxChars, maxPages, checkCancelled)
        } else {
            parseTextBytes(allBytes, maxChars)
        }
    }

    private fun parseTextBytes(allBytes: ByteArray, maxChars: Int): String {
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

    private fun parsePdfBytes(
        allBytes: ByteArray,
        maxChars: Int,
        maxPages: Int,
        checkCancelled: (() -> Unit)?
    ): String {
        // Cấu hình hoàn toàn in-memory qua RandomAccessReadBuffer (không ghi tệp tạm disk)
        val doc: PDDocument = try {
            Loader.loadPDF(RandomAccessReadBuffer(allBytes))
        } catch (e: org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            throw DocumentParseException(
                HttpStatusCode.UnprocessableEntity,
                ErrorCodes.PDF_ENCRYPTED,
                "Tệp PDF đã bị mã hóa hoặc có mật khẩu bảo vệ. Vui lòng mở khóa tệp trước khi tải lên."
            )
        } catch (e: Exception) {
            if (e.message?.contains("password", ignoreCase = true) == true ||
                e.message?.contains("encrypt", ignoreCase = true) == true) {
                throw DocumentParseException(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCodes.PDF_ENCRYPTED,
                    "Tệp PDF đã bị mã hóa hoặc có mật khẩu bảo vệ. Vui lòng mở khóa tệp trước khi tải lên."
                )
            }
            throw DocumentParseException(
                HttpStatusCode.UnsupportedMediaType,
                ErrorCodes.UNSUPPORTED_FILE_TYPE,
                "Không thể đọc cấu trúc tệp PDF hoặc tệp đã bị hư hỏng: ${e.message}"
            )
        }

        doc.use { document ->
            checkCancelled?.invoke()

            // 1. Kiểm tra mã hóa / mật khẩu TRƯỚC khi xử lý
            if (document.isEncrypted) {
                throw DocumentParseException(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCodes.PDF_ENCRYPTED,
                    "Tệp PDF đã bị mã hóa hoặc có mật khẩu bảo vệ. Vui lòng mở khóa tệp trước khi tải lên."
                )
            }

            // 2. Kiểm tra số trang TRƯỚC khi trích xuất
            val pageCount = document.numberOfPages
            if (pageCount > maxPages) {
                throw DocumentParseException(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCodes.PDF_TOO_MANY_PAGES,
                    "Tệp PDF vượt quá giới hạn cho phép (tối đa $maxPages trang, tệp hiện tại có $pageCount trang). Vui lòng rút gọn tài liệu."
                )
            }

            // 3. Kiểm tra bảo mật: Chặn script, hành động tự động và tệp nhúng
            val catalog = document.documentCatalog

            // 3.1. OpenAction & Catalog Additional Actions (AA)
            if (catalog.openAction != null || catalog.cosObject.containsKey(org.apache.pdfbox.cos.COSName.AA)) {
                throw DocumentParseException(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCodes.PDF_UNSAFE_CONTENT,
                    "Tệp PDF chứa hành động tự động không an toàn (OpenAction/Catalog Actions)."
                )
            }

            // 3.2. JavaScript Name Tree & EmbeddedFiles Name Tree qua Names dictionary
            val namesDict = catalog.names?.cosObject
            if (namesDict != null) {
                if (namesDict.containsKey(org.apache.pdfbox.cos.COSName.JAVA_SCRIPT) ||
                    namesDict.containsKey(org.apache.pdfbox.cos.COSName.JS)) {
                    throw DocumentParseException(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorCodes.PDF_UNSAFE_CONTENT,
                        "Tệp PDF chứa tập lệnh JavaScript không an toàn."
                    )
                }
                if (namesDict.containsKey(org.apache.pdfbox.cos.COSName.EMBEDDED_FILES)) {
                    throw DocumentParseException(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorCodes.PDF_UNSAFE_CONTENT,
                        "Tệp PDF chứa tệp đính kèm nhúng (EmbeddedFiles) không an toàn."
                    )
                }
            }

            // 3.4. Quét từng trang: Page Actions & Annotation Actions (JavaScript, Launch)
            for (page in document.pages) {
                checkCancelled?.invoke()

                if (page.cosObject.containsKey(org.apache.pdfbox.cos.COSName.AA)) {
                    throw DocumentParseException(
                        HttpStatusCode.UnprocessableEntity,
                        ErrorCodes.PDF_UNSAFE_CONTENT,
                        "Tệp PDF chứa hành động trang tự động (Page Additional Actions) không an toàn."
                    )
                }

                for (annot in page.annotations) {
                    val dict = annot.cosObject
                    if (dict is org.apache.pdfbox.cos.COSDictionary) {
                        if (dict.containsKey(org.apache.pdfbox.cos.COSName.AA)) {
                            throw DocumentParseException(
                                HttpStatusCode.UnprocessableEntity,
                                ErrorCodes.PDF_UNSAFE_CONTENT,
                                "Tệp PDF chứa chú thích với hành động tự động không an toàn."
                            )
                        }
                        if (dict.containsKey(org.apache.pdfbox.cos.COSName.A)) {
                            val actionDict = dict.getDictionaryObject(org.apache.pdfbox.cos.COSName.A)
                            if (actionDict is org.apache.pdfbox.cos.COSDictionary) {
                                val s = actionDict.getNameAsString(org.apache.pdfbox.cos.COSName.S)
                                if (s.equals("JavaScript", ignoreCase = true) || s.equals("Launch", ignoreCase = true)) {
                                    throw DocumentParseException(
                                        HttpStatusCode.UnprocessableEntity,
                                        ErrorCodes.PDF_UNSAFE_CONTENT,
                                        "Tệp PDF chứa chú thích với tập lệnh hoặc hành động thực thi không an toàn."
                                    )
                                }
                            }
                        }
                    }
                    if (annot is org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink) {
                        val action = annot.action
                        if (action is PDActionJavaScript || action is PDActionLaunch) {
                            throw DocumentParseException(
                                HttpStatusCode.UnprocessableEntity,
                                ErrorCodes.PDF_UNSAFE_CONTENT,
                                "Tệp PDF chứa chú thích với tập lệnh hoặc hành động thực thi không an toàn."
                            )
                        }
                    }
                }
            }

            // 4. Trích xuất văn bản có hỗ trợ kiểm tra hủy (cooperative cancellation giữa các trang)
            val stripper = CancellablePdfTextStripper(checkCancelled)
            val text = stripper.getText(document).trim().removePrefix("\uFEFF")

            // 5. Kiểm tra nội dung văn bản (phát hiện PDF scan hoặc rỗng không có chữ)
            val nonWhitespaceChars = text.count { !it.isWhitespace() }
            if (nonWhitespaceChars < MIN_PDF_TEXT_CHARS) {
                throw DocumentParseException(
                    HttpStatusCode.UnprocessableEntity,
                    ErrorCodes.PDF_NO_TEXT,
                    "Tệp PDF không chứa nội dung văn bản có thể đọc được (dạng ảnh scan hoặc ít hơn $MIN_PDF_TEXT_CHARS ký tự). Vui lòng sử dụng tệp PDF có chữ hoặc chuyển sang định dạng .txt/.md."
                )
            }

            // 6. Kiểm tra giới hạn độ dài ký tự tối đa (từ chối, tuyệt đối không cắt ngắn âm thầm)
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

    // Tương thích ngược với các lời gọi cũ
    fun parseTextDocument(
        inputStream: InputStream,
        fileName: String,
        maxBytes: Long = MAX_FILE_BYTES,
        maxChars: Int = MAX_TEXT_CHARS
    ): String = parseDocument(inputStream, fileName, maxBytes, maxChars)
}
