package com.skillexchange.api.services.nlp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrganizationContextGuardTest {

    private val extractor = SkillExtractionService()

    @Test
    fun `blocks skill inside company name`() {
        val text = "nhan vien tai cong ty co phan boi loi sao vang"
        val boiLoiIndex = text.indexOf("boi loi")

        assertTrue(extractor.isInsideOrganizationName(text, boiLoiIndex))
    }

    @Test
    fun `blocks skill inside club name with student suffix`() {
        val text = "thanh vien cua cau lac bo co vua sinh vien"
        val coVuaIndex = text.indexOf("co vua")

        assertTrue(extractor.isInsideOrganizationName(text, coVuaIndex))
    }

    @Test
    fun `blocks skill inside standalone club name without school name`() {
        val text = "tham gia hoat dong tai cau lac bo co vua"
        val coVuaIndex = text.indexOf("co vua")

        assertTrue(extractor.isInsideOrganizationName(text, coVuaIndex))
    }

    @Test
    fun `does not block skill when separated by action verb hoc vien day python`() {
        val text = "hoc vien x day python cho hoc vien moi"
        val pythonIndex = text.indexOf("python")

        // Từ "day" là động từ ngắt cụm danh xưng tổ chức -> không được chặn!
        assertFalse(extractor.isInsideOrganizationName(text, pythonIndex))
    }

    @Test
    fun `does not block independent skill after school name`() {
        val text = "tot nghiep dai hoc ngoai ngu, thanh thao tieng nhat va tieng anh"
        val tiengNhatIndex = text.indexOf("tieng nhat")

        // Cách xa quá 3-4 từ hoặc có dấu phẩy ngắt -> không được chặn
        assertFalse(extractor.isInsideOrganizationName(text, tiengNhatIndex))
    }
}
