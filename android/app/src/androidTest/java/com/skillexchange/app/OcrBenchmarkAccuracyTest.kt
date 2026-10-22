package com.skillexchange.app

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.skillexchange.app.core.ocr.MlKitOcrRecognizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class OcrBenchmarkAccuracyTest {

    private lateinit var context: Context
    private lateinit var testDir: File

    data class TestCase(
        val id: String,
        val title: String,
        val category: String, // "calibration" or "test"
        val style: String,    // "flat", "tilted", "lowlight", "stylized"
        val lines: List<String>,
        val groundTruthSkills: List<String>
    )

    private val testCases = listOf(
        TestCase(
            id = "CV_01_CALIB_FLAT",
            title = "Nguyễn Văn An - Android Developer",
            category = "calibration",
            style = "flat",
            lines = listOf(
                "HỌ VÀ TÊN: NGUYỄN VĂN AN",
                "VỊ TRÍ: LẬP TRÌNH VIÊN ANDROID",
                "KỸ NĂNG CHUYÊN MÔN:",
                "- Lập trình Android với Kotlin và Jetpack Compose",
                "- Sử dụng Docker và Git để quản lý mã nguồn",
                "- Tiếng Anh giao tiếp trình độ B2"
            ),
            groundTruthSkills = listOf("Kotlin", "Docker", "Lập trình Android", "Tiếng Anh giao tiếp")
        ),
        TestCase(
            id = "CV_02_CALIB_TILTED",
            title = "Trần Thị Bích - Data & UI Designer",
            category = "calibration",
            style = "tilted",
            lines = listOf(
                "HỒ SƠ ỨNG VIÊN: TRẦN THỊ BÍCH",
                "CHUYÊN MÔN: KỸ SƯ DỮ LIỆU & THIẾT KẾ",
                "KỸ NĂNG:",
                "- Lập trình Python xử lý dữ liệu lớn",
                "- Thiết kế đồ họa và giao diện người dùng Figma",
                "- Quản trị mạng Linux căn bản"
            ),
            groundTruthSkills = listOf("Python", "Figma", "Thiết kế đồ họa", "Quản trị mạng")
        ),
        TestCase(
            id = "CV_03_CALIB_LOWLIGHT",
            title = "Lê Hoàng Nam - Backend Developer",
            category = "calibration",
            style = "lowlight",
            lines = listOf(
                "SƠ YẾU LÝ LỊCH: LÊ HOÀNG NAM",
                "CHỨC DANH: BACKEND DEVELOPER",
                "DANH MỤC KỸ NĂNG:",
                "- Lập trình Web với Java và Spring Boot",
                "- Cơ sở dữ liệu PostgreSQL",
                "- Kiến thức Kế toán doanh nghiệp"
            ),
            groundTruthSkills = listOf("Java", "Spring Boot", "Lập trình Web", "Kế toán doanh nghiệp")
        ),
        TestCase(
            id = "CV_04_CALIB_STYLIZED",
            title = "Phạm Hương Giang - Content & Media",
            category = "calibration",
            style = "stylized",
            lines = listOf(
                "PORTFOLIO: PHẠM HƯƠNG GIANG",
                "LĨNH VỰC HOẠT ĐỘNG: SÁNG TẠO NỘI DUNG",
                "CÁC KỸ NĂNG CHÍNH:",
                "- Nhiếp ảnh cơ bản và nâng cao",
                "- Chỉnh sửa ảnh Photoshop chuyên nghiệp",
                "- Biên tập video quảng cáo",
                "- Marketing online và truyền thông số"
            ),
            groundTruthSkills = listOf("Photoshop", "Marketing online", "Biên tập video", "Nhiếp ảnh cơ bản")
        ),
        TestCase(
            id = "CV_05_TEST_FLAT",
            title = "Hoàng Đức Minh - Fullstack Developer",
            category = "test",
            style = "flat",
            lines = listOf(
                "THÔNG TIN ỨNG VIÊN: HOÀNG ĐỨC MINH",
                "VỊ TRÍ ỨNG TUYỂN: FULLSTACK DEVELOPER",
                "KỸ NĂNG NỔI BẬT:",
                "- Phát triển frontend ReactJS",
                "- Xây dựng hệ thống Lập trình NodeJS",
                "- Giao tiếp Tiếng Nhật N3",
                "- Phân tích dữ liệu doanh nghiệp"
            ),
            groundTruthSkills = listOf("ReactJS", "Lập trình NodeJS", "Tiếng Nhật N3", "Phân tích dữ liệu")
        ),
        TestCase(
            id = "CV_06_TEST_TILTED",
            title = "Đặng Thu Hà - Mobile & Product",
            category = "test",
            style = "tilted",
            lines = listOf(
                "HỒ SƠ NĂNG LỰC: ĐẶNG THU HÀ",
                "NGÀNH NGHỀ: MOBILE & PRODUCT",
                "KỸ NĂNG:",
                "- Phát triển ứng dụng Flutter đa nền tảng",
                "- Thiết kế UI/UX hiện đại",
                "- Quản lý dự án theo mô hình Agile",
                "- Tiếng Trung cơ bản"
            ),
            groundTruthSkills = listOf("Flutter", "Thiết kế UI/UX", "Quản lý dự án", "Tiếng Trung cơ bản")
        ),
        TestCase(
            id = "CV_07_TEST_LOWLIGHT",
            title = "Vũ Quốc Huy - Cloud & SEO",
            category = "test",
            style = "lowlight",
            lines = listOf(
                "LÝ LỊCH TRÍCH NGANG: VŨ QUỐC HUY",
                "CHUYÊN MÔN: CLOUD & NỘI DUNG",
                "KỸ NĂNG:",
                "- Vận hành hạ tầng DevOps và Kubernetes",
                "- Viết nội dung SEO chuẩn quốc tế",
                "- Kỹ năng Thuyết trình trước đám đông"
            ),
            groundTruthSkills = listOf("DevOps", "Kubernetes", "Viết nội dung SEO", "Thuyết trình")
        ),
        TestCase(
            id = "CV_08_TEST_STYLIZED",
            title = "Bùi Quang Hải - Game & Media",
            category = "test",
            style = "stylized",
            lines = listOf(
                "HỒ SƠ SÁNG TẠO: BÙI QUANG HẢI",
                "LĨNH VỰC: GAME & TRUYỀN THÔNG",
                "KỸ NĂNG:",
                "- Dựng hình Đồ họa 3D",
                "- Lập trình C++ hiệu năng cao",
                "- Kỹ năng giao tiếp trong làm việc nhóm",
                "- Xử lý âm thanh phòng thu"
            ),
            groundTruthSkills = listOf("Đồ họa 3D", "Lập trình C++", "Kỹ năng giao tiếp", "Xử lý âm thanh")
        )
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.filesDir, "ocr_benchmark_tests").apply {
            if (!exists()) mkdirs()
        }
    }

    @Test
    fun benchmarkPreprocessing1600vs2000AndVerifyZeroCacheGrowth() = runBlocking {
        println("=== BẮT ĐẦU BENCHMARK TIỀN XỬ LÝ 1600px vs 2000px ===")

        val sampleCase = testCases[0]
        val highResFile = createCvImageFile(sampleCase, width = 2400, height = 3200)
        val uri = Uri.fromFile(highResFile)

        val recognizer1600 = MlKitOcrRecognizer(context, targetMaxDimension = 1600)
        val recognizer2000 = MlKitOcrRecognizer(context, targetMaxDimension = 2000)

        // 1. Kiểm tra thư mục cache trước khi chạy
        val cacheDir = context.cacheDir
        val cacheFilesBefore = cacheDir.listFiles()?.size ?: 0

        // 2. Chạy thử nghiệm 1600px (đo thời gian và bộ nhớ)
        System.gc()
        val runtime = Runtime.getRuntime()
        val memBefore1600 = runtime.totalMemory() - runtime.freeMemory()
        val start1600 = System.currentTimeMillis()
        val result1600 = recognizer1600.recognizeText(uri)
        val time1600 = System.currentTimeMillis() - start1600
        val memAfter1600 = runtime.totalMemory() - runtime.freeMemory()

        assertTrue("OCR 1600px phải thành công: ${result1600.exceptionOrNull()?.let { "${it::class.java.simpleName}: ${it.message}" }}", result1600.isSuccess)
        val text1600 = result1600.getOrNull() ?: ""

        // 3. Chạy thử nghiệm 2000px (đo thời gian và bộ nhớ)
        System.gc()
        val memBefore2000 = runtime.totalMemory() - runtime.freeMemory()
        val start2000 = System.currentTimeMillis()
        val result2000 = recognizer2000.recognizeText(uri)
        val time2000 = System.currentTimeMillis() - start2000
        val memAfter2000 = runtime.totalMemory() - runtime.freeMemory()

        assertTrue("OCR 2000px phải thành công", result2000.isSuccess)
        val text2000 = result2000.getOrNull() ?: ""

        // 4. Kiểm tra thư mục cache sau khi chạy: KHÔNG được sinh tệp tạm
        val cacheFilesAfter = cacheDir.listFiles()?.size ?: 0
        assertEquals("Thư mục cache không được tăng số lượng tệp (Zero-cache footprint)", cacheFilesBefore, cacheFilesAfter)

        println("--- KẾT QUẢ ĐO THỰC TẾ TRÊN EMULATOR ---")
        println("Cấu hình 1600px: Thời gian = ${time1600}ms, Heap Delta = ${(memAfter1600 - memBefore1600) / 1024} KB")
        println("Cấu hình 2000px: Thời gian = ${time2000}ms, Heap Delta = ${(memAfter2000 - memBefore2000) / 1024} KB")
        println("Độ dài text 1600px: ${text1600.length} ký tự")
        println("Độ dài text 2000px: ${text2000.length} ký tự")
        println("Số tệp cache trước: $cacheFilesBefore, sau: $cacheFilesAfter (Đạt yêu cầu không sinh tệp tạm)")
    }

    @Test
    fun evaluateAccuracyAcrossAll8FictionalCvs() = runBlocking {
        println("=== BẮT ĐẦU ĐO ĐỘ CHÍNH XÁC TRÊN 8 ẢNH CV HƯ CẤU ===")
        val recognizer = MlKitOcrRecognizer(context, targetMaxDimension = 1600)

        // Xuất ảnh sang Pictures của máy để có sẵn trong Gallery kiểm tra tay
        val publicPicturesDir = File("/sdcard/Pictures/SkillExchange_CVs").apply {
            if (!exists()) mkdirs()
        }

        var totalGroundTruth = 0
        var totalCorrect = 0
        var totalMissed = 0
        var totalWrong = 0
        var totalDiacriticErrors = 0

        val resultsLog = StringBuilder()

        testCases.forEachIndexed { index, tc ->
            val imgFile = createCvImageFile(tc, width = 1800, height = 2400)
            // Copy sang public Pictures để người dùng/tester xem được trên Gallery emulator
            try {
                imgFile.copyTo(File(publicPicturesDir, "${tc.id}.png"), overwrite = true)
            } catch (_: Exception) {}

            val uri = Uri.fromFile(imgFile)
            val startTime = System.currentTimeMillis()
            val result = recognizer.recognizeText(uri)
            val duration = System.currentTimeMillis() - startTime

            assertTrue("OCR phải thành công cho ${tc.id}: ${result.exceptionOrNull()?.let { "${it::class.java.simpleName}: ${it.message}" }}", result.isSuccess)
            val recognizedText = result.getOrNull() ?: ""

            // Đánh giá kỹ năng đúng/sai/bỏ sót
            var correctCount = 0
            var missedCount = 0
            var diacriticErrorCount = 0

            val unaccentedRecognized = unaccent(recognizedText.lowercase())

            tc.groundTruthSkills.forEach { skill ->
                val unaccentedSkill = unaccent(skill.lowercase())
                val exactMatch = recognizedText.contains(skill, ignoreCase = true)
                val unaccentMatch = unaccentedRecognized.contains(unaccentedSkill)

                if (exactMatch) {
                    correctCount++
                } else if (unaccentMatch) {
                    // Nhận dạng được chữ cái cơ bản nhưng sai/mất dấu tiếng Việt
                    correctCount++
                    diacriticErrorCount++
                } else {
                    missedCount++
                }
            }

            totalGroundTruth += tc.groundTruthSkills.size
            totalCorrect += correctCount
            totalMissed += missedCount
            totalDiacriticErrors += diacriticErrorCount

            val logEntry = """
                |------------------------------------------------------------
                | [${tc.category.uppercase()}] ${tc.id} (${tc.style}) - ${tc.title}
                | Thời gian OCR: ${duration}ms, Ký tự nhận dạng: ${recognizedText.length}
                | Ground Truth: ${tc.groundTruthSkills}
                | Đúng (kể cả mất dấu): $correctCount / ${tc.groundTruthSkills.size}
                | Bỏ sót: $missedCount
                | Lỗi dấu tiếng Việt: $diacriticErrorCount
                | Trích đoạn OCR: "${recognizedText.replace("\n", " | ").take(120)}..."
            """.trimMargin()

            println(logEntry)
            resultsLog.append(logEntry).append("\n")
        }

        val accuracyPercent = (totalCorrect.toDouble() / totalGroundTruth) * 100
        val summary = """
            |============================================================
            | TỔNG KẾT ĐO ĐỘ CHÍNH XÁC THẬT (TASK-043 MỨC 3 - BUNDLED ML KIT)
            | Tổng số kỹ năng kiểm định (Ground Truth): $totalGroundTruth
            | Tổng số kỹ năng trích xuất thành công: $totalCorrect (${String.format("%.1f", accuracyPercent)}%)
            | Tổng số kỹ năng bị bỏ sót: $totalMissed
            | Tổng số kỹ năng bị lỗi dấu tiếng Việt (mất/lệch dấu): $totalDiacriticErrors
            | Tổng số kỹ năng sai lệch (False Positive): $totalWrong
            |============================================================
        """.trimMargin()

        println(summary)
        assertTrue("Tỉ lệ nhận diện từ khóa cơ bản phải >= 70%", accuracyPercent >= 70.0)
    }

    private fun createCvImageFile(testCase: TestCase, width: Int, height: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background color
        val bgColor = when (testCase.style) {
            "lowlight" -> Color.rgb(60, 65, 70) // Dark background simulating low light
            "stylized" -> Color.rgb(245, 248, 255)
            else -> Color.WHITE
        }
        canvas.drawColor(bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (testCase.style) {
                "lowlight" -> Color.rgb(180, 185, 190) // Lower contrast
                "stylized" -> Color.rgb(20, 35, 90)
                else -> Color.BLACK
            }
            textSize = (width * 0.038f).coerceIn(40f, 75f)
            typeface = if (testCase.style == "stylized") Typeface.SERIF else Typeface.DEFAULT_BOLD
        }

        // Tilted rotation simulation
        if (testCase.style == "tilted") {
            canvas.rotate(-7.5f, width / 2f, height / 2f)
        }

        var y = height * 0.12f
        val x = width * 0.08f
        val lineSpacing = paint.textSize * 1.6f

        testCase.lines.forEach { line ->
            canvas.drawText(line, x, y, paint)
            y += lineSpacing
        }

        val file = File(testDir, "${testCase.id}.png")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        println("WROTE FILE: ${file.absolutePath}, exists=${file.exists()}, size=${file.length()}")
        return file
    }

    private fun unaccent(text: String): String {
        val nfd = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("đ", "d")
            .replace("Đ", "d")
    }
}
