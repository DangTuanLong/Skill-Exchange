package com.skillexchange.api.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.skillexchange.api.models.ApiError
import com.skillexchange.api.models.ErrorCodes
import com.skillexchange.api.models.nlp.ExtractSkillsResponse
import com.skillexchange.api.plugins.configureSerialization
import com.skillexchange.api.plugins.configureStatusPages
import com.skillexchange.api.services.nlp.CancellablePdfTextStripper
import com.skillexchange.api.services.nlp.DocumentParser
import com.skillexchange.api.services.nlp.DocumentRateLimiter
import com.skillexchange.api.services.nlp.SkillDefinition
import com.skillexchange.api.services.nlp.SkillExtractionService
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.PDJavascriptNameTreeNode
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript
import org.apache.pdfbox.pdmodel.interactive.action.PDDocumentCatalogAdditionalActions
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Semaphore
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentNlpRoutesTest {

    private val testSkills = listOf(
        SkillDefinition(1, "Android (Kotlin)", 1, "Lập trình"),
        SkillDefinition(2, "Python", 1, "Lập trình"),
        SkillDefinition(3, "Java", 1, "Lập trình"),
        SkillDefinition(4, "JavaScript", 1, "Lập trình"),
        SkillDefinition(5, "C++", 1, "Lập trình"),
        SkillDefinition(8, "Tiếng Anh", 2, "Ngoại ngữ"),
        SkillDefinition(9, "Guitar", 3, "Âm nhạc"),
        SkillDefinition(10, "Adobe Photoshop", 4, "Thiết kế")
    )

    private val skillExtractionService = SkillExtractionService(skillProvider = { testSkills })

    private fun createTestJwt(userId: String = "test-doc-user-1"): String {
        return JWT.create()
            .withSubject(userId)
            .withAudience("authenticated")
            .sign(Algorithm.HMAC256("test-secret"))
    }

    private fun testApp(
        customDocumentRateLimiter: DocumentRateLimiter = DocumentRateLimiter(maxPerMinute = 10),
        customConcurrencyLimiter: Semaphore = Semaphore(3),
        block: suspend (io.ktor.client.HttpClient) -> Unit
    ) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            install(Authentication) {
                jwt("auth-jwt") {
                    verifier(
                        JWT.require(Algorithm.HMAC256("test-secret"))
                            .withAudience("authenticated")
                            .build()
                    )
                    validate { credential ->
                        if (credential.payload.subject != null) {
                            JWTPrincipal(credential.payload)
                        } else null
                    }
                }
            }
            routing {
                nlpRoutes(
                    skillExtractionService = skillExtractionService,
                    documentRateLimiter = customDocumentRateLimiter,
                    documentConcurrencyLimiter = customConcurrencyLimiter
                )
            }
        }

        val client = createClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        block(client)
    }

    // --- HELPER TẠO FILE PDF PHỤC VỤ TEST ---

    private fun createValidPdfBytes(text: String, pages: Int = 1): ByteArray {
        val doc = PDDocument()
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        for (i in 1..pages) {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(font, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Page $i: $text")
                cs.endText()
            }
        }
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun createEncryptedPdfBytes(): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val spp = StandardProtectionPolicy("owner-pass", "user-pass", AccessPermission())
        doc.protect(spp)
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun createPdfWithOpenAction(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        doc.documentCatalog.openAction = PDActionJavaScript("app.alert('malicious script');")
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun createPdfWithCatalogAA(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        doc.documentCatalog.cosObject.setItem(org.apache.pdfbox.cos.COSName.AA, org.apache.pdfbox.cos.COSDictionary())
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun createPdfWithJavaScriptNames(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val names = PDDocumentNameDictionary(doc.documentCatalog)
        val jsTree = PDJavascriptNameTreeNode()
        jsTree.names = mapOf("malicious" to PDActionJavaScript("app.alert('js');"))
        names.cosObject.setItem(org.apache.pdfbox.cos.COSName.JAVA_SCRIPT, jsTree.cosObject)
        doc.documentCatalog.names = names
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun createPdfWithEmbeddedFiles(): ByteArray {
        val doc = PDDocument()
        doc.addPage(PDPage(PDRectangle.A4))
        val efTree = PDEmbeddedFilesNameTreeNode()
        val fileSpec = PDComplexFileSpecification()
        fileSpec.file = "payload.exe"
        efTree.names = mapOf("payload" to fileSpec)
        val names = PDDocumentNameDictionary(doc.documentCatalog)
        names.cosObject.setItem(org.apache.pdfbox.cos.COSName.EMBEDDED_FILES, efTree.cosObject)
        doc.documentCatalog.names = names
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    private fun createPdfWithAnnotationAction(): ByteArray {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val link = PDAnnotationLink()
        link.action = PDActionJavaScript("app.alert('annot js');")
        page.annotations.add(link)
        val out = ByteArrayOutputStream()
        doc.save(out)
        doc.close()
        return out.toByteArray()
    }

    // --- TESTS MỨC 1 (.TXT, .MD) ---

    @Test
    fun testExtractSkillsDocument_validTxtFile_returns200AndExtractsSkills() = testApp { client ->
        val fileContent = "Kỹ sư phần mềm thành thạo lập trình Android (Kotlin) và Java. Giao tiếp Tiếng Anh tốt."
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", fileContent.toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"my_cv.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)
        val skillNames = body.data.map { it.skillName }
        assertTrue(skillNames.contains("Android (Kotlin)"))
        assertTrue(skillNames.contains("Java"))
        assertTrue(skillNames.contains("Tiếng Anh"))
    }

    @Test
    fun testExtractSkillsDocument_validMdFile_returns200AndExtractsSkills() = testApp { client ->
        val fileContent = "# Hồ sơ cá nhân\n\n- Thành thạo Python và C++\n- Có sở thích chơi Guitar"
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", fileContent.toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/markdown")
                            append(HttpHeaders.ContentDisposition, "filename=\"resume.md\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)
        val skillNames = body.data.map { it.skillName }
        assertTrue(skillNames.contains("Python"))
        assertTrue(skillNames.contains("C++"))
        assertTrue(skillNames.contains("Guitar"))
    }

    @Test
    fun testExtractSkillsDocument_fileTooLarge_returns413PayloadTooLarge() = testApp { client ->
        val chunk = "0123456789ABCDEF".toByteArray(Charsets.UTF_8) // 16 bytes
        val totalChunks = (2 * 1024 * 1024 / 16) + 10 // > 2MB
        val largeBytes = ByteArrayOutputStream().apply {
            repeat(totalChunks) { write(chunk) }
        }.toByteArray()

        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", largeBytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"huge.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.PayloadTooLarge, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PAYLOAD_TOO_LARGE, err.code)
    }

    @Test
    fun testExtractSkillsDocument_textExceeds10kChars_returns422TextTooLong() = testApp { client ->
        val longText = "A".repeat(10_001)
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", longText.toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"long.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.TEXT_TOO_LONG, err.code)
    }

    @Test
    fun testExtractSkillsDocument_invalidUtf8Encoding_returns422InvalidEncoding() = testApp { client ->
        val invalidUtf8Bytes = byteArrayOf(0x48, 0x65, 0x6C, 0x6C, 0x6F, 0xC3.toByte(), 0x28)
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", invalidUtf8Bytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"bad_utf8.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.INVALID_ENCODING, err.code)
    }

    @Test
    fun testExtractSkillsDocument_emptyFile_returns422EmptyFile() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", ByteArray(0), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"empty.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.EMPTY_FILE, err.code)
    }

    @Test
    fun testExtractSkillsDocument_zipExtension_returns415UnsupportedFileType() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", "fake content".toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "application/zip")
                            append(HttpHeaders.ContentDisposition, "filename=\"archive.zip\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.UNSUPPORTED_FILE_TYPE, err.code)
        assertTrue(err.message.contains("ZIP"))
    }

    @Test
    fun testExtractSkillsDocument_zipMagicBytes_returns415UnsupportedFileType() = testApp { client ->
        val disguisedZipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00)
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", disguisedZipBytes, Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"disguised.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.UNSUPPORTED_FILE_TYPE, err.code)
        assertTrue(err.message.contains("ZIP"))
    }

    // --- TESTS MỨC 2: TÀI LIỆU PDF ---

    @Test
    fun testExtractSkillsDocument_validPdf_returns200AndExtractsSkills() = testApp { client ->
        val pdfBytes = createValidPdfBytes("Software engineer skilled in Android, Java and Python.", pages = 2)
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"resume.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        assertTrue(body.success)
        val skills = body.data.map { it.skillName }
        assertTrue(skills.contains("Android (Kotlin)"))
        assertTrue(skills.contains("Java"))
        assertTrue(skills.contains("Python"))
    }

    @Test
    fun testExtractSkillsDocument_scannedPdfNoText_returns422PdfNoText() = testApp { client ->
        // Tạo PDF chỉ có 5 ký tự (< 20 ký tự tối thiểu)
        val pdfBytes = createValidPdfBytes("Short", pages = 1)
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"scan.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_NO_TEXT, err.code)
        assertTrue(err.message.contains("scan"))
    }

    @Test
    fun testExtractSkillsDocument_encryptedPdf_returns422PdfEncrypted() = testApp { client ->
        val pdfBytes = createEncryptedPdfBytes()
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"locked.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_ENCRYPTED, err.code)
        assertTrue(err.message.contains("mã hóa"))
    }

    @Test
    fun testExtractSkillsDocument_pdfTooManyPages_returns422PdfTooManyPages() = testApp { client ->
        val pdfBytes = createValidPdfBytes("Valid page content for page count limit test.", pages = 6) // Vượt quá 5 trang
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"long_book.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_TOO_MANY_PAGES, err.code)
        assertTrue(err.message.contains("tối đa 5 trang"))
    }

    @Test
    fun testExtractSkillsDocument_pdfWithOpenAction_returns422PdfUnsafeContent() = testApp { client ->
        val pdfBytes = createPdfWithOpenAction()
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"open_action.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_UNSAFE_CONTENT, err.code)
    }

    @Test
    fun testExtractSkillsDocument_pdfWithCatalogAA_returns422PdfUnsafeContent() = testApp { client ->
        val pdfBytes = createPdfWithCatalogAA()
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"catalog_aa.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_UNSAFE_CONTENT, err.code)
    }

    @Test
    fun testExtractSkillsDocument_pdfWithJavaScriptNames_returns422PdfUnsafeContent() = testApp { client ->
        val pdfBytes = createPdfWithJavaScriptNames()
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"js_names.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_UNSAFE_CONTENT, err.code)
    }

    @Test
    fun testExtractSkillsDocument_pdfWithEmbeddedFiles_returns422PdfUnsafeContent() = testApp { client ->
        val pdfBytes = createPdfWithEmbeddedFiles()
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"embedded.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_UNSAFE_CONTENT, err.code)
    }

    @Test
    fun testExtractSkillsDocument_pdfWithAnnotationAction_returns422PdfUnsafeContent() = testApp { client ->
        val pdfBytes = createPdfWithAnnotationAction()
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"annotation_action.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.PDF_UNSAFE_CONTENT, err.code)
    }

    @Test
    fun testExtractSkillsDocument_pdfFakeMagicBytes_returns415UnsupportedFileType() = testApp { client ->
        val fakePdfBytes = "THIS IS NOT A VALID PDF FILE HEADER".toByteArray(Charsets.UTF_8)
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", fakePdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"fake.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.UNSUPPORTED_FILE_TYPE, err.code)
    }

    @Test
    fun testExtractSkillsDocument_verifyNoTempFilesCreated() = testApp { client ->
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        val filesBefore = tmpDir.list()?.toSet() ?: emptySet()

        val pdfBytes = createValidPdfBytes("Verify no temp files created on disk: Android Kotlin Java", pages = 3)
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", pdfBytes, Headers.build {
                            append(HttpHeaders.ContentType, "application/pdf")
                            append(HttpHeaders.ContentDisposition, "filename=\"mem_check.pdf\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)

        val filesAfter = tmpDir.list()?.toSet() ?: emptySet()
        val newFiles = filesAfter - filesBefore
        val newPdfBoxFiles = newFiles.filter { it.contains("pdfbox", ignoreCase = true) || it.endsWith(".tmp") }
        assertTrue(newPdfBoxFiles.isEmpty(), "Phát hiện tệp tạm PDFBox được tạo ra trong tmpdir: $newPdfBoxFiles")
    }

    @Test
    fun testExtractSkillsDocument_cooperativeCancellation_stopsBetweenPages() {
        val pdfBytes = createValidPdfBytes("Cancellation check page data.", pages = 3)
        var pagesVisited = 0

        assertFailsWith<CancellationException> {
            Loader.loadPDF(RandomAccessReadBuffer(pdfBytes)).use { doc ->
                val stripper = CancellablePdfTextStripper {
                    pagesVisited++
                    if (pagesVisited >= 2) {
                        throw CancellationException("Simulated coroutine timeout or cancellation after page 1")
                    }
                }
                stripper.getText(doc)
            }
        }

        assertEquals(2, pagesVisited, "Stripper phải dừng ngay lập tức khi callback ném CancellationException")
    }

    // --- SECURITY, LIMITS & HEADERS ---

    @Test
    fun testExtractSkillsDocument_noJwt_returns401Unauthorized() = testApp { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", "Kotlin content".toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"doc.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun testExtractSkillsDocument_rateLimiting_returns429RateLimited() = testApp(
        customDocumentRateLimiter = DocumentRateLimiter(maxPerMinute = 2)
    ) { client ->
        val token = createTestJwt("user-rate-test-distinct")
        val makeReq = suspend {
            client.post("/api/nlp/extract-skills-document") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", "Tôi học Python".toByteArray(Charsets.UTF_8), Headers.build {
                                append(HttpHeaders.ContentType, "text/plain")
                                append(HttpHeaders.ContentDisposition, "filename=\"doc.txt\"")
                            })
                        }
                    )
                )
            }
        }

        assertEquals(HttpStatusCode.OK, makeReq().status)
        assertEquals(HttpStatusCode.OK, makeReq().status)
        val res3 = makeReq()
        assertEquals(HttpStatusCode.TooManyRequests, res3.status)
        val err = Json.decodeFromString<ApiError>(res3.bodyAsText())
        assertEquals(ErrorCodes.RATE_LIMITED, err.code)
    }

    @Test
    fun testExtractSkillsDocument_concurrencyLimit_returns503WithRetryAfterHeader() = testApp(
        customConcurrencyLimiter = Semaphore(0)
    ) { client ->
        val response = client.post("/api/nlp/extract-skills-document") {
            header(HttpHeaders.Authorization, "Bearer ${createTestJwt("concurrency-test-user")}")
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("file", "Nội dung tài liệu".toByteArray(Charsets.UTF_8), Headers.build {
                            append(HttpHeaders.ContentType, "text/plain")
                            append(HttpHeaders.ContentDisposition, "filename=\"doc.txt\"")
                        })
                    }
                )
            )
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("5", response.headers["Retry-After"])
        val err = Json.decodeFromString<ApiError>(response.bodyAsText())
        assertEquals(ErrorCodes.SERVER_BUSY, err.code)
    }

    @Test
    fun testExtractSkillsDocument_benchmarkLongText_measuresRealTime() = testApp { client ->
        val paragraph = """
            Trong quá trình công tác, tôi đã phát triển nhiều dự án ứng dụng di động Android bằng ngôn ngữ Kotlin.
            Hệ thống backend được xây dựng trên nền tảng Python và Java với khả năng xử lý dữ liệu lớn.
            Tôi có thể giao tiếp và đọc tài liệu chuyên ngành bằng Tiếng Anh. Ngoài ra, tôi có sở thích chơi Guitar và thiết kế hình ảnh với Adobe Photoshop.
        """.trimIndent()

        val sb = StringBuilder()
        while (sb.length + paragraph.length < 9_800) {
            sb.append(paragraph).append("\n\n")
        }
        val realLongText = sb.toString()
        assertTrue(realLongText.length in 8_000..9_999)

        var responseStatus: HttpStatusCode? = null
        var responseBody: ExtractSkillsResponse? = null

        val elapsedMs = measureTimeMillis {
            val response = client.post("/api/nlp/extract-skills-document") {
                header(HttpHeaders.Authorization, "Bearer ${createTestJwt()}")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("file", realLongText.toByteArray(Charsets.UTF_8), Headers.build {
                                append(HttpHeaders.ContentType, "text/plain; charset=utf-8")
                                append(HttpHeaders.ContentDisposition, "filename=\"cv_full.txt\"")
                            })
                        }
                    )
                )
            }
            responseStatus = response.status
            responseBody = Json.decodeFromString<ExtractSkillsResponse>(response.bodyAsText())
        }

        assertEquals(HttpStatusCode.OK, responseStatus)
        assertTrue(responseBody!!.success)
        assertTrue(responseBody!!.data.isNotEmpty())
        assertTrue(elapsedMs < 2000, "Thời gian xử lý $elapsedMs ms vượt quá ngưỡng chấp nhận 2000ms")
    }
}
