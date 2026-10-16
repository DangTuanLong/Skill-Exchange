package com.skillexchange.api.services.nlp

import kotlin.test.Test
import kotlin.test.assertTrue

data class BenchmarkSample(
    val id: Int,
    val bio: String,
    val expectedSkills: Set<String>,
    val isHeldOut: Boolean,
    val description: String
)

class SkillExtractionBenchmarkTest {

    private val extractor = SkillExtractionService()

    /**
     * Tập dữ liệu 30 câu bio thực tế gán nhãn Ground Truth TRƯỚC KHI tinh chỉnh.
     * Tách thành:
     * - 12 câu: Tập phát triển / tinh chỉnh (Development/Tuning set)
     * - 18 câu: Tập kiểm tra giữ kín (Held-out Test set)
     */
    private val benchmarkDataset = listOf(
        // ─── TẬP TINH CHỈNH (12 CÂU) ──────────────────────────────────────
        BenchmarkSample(
            id = 1,
            bio = "Mình là lập trình viên Android, có 3 năm kinh nghiệm với Kotlin.",
            expectedSkills = setOf("Android (Kotlin)"),
            isHeldOut = false,
            description = "Chuẩn: Android & Kotlin"
        ),
        BenchmarkSample(
            id = 2,
            bio = "Đam mê thiết kế giao diện UI UX và Figma cho các ứng dụng di động.",
            expectedSkills = setOf("Figma / UI Design"),
            isHeldOut = false,
            description = "Alias: UI UX & Figma"
        ),
        BenchmarkSample(
            id = 3,
            bio = "Biết chơi guitar cơ bản và đệm hát.",
            expectedSkills = setOf("Guitar", "Hát"),
            isHeldOut = false,
            description = "Chuẩn đa kỹ năng: Guitar + Hát"
        ),
        BenchmarkSample(
            id = 4,
            bio = "Có chứng chỉ tiếng Anh và tiếng Nhật N3.",
            expectedSkills = setOf("Tiếng Anh", "Tiếng Nhật"),
            isHeldOut = false,
            description = "Chuẩn ngoại ngữ: Tiếng Anh + Tiếng Nhật"
        ),
        BenchmarkSample(
            id = 5,
            bio = "Làm việc với cơ sở dữ liệu SQL và backend Python.",
            expectedSkills = setOf("SQL", "Python"),
            isHeldOut = false,
            description = "Chuẩn công nghệ: SQL + Python"
        ),
        BenchmarkSample(
            id = 6,
            bio = "Chuyên chụp ảnh chân dung và chỉnh sửa bằng photoshop.",
            expectedSkills = setOf("Adobe Photoshop"),
            isHeldOut = false,
            description = "Alias: photoshop -> Adobe Photoshop"
        ),
        BenchmarkSample(
            id = 7,
            bio = "Thích đá bóng và tập gym mỗi buổi chiều.",
            expectedSkills = setOf("Bóng đá", "Gym / Fitness"),
            isHeldOut = false,
            description = "Thể thao: đá bóng (bóng đá) + gym"
        ),
        BenchmarkSample(
            id = 8,
            bio = "Biết bơi lội và thích chơi cầu lông cuối tuần.",
            expectedSkills = setOf("Bơi lội", "Cầu lông"),
            isHeldOut = false,
            description = "Thể thao: Bơi lội + Cầu lông"
        ),
        BenchmarkSample(
            id = 9,
            bio = "Lập trình web bằng reactjs và nodejs.",
            expectedSkills = setOf("Web (React)", "Node.js"),
            isHeldOut = false,
            description = "Alias: reactjs + nodejs"
        ),
        BenchmarkSample(
            id = 10,
            bio = "Chơi đàn piano và biết chút về beat making.",
            expectedSkills = setOf("Piano", "Beat making"),
            isHeldOut = false,
            description = "Âm nhạc: Piano + Beat making"
        ),
        BenchmarkSample(
            id = 11,
            bio = "Chuyên viên marketing digital và quản lý dự án công nghệ.",
            expectedSkills = setOf("Marketing Digital", "Quản lý dự án"),
            isHeldOut = false,
            description = "Kinh doanh: Marketing Digital + Quản lý dự án"
        ),
        BenchmarkSample(
            id = 12,
            bio = "Lập trình ứng dụng iOS bằng Swift.",
            expectedSkills = setOf("iOS (Swift)"),
            isHeldOut = false,
            description = "Công nghệ: iOS (Swift)"
        ),

        // ─── TẬP KIỂM TRA GIỮ KÍN (18 CÂU) ──────────────────────────────
        BenchmarkSample(
            id = 13,
            bio = "minh biet lap trinh python va co the danh dan guitar",
            expectedSkills = setOf("Python", "Guitar"),
            isHeldOut = true,
            description = "Không dấu: python + dan guitar"
        ),
        BenchmarkSample(
            id = 14,
            bio = "chuyen thiet ke banner bang ps va edit video",
            expectedSkills = setOf("Adobe Photoshop", "Video Editing"),
            isHeldOut = true,
            description = "Viết tắt alias: ps + edit video"
        ),
        BenchmarkSample(
            id = 15,
            bio = "hoc pythn co ban va muon tim ban trao doi",
            expectedSkills = setOf("Python"),
            isHeldOut = true,
            description = "Fuzzy sai 1 ký tự: pythn ~ python"
        ),
        BenchmarkSample(
            id = 16,
            bio = "biet dung figam de thiet ke mockup",
            expectedSkills = setOf("Figma / UI Design"),
            isHeldOut = true,
            description = "Fuzzy sai 1 ký tự: figam ~ figma"
        ),
        BenchmarkSample(
            id = 17,
            bio = "Tôi thích đi du lịch và uống cà phê mỗi sáng chủ nhật.",
            expectedSkills = emptySet(),
            isHeldOut = true,
            description = "Nhiễu âm tính: không chứa kỹ năng nào"
        ),
        BenchmarkSample(
            id = 18,
            bio = "Thích uống cà phê sữa đá, xem phim truyền hình.",
            expectedSkills = emptySet(),
            isHeldOut = true,
            description = "Nhiễu âm tính: từ vựng đời sống"
        ),
        BenchmarkSample(
            id = 19,
            bio = "Lập trình C++ cho các hệ thống phần mềm hiệu năng cao.",
            expectedSkills = emptySet(), // Lưu ý: C++ chưa có trong default 39 skills của hệ thống nên ground truth là rỗng (không bắt nhầm sang C)
            isHeldOut = true,
            description = "Bẫy ranh giới: C++ (không bắt C)"
        ),
        BenchmarkSample(
            id = 20,
            bio = "Thành thạo lập trình Java cho hệ thống backend lớn.",
            expectedSkills = setOf("Java"),
            isHeldOut = true,
            description = "Ranh giới: Java độc lập"
        ),
        BenchmarkSample(
            id = 21,
            bio = "Vừa biết vẽ illustrator vừa thích tập yoga để thư giãn.",
            expectedSkills = setOf("Illustrator", "Yoga"),
            isHeldOut = true,
            description = "Đa ngành: Illustrator + Yoga"
        ),
        BenchmarkSample(
            id = 22,
            bio = "Nói được tiếng Hàn lưu loát và đang tự học tiếng Trung.",
            expectedSkills = setOf("Tiếng Hàn", "Tiếng Trung"),
            isHeldOut = true,
            description = "Ngoại ngữ: Tiếng Hàn + Tiếng Trung"
        ),
        BenchmarkSample(
            id = 23,
            bio = "thich choi guitr va ca hat acoustic",
            expectedSkills = setOf("Guitar", "Hát"),
            isHeldOut = true,
            description = "Fuzzy: guitr ~ guitar + ca hat (Hát)"
        ),
        BenchmarkSample(
            id = 24,
            bio = "Đạt giải nhất cờ vua cấp trường và thích chơi tennis.",
            expectedSkills = setOf("Cờ vua", "Tennis"),
            isHeldOut = true,
            description = "Thể thao: Cờ vua + Tennis"
        ),
        BenchmarkSample(
            id = 25,
            bio = "Nghiên cứu về machine learning và thị giác máy tính với python.",
            expectedSkills = setOf("AI/ML", "Python"),
            isHeldOut = true,
            description = "AI/ML (alias machine learning) + Python"
        ),
        BenchmarkSample(
            id = 26,
            bio = "Tôi không thích học tiếng Anh nhưng vẫn cố gắng.",
            expectedSkills = setOf("Tiếng Anh"),
            isHeldOut = true,
            description = "Câu phủ định có chứa tên kỹ năng: Tiếng Anh"
        ),
        BenchmarkSample(
            id = 27,
            bio = "Giao tiếp cơ bản bằng tiếng Pháp và tiếng Đức.",
            expectedSkills = setOf("Tiếng Pháp", "Tiếng Đức"),
            isHeldOut = true,
            description = "Ngoại ngữ: Tiếng Pháp + Tiếng Đức"
        ),
        BenchmarkSample(
            id = 28,
            bio = "Phát triển app đa nền tảng bằng fluter.",
            expectedSkills = setOf("Flutter"),
            isHeldOut = true,
            description = "Fuzzy sai 1 ký tự: fluter ~ flutter"
        ),
        BenchmarkSample(
            id = 29,
            bio = "Đi làm tại cơ quan nhà nước, sáng đi tối về.",
            expectedSkills = emptySet(),
            isHeldOut = true,
            description = "Bẫy từ con: 'cơ quan' không phải 'cơ' hay 'C'"
        ),
        BenchmarkSample(
            id = 30,
            bio = "Thích ngắm bóng bay trên bầu trời hoàng hôn.",
            expectedSkills = emptySet(),
            isHeldOut = true,
            description = "Bẫy từ con: 'bóng bay' không phải 'bóng đá'"
        )
    )

