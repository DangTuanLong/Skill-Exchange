package com.skillexchange.api.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.apache.pdfbox.Loader
import org.apache.pdfbox.io.RandomAccessReadBuffer
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.test.assertTrue

/**
 * Benchmark đo lường hiệu năng, đỉnh RAM và thời gian xử lý khi trích xuất PDF (TASK-043 Mức 2).
 * Tách biệt khỏi bộ kiểm thử mặc định (chạy qua task: .\gradlew.bat benchmarkTest).
 */
class PdfMemoryBenchmarkTest {

    private fun generateRealisticPdf(pageCount: Int = 5, targetSizeBytes: Int = 2 * 1024 * 1024): ByteArray {
        val document = PDDocument()
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)

        val paragraphs = listOf(
            "Curriculum Vitae - Senior Software Engineer & Technical Architect",
            "Full Stack Developer specializing in Kotlin, Ktor, Jetpack Compose, Android SDK, and PostgreSQL.",
            "Proficient in Docker, Git, CI/CD, Microservices architecture, REST APIs, GraphQL, and Cloud Infrastructure.",
            "Demonstrated experience in UI/UX Design with Figma, State Management, Room Database, and Supabase.",
            "Experienced in mentoring junior developers, conducting code reviews, and optimizing JVM performance."
        )

        for (i in 1..pageCount) {
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)

            PDPageContentStream(document, page).use { contentStream ->
                contentStream.beginText()
                contentStream.setFont(font, 12f)
                contentStream.newLineAtOffset(50f, 750f)

                contentStream.showText("Page $i of $pageCount - Candidate Profile & Skill Verification")
                contentStream.newLineAtOffset(0f, -20f)

                for (p in paragraphs) {
                    contentStream.showText(p)
                    contentStream.newLineAtOffset(0f, -15f)
                }

                for (line in 1..40) {
                    contentStream.showText("Skill Item $line: Advanced expertise in Software Engineering and Distributed Systems.")
                    contentStream.newLineAtOffset(0f, -12f)
                }
                contentStream.endText()
            }
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()

        val basePdfBytes = out.toByteArray()
        if (basePdfBytes.size < targetSizeBytes) {
            val neededPadding = targetSizeBytes - basePdfBytes.size
            val paddedOut = ByteArrayOutputStream()
            paddedOut.write(basePdfBytes)
            paddedOut.write("\n% PADDING DATA FOR BENCHMARK 2MB: ".toByteArray())
            val chunk = ByteArray(8192) { 0x41.toByte() }
            var written = 0
            while (written < neededPadding) {
                val toWrite = minOf(chunk.size, neededPadding - written)
                paddedOut.write(chunk, 0, toWrite)
                written += toWrite
            }
            paddedOut.write("\n".toByteArray())
            return paddedOut.toByteArray()
        }

