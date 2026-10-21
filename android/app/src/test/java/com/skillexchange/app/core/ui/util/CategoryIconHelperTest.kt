package com.skillexchange.app.core.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryIconHelperTest {

    @Test
    fun getCategoryIcon_MatchesBySlug() {
        assertEquals(Icons.Default.Code, CategoryIconHelper.getCategoryIcon("code"))
        assertEquals(Icons.Default.Language, CategoryIconHelper.getCategoryIcon("language"))
        assertEquals(Icons.Default.MusicNote, CategoryIconHelper.getCategoryIcon("music"))
        assertEquals(Icons.Default.Palette, CategoryIconHelper.getCategoryIcon("palette"))
        assertEquals(Icons.Default.Palette, CategoryIconHelper.getCategoryIcon("design"))
        assertEquals(Icons.Default.Restaurant, CategoryIconHelper.getCategoryIcon("cooking"))
        assertEquals(Icons.Default.FitnessCenter, CategoryIconHelper.getCategoryIcon("sports"))
        assertEquals(Icons.Default.FitnessCenter, CategoryIconHelper.getCategoryIcon("fitness"))
        assertEquals(Icons.Default.Work, CategoryIconHelper.getCategoryIcon("business"))
        assertEquals(Icons.Default.Work, CategoryIconHelper.getCategoryIcon("work"))
        assertEquals(Icons.Default.PhotoCamera, CategoryIconHelper.getCategoryIcon("camera"))
        assertEquals(Icons.Default.PhotoCamera, CategoryIconHelper.getCategoryIcon("photo"))
        assertEquals(Icons.Default.Edit, CategoryIconHelper.getCategoryIcon("writing"))
        assertEquals(Icons.Default.Edit, CategoryIconHelper.getCategoryIcon("edit"))
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon("other"))
    }

    @Test
    fun getCategoryIcon_MatchesByEmoji() {
        assertEquals(Icons.Default.Code, CategoryIconHelper.getCategoryIcon("💻"))
        assertEquals(Icons.Default.Language, CategoryIconHelper.getCategoryIcon("🌍"))
        assertEquals(Icons.Default.MusicNote, CategoryIconHelper.getCategoryIcon("🎵"))
        assertEquals(Icons.Default.Palette, CategoryIconHelper.getCategoryIcon("🎨"))
        assertEquals(Icons.Default.Restaurant, CategoryIconHelper.getCategoryIcon("🍳"))
        assertEquals(Icons.Default.FitnessCenter, CategoryIconHelper.getCategoryIcon("⚽"))
        assertEquals(Icons.Default.Work, CategoryIconHelper.getCategoryIcon("💼"))
        assertEquals(Icons.Default.PhotoCamera, CategoryIconHelper.getCategoryIcon("📷"))
        assertEquals(Icons.Default.Edit, CategoryIconHelper.getCategoryIcon("✍️"))
        assertEquals(Icons.Default.Edit, CategoryIconHelper.getCategoryIcon("✍"))
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon("🌟"))
    }

    @Test
    fun getCategoryIcon_MatchesByName_WhenRawIconEmptyOrUnknown() {
        assertEquals(Icons.Default.Code, CategoryIconHelper.getCategoryIcon(null, "Lập trình"))
        assertEquals(Icons.Default.Code, CategoryIconHelper.getCategoryIcon("", "lap trinh"))
        assertEquals(Icons.Default.Language, CategoryIconHelper.getCategoryIcon("", "Ngoại ngữ"))
        assertEquals(Icons.Default.Language, CategoryIconHelper.getCategoryIcon(null, "ngoai ngu"))
        assertEquals(Icons.Default.MusicNote, CategoryIconHelper.getCategoryIcon(null, "Âm nhạc"))
        assertEquals(Icons.Default.Palette, CategoryIconHelper.getCategoryIcon(null, "Thiết kế đồ họa"))
        assertEquals(Icons.Default.Restaurant, CategoryIconHelper.getCategoryIcon(null, "Nấu ăn & Ẩm thực"))
        assertEquals(Icons.Default.FitnessCenter, CategoryIconHelper.getCategoryIcon(null, "Thể thao"))
        assertEquals(Icons.Default.Work, CategoryIconHelper.getCategoryIcon(null, "Kinh doanh"))
        assertEquals(Icons.Default.PhotoCamera, CategoryIconHelper.getCategoryIcon(null, "Nhiếp ảnh"))
        assertEquals(Icons.Default.Edit, CategoryIconHelper.getCategoryIcon(null, "Viết lách"))
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon(null, "Khác"))
    }

    @Test
    fun getCategoryIcon_PriorityOrder_SlugOverEmojiOverName() {
        // If raw is slug "code" but name says "Âm nhạc", slug priority wins
        assertEquals(Icons.Default.Code, CategoryIconHelper.getCategoryIcon("code", "Âm nhạc"))
        // If raw is emoji "🎵" but name says "Lập trình", emoji priority wins over name
        assertEquals(Icons.Default.MusicNote, CategoryIconHelper.getCategoryIcon("🎵", "Lập trình"))
    }

    @Test
    fun getCategoryIcon_UnknownOrBlankOrNull_ReturnsDefaultCategoryWithoutCrashing() {
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon(null, null))
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon("", ""))
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon("   ", "   "))
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon("unknown_slug_xyz", "Tên danh mục lạ hoắc"))
        assertEquals(Icons.Default.Category, CategoryIconHelper.getCategoryIcon("???", "???"))
    }
}