    @Test
    fun `run benchmark on 30 bio dataset and print honest evaluation metrics`() {
        var totalTP = 0
        var totalFP = 0
        var totalFN = 0

        var heldOutTP = 0
        var heldOutFP = 0
        var heldOutFN = 0

        val misclassified = mutableListOf<String>()

        println("\n" + "=".repeat(80))
        println("  SKILLEXCHANGE - KOTLIN IN-PROCESS NLP SKILL EXTRACTION BENCHMARK")
        println("=".repeat(80))

        for (sample in benchmarkDataset) {
            val extracted = extractor.extractSkills(sample.bio)
            val predicted = extracted.map { it.skillName }.toSet()
            val expected = sample.expectedSkills

            val tp = predicted.intersect(expected).size
            val fp = predicted.minus(expected).size
            val fn = expected.minus(predicted).size

            totalTP += tp
            totalFP += fp
            totalFN += fn

            if (sample.isHeldOut) {
                heldOutTP += tp
                heldOutFP += fp
                heldOutFN += fn
            }

            if (fp > 0 || fn > 0) {
                val sb = StringBuilder()
                sb.appendLine("  [ID: ${sample.id}] [${if (sample.isHeldOut) "HELD-OUT" else "TUNING"}] ${sample.description}")
                sb.appendLine("    Bio: \"${sample.bio}\"")
                sb.appendLine("    Ground Truth : $expected")
                sb.appendLine("    Predicted    : $predicted (${extracted.map { "${it.skillName}:${it.confidence}:${it.matchType}" }})")
                if (fp > 0) sb.appendLine("    False Positive (thừa): ${predicted.minus(expected)}")
                if (fn > 0) sb.appendLine("    False Negative (thiếu): ${expected.minus(predicted)}")
                misclassified.add(sb.toString())
            }
        }

        val precision = if (totalTP + totalFP > 0) totalTP.toDouble() / (totalTP + totalFP) else 0.0
        val recall = if (totalTP + totalFN > 0) totalTP.toDouble() / (totalTP + totalFN) else 0.0
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0

        val heldOutPrecision = if (heldOutTP + heldOutFP > 0) heldOutTP.toDouble() / (heldOutTP + heldOutFP) else 0.0
        val heldOutRecall = if (heldOutTP + heldOutFN > 0) heldOutTP.toDouble() / (heldOutTP + heldOutFN) else 0.0
        val heldOutF1 = if (heldOutPrecision + heldOutRecall > 0) 2 * heldOutPrecision * heldOutRecall / (heldOutPrecision + heldOutRecall) else 0.0

        println("\n>>> TỔNG HỢP TOÀN BỘ (30 CÂU):")
        println("    - True Positives  (TP) : $totalTP")
        println("    - False Positives (FP) : $totalFP")
        println("    - False Negatives (FN) : $totalFN")
        println("    - Precision            : ${"%.2f".format(precision * 100)}%")
        println("    - Recall               : ${"%.2f".format(recall * 100)}%")
        println("    - F1-Score             : ${"%.2f".format(f1 * 100)}%")

        println("\n>>> TẬP KIỂM TRA GIỮ KÍN - HELD-OUT (18 CÂU):")
        println("    - True Positives  (TP) : $heldOutTP")
        println("    - False Positives (FP) : $heldOutFP")
        println("    - False Negatives (FN) : $heldOutFN")
        println("    - Precision            : ${"%.2f".format(heldOutPrecision * 100)}%")
        println("    - Recall               : ${"%.2f".format(heldOutRecall * 100)}%")
        println("    - F1-Score             : ${"%.2f".format(heldOutF1 * 100)}%")

        println("\n>>> CÁC CÂU DỰ ĐOÁN CHƯA TRÙNG KHỚP (ĐỂ CHỦ SỞ HỮU RÀ SOÁT):")
        if (misclassified.isEmpty()) {
            println("    (Không có câu nào dự đoán sai! 100% khớp ground truth)")
        } else {
            misclassified.forEach { println(it) }
        }
        println("=".repeat(80) + "\n")

        // Đảm bảo F1-score đạt ngưỡng yêu cầu (ít nhất >= 85%)
        assertTrue(f1 >= 0.85, "F1-Score tổng thể phải đạt ít nhất 85% (thực tế: ${"%.2f".format(f1 * 100)}%)")
    }
}