        return basePdfBytes
    }

    /**
     * Tạo tệp PDF "xấu": cấu trúc nén dày đặc, 5 trang phức tạp với nhiều đối tượng stream lạ và text đè
     */
    private fun generateComplexOrUglyPdf(): ByteArray {
        val document = PDDocument()
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val italic = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)

        for (p in 1..5) {
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            PDPageContentStream(document, page).use { cs ->
                cs.beginText()
                cs.setFont(font, 10f)
                cs.newLineAtOffset(20f, 800f)
                cs.showText("--- UGLY COMPACT DENSE COMPLEX PAGE $p ---")
                cs.newLineAtOffset(0f, -15f)

                // Trộn lẫn các đoạn font khác nhau, ma trận offset lạ mô phỏng PDF render từ web/tool cũ
                for (i in 1..50) {
                    cs.setFont(if (i % 2 == 0) font else italic, 9f)
                    cs.showText("DataChunk_$i: Micro-fragment with complex offsets and dense text stream.")
                    cs.newLineAtOffset(0f, -14f)
                }
                cs.endText()
            }
        }

        val out = ByteArrayOutputStream()
        document.save(out)
        document.close()
        val raw = out.toByteArray()

        // Thêm các comment phức tạp và padding mô phỏng tệp ~2MB với cấu trúc stream nén
        val padded = ByteArrayOutputStream()
        padded.write(raw)
        val padTarget = 2 * 1024 * 1024
        if (padded.size() < padTarget) {
            val dummyComment = "\n% DENSE_CORRUPTED_METADATA_STREAM_PADDING_TEST\n".toByteArray()
            padded.write(dummyComment)
            val fill = ByteArray(4096) { ((it % 90) + 33).toByte() }
            while (padded.size() < padTarget) {
                padded.write(fill, 0, minOf(fill.size, padTarget - padded.size()))
            }
        }
        return padded.toByteArray()
    }

    private fun extractTextFromPdf(pdfBytes: ByteArray): Pair<String, Int> {
        Loader.loadPDF(RandomAccessReadBuffer(pdfBytes)).use { doc ->
            val pageCount = doc.numberOfPages
            val stripper = PDFTextStripper()
            val text = stripper.getText(doc)
            return Pair(text, pageCount)
        }
    }

    private fun getUsedMemoryMb(): Double {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        return usedBytes / (1024.0 * 1024.0)
    }

    @Test
    fun benchmarkSingleAndConcurrentExtraction() {
        println("=== BẮT ĐẦU BENCHMARK THỰC TẾ TRÍCH XUẤT PDF VỚI APACHE PDFBOX 3.0.8 ===")

        val pdfBytes = generateRealisticPdf(pageCount = 5, targetSizeBytes = 2 * 1024 * 1024)
        println("1. Kích thước PDF thử nghiệm: ${pdfBytes.size} bytes (~${String.format("%.2f", pdfBytes.size / (1024.0 * 1024.0))} MB)")

        // Warm-up JVM & GC baseline
        repeat(2) { extractTextFromPdf(pdfBytes) }
        System.gc()
        Thread.sleep(200)
        val baselineMemoryMb = getUsedMemoryMb()
        println("2. Mức RAM baseline trước khi trích xuất: ${String.format("%.2f", baselineMemoryMb)} MB")

        // 1 Request đơn lẻ
        val startSingle = System.currentTimeMillis()
        var singlePeak = baselineMemoryMb
        val (text, pages) = extractTextFromPdf(pdfBytes)
        val singleUsed = getUsedMemoryMb()
        if (singleUsed > singlePeak) singlePeak = singleUsed
        val durationSingle = System.currentTimeMillis() - startSingle

        println("3. KẾT QUẢ ĐO 1 REQUEST (~2MB / 5 trang):")
        println("   - Số trang phát hiện: $pages")
        println("   - Độ dài văn bản trích xuất: ${text.length} ký tự")
        println("   - Thời gian xử lý: ${durationSingle} ms")
        println("   - Đỉnh RAM tức thời: ${String.format("%.2f", singlePeak)} MB (Tăng: +${String.format("%.2f", singlePeak - baselineMemoryMb)} MB)")

        // 3 Requests đồng thời (Concurrent 3 requests)
        System.gc()
        Thread.sleep(200)
        val preConcurrentBase = getUsedMemoryMb()

        var concurrentPeak = preConcurrentBase
        val startConcurrent = System.currentTimeMillis()

        runBlocking(Dispatchers.IO) {
            val jobs = (1..3).map { reqId ->
                async {
                    val reqStart = System.currentTimeMillis()
                    val (extractedText, _) = extractTextFromPdf(pdfBytes)
                    val mem = getUsedMemoryMb()
                    synchronized(this@PdfMemoryBenchmarkTest) {
                        if (mem > concurrentPeak) concurrentPeak = mem
                    }
                    val reqTime = System.currentTimeMillis() - reqStart
                    Pair(reqId, reqTime)
                }
            }
            val results = jobs.awaitAll()
            val totalConcurrentDuration = System.currentTimeMillis() - startConcurrent

            println("4. KẾT QUẢ ĐO 3 REQUEST ĐỒNG THỜI (CONCURRENT 3 REQUESTS):")
            println("   - Tổng thời gian hoàn thành cả 3 requests: ${totalConcurrentDuration} ms")
            results.forEach { (id, time) ->
                println("     * Request $id: ${time} ms")
            }
            println("   - Đỉnh RAM cao nhất trong khi 3 request chạy: ${String.format("%.2f", concurrentPeak)} MB")
            println("   - Mức tăng RAM ròng (Delta): +${String.format("%.2f", concurrentPeak - preConcurrentBase)} MB")
            println("   - Đỉnh RAM tối đa toàn bộ JVM (Heap Max): ${String.format("%.2f", Runtime.getRuntime().maxMemory() / (1024.0 * 1024.0))} MB")
        }

        // Đo với PDF "xấu" (cấu trúc phức tạp, mật độ cao, font khác nhau, metadata stream nặng)
        println("5. ĐO VỚI PDF 'XẤU' (DENSE COMPLEX STRUCTURE ~2MB / 5 TRANG):")
        val uglyPdf = generateComplexOrUglyPdf()
        System.gc()
        Thread.sleep(200)
        val preUglyBase = getUsedMemoryMb()
        val startUgly = System.currentTimeMillis()
        val (uglyText, uglyPages) = extractTextFromPdf(uglyPdf)
        val uglyPeak = getUsedMemoryMb()
        val uglyDuration = System.currentTimeMillis() - startUgly

        println("   - Kích thước PDF xấu: ${uglyPdf.size} bytes (~${String.format("%.2f", uglyPdf.size / (1024.0 * 1024.0))} MB)")
        println("   - Số trang: $uglyPages")
        println("   - Ký tự trích xuất: ${uglyText.length}")
        println("   - Thời gian xử lý: ${uglyDuration} ms")
        println("   - Đỉnh RAM khi trích xuất PDF xấu: ${String.format("%.2f", uglyPeak)} MB (Tăng ròng: +${String.format("%.2f", uglyPeak - preUglyBase)} MB)")

        assertTrue(text.isNotBlank(), "Văn bản trích xuất không được rỗng")
        assertTrue(uglyText.isNotBlank(), "Văn bản PDF xấu không được rỗng")
        println("=== KẾT THÚC BENCHMARK THÀNH CÔNG ===")
    }
}
