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
}
