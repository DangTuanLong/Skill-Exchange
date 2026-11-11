package com.skillexchange.api.services.nlp

import kotlin.test.Test
import kotlin.test.assertEquals

class NegationDetectionTest {

    private val extractor = SkillExtractionService()

    @Test
    fun `detects Vietnamese negation correctly`() {
        val text = "toi chua tung lam viec voi flutter va khong co kinh nghiem java"
        val flutterIndex = text.indexOf("flutter")
        val javaIndex = text.indexOf("java")

        assertEquals(SkillExtractionService.MatchIntent.NEGATIVE, extractor.determineIntentAt(text, flutterIndex))
        assertEquals(SkillExtractionService.MatchIntent.NEGATIVE, extractor.determineIntentAt(text, javaIndex))
    }

    @Test
    fun `detects English negation correctly`() {
        val text = "not familiar with swift and have no experience with flutter"
        val swiftIndex = text.indexOf("swift")
        val flutterIndex = text.indexOf("flutter")

        assertEquals(SkillExtractionService.MatchIntent.NEGATIVE, extractor.determineIntentAt(text, swiftIndex))
        assertEquals(SkillExtractionService.MatchIntent.NEGATIVE, extractor.determineIntentAt(text, flutterIndex))
    }

    @Test
    fun `stops at clause punctuation delimiter`() {
        val text = "toi khong thich hoc van, rat dam me python"
        val pythonIndex = text.indexOf("python")

        // Sau dấu phẩy là mệnh đề mới khẳng định -> phải là HAVE
        assertEquals(SkillExtractionService.MatchIntent.HAVE, extractor.determineIntentAt(text, pythonIndex))
    }

    @Test
    fun `handles affirmative exception khong nhung ma con`() {
        val text = "toi khong nhung biet java ma con gioi python"
        val pythonIndex = text.indexOf("python")

        assertEquals(SkillExtractionService.MatchIntent.HAVE, extractor.determineIntentAt(text, pythonIndex))
    }

    @Test
    fun `does not trigger false negation on non-negation compound words`() {
        val text = "chuyen gia ve an ninh khong gian mang va phat trien python"
        val pythonIndex = text.indexOf("python")

        assertEquals(SkillExtractionService.MatchIntent.HAVE, extractor.determineIntentAt(text, pythonIndex))

        val text2 = "viet van ban tieng viet khong dau va lap trinh python"
        val pythonIndex2 = text2.indexOf("python")
        assertEquals(SkillExtractionService.MatchIntent.HAVE, extractor.determineIntentAt(text2, pythonIndex2))
    }

    @Test
    fun `detects want intent correctly`() {
        val text = "ke hoach nam toi: muon hoc them guitar va dang tim nguoi day boi loi"
        val guitarIndex = text.indexOf("guitar")
        val boiLoiIndex = text.indexOf("boi loi")

        assertEquals(SkillExtractionService.MatchIntent.WANT, extractor.determineIntentAt(text, guitarIndex))
        assertEquals(SkillExtractionService.MatchIntent.WANT, extractor.determineIntentAt(text, boiLoiIndex))
    }

    @Test
    fun `fuzzy match uses exact token offset instead of first substring indexOf`() {
        // Từ đầu tiên "kotlntest" nằm trong mệnh đề phủ định và chứa chuỗi con "kotln";
        // mệnh đề thứ hai sau dấu chấm phẩy khẳng định "thanh thao kotln" (fuzzy ~ kotlin -> Android (Kotlin)).
        val text = "Chưa biết kotlntest; hiện tại đã thành thạo kotln 3 năm."
        val extracted = extractor.extractSkills(text)
        val kotlinSkill = extracted.find { it.skillName == "Android (Kotlin)" }
        kotlin.test.assertNotNull(kotlinSkill)
        assertEquals("FUZZY", kotlinSkill.matchType)
    }

    @Test
    fun `extracts skills accurately from noisy OCR text with character confusions`() {
        val noisyOcrText = """
            MỤC TIÊU NGHỀ NGHIỆP:
            Lập trình viên có tinh thần nỗ lực không ngừng, kèm theo kỹ năng làm việc nhóm tốt.
            CÔNG NGHỆ SỬ DỤNG:
            Kot1in | Pyth0n | SQ1 | Node .js | Al/ML | Ul/UX
            Ngoại ngữ & Thể thao:
            - Tlếng Anh, Ti6ng Nhật
            - Chơi Cầu 1ông và Bóng oa cuối tuần
        """.trimIndent()

        val extracted = extractor.extractSkills(noisyOcrText).map { it.skillName }.toSet()
        kotlin.test.assertTrue(extracted.contains("Android (Kotlin)"), "Phải bắt được Kotlin từ Kot1in")
        kotlin.test.assertTrue(extracted.contains("Python"), "Phải bắt được Python từ Pyth0n")
        kotlin.test.assertTrue(extracted.contains("SQL"), "Phải bắt được SQL từ SQ1")
        kotlin.test.assertTrue(extracted.contains("Node.js"), "Phải bắt được Node.js từ Node .js")
        kotlin.test.assertTrue(extracted.contains("AI/ML"), "Phải bắt được AI/ML từ Al/ML")
        kotlin.test.assertTrue(extracted.contains("Figma / UI Design"), "Phải bắt được Figma / UI Design từ Ul/UX")
        kotlin.test.assertTrue(extracted.contains("Tiếng Anh"), "Phải bắt được Tiếng Anh từ Tlếng Anh")
        kotlin.test.assertTrue(extracted.contains("Tiếng Nhật"), "Phải bắt được Tiếng Nhật từ Ti6ng Nhật")
        kotlin.test.assertTrue(extracted.contains("Cầu lông"), "Phải bắt được Cầu lông từ Cầu 1ông")
        kotlin.test.assertTrue(extracted.contains("Bóng đá"), "Phải bắt được Bóng đá từ Bóng oa")
    }

    @Test
    fun `two-word bigram fuzzy match rescues 1-char typo in multi-word skills`() {
        val text = "Thành thạo tieg anh giao tiếp, dựng video edting và triển khai marketing digtal."
        val extracted = extractor.extractSkills(text)
        val names = extracted.map { it.skillName }.toSet()

        kotlin.test.assertTrue(names.contains("Tiếng Anh"))
        kotlin.test.assertTrue(names.contains("Video Editing"))
        kotlin.test.assertTrue(names.contains("Marketing Digital"))
    }
}
