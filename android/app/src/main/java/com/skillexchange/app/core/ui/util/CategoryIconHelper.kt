package com.skillexchange.app.core.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.Normalizer
import java.util.Locale

/**
 * Tiện ích chuẩn hóa và ánh xạ biểu tượng danh mục kỹ năng (TASK-062, DEC-022, UI_SPEC.md §1).
 *
 * Thứ tự ưu tiên giải quyết:
 * 1. Slug tiếng Anh (code, language, music, palette, restaurant, fitness, work, camera, edit, other)
 * 2. Ký tự Emoji trong cột icon DB hiện có (💻, 🌍, 🎵, 🎨, 🍳, ⚽, 💼, 📷, ✍️, 🌟)
 * 3. Tên danh mục tiếng Việt (có dấu hoặc không dấu: Lập trình, Ngoại ngữ, Âm nhạc...)
 * 4. Biểu tượng mặc định (Icons.Default.Category) khi chuỗi rỗng/null hoặc không nhận ra.
 *
 * Không bao giờ gây crash, hoàn toàn an toàn khi gặp dữ liệu bất thường.
 */
object CategoryIconHelper {

    fun getCategoryIcon(rawIconOrSlug: String?, categoryName: String? = null): ImageVector {
        val raw = rawIconOrSlug?.trim()?.lowercase(Locale.ROOT) ?: ""

        // ── 1. Kiểm tra theo Slug tiếng Anh ──────────────────────────────
        if (raw.isNotEmpty() && !containsEmoji(raw)) {
            when {
                raw in listOf("code", "programming", "dev", "developer") -> return Icons.Default.Code
                raw in listOf("language", "foreign_language", "languages", "translate") -> return Icons.Default.Language
                raw in listOf("music", "audio", "song") -> return Icons.Default.MusicNote
                raw in listOf("palette", "design", "art", "ui", "ux") -> return Icons.Default.Palette
                raw in listOf("restaurant", "cooking", "food", "culinary") -> return Icons.Default.Restaurant
                raw in listOf("fitness", "sports", "gym", "sport", "workout") -> return Icons.Default.FitnessCenter
                raw in listOf("work", "business", "finance", "sales", "office") -> return Icons.Default.Work
                raw in listOf("camera", "photo", "photography") -> return Icons.Default.PhotoCamera
                raw in listOf("edit", "writing", "content", "write") -> return Icons.Default.Edit
                raw in listOf("category", "other", "misc") -> return Icons.Default.Category
            }
        }

        // ── 2. Kiểm tra theo Ký tự Emoji trong dữ liệu seed / DB ──────────
        if (raw.isNotEmpty()) {
            when {
                raw.contains("💻") -> return Icons.Default.Code
                raw.contains("🌍") || raw.contains("🌐") -> return Icons.Default.Language
                raw.contains("🎵") || raw.contains("🎶") -> return Icons.Default.MusicNote
                raw.contains("🎨") || raw.contains("🖌") -> return Icons.Default.Palette
                raw.contains("🍳") || raw.contains("🍲") || raw.contains("🍜") -> return Icons.Default.Restaurant
                raw.contains("⚽") || raw.contains("🏀") || raw.contains("🏃") -> return Icons.Default.FitnessCenter
                raw.contains("💼") || raw.contains("📊") -> return Icons.Default.Work
                raw.contains("📷") || raw.contains("📸") -> return Icons.Default.PhotoCamera
                raw.contains("✍") || raw.contains("📝") -> return Icons.Default.Edit
                raw.contains("🌟") || raw.contains("⭐") -> return Icons.Default.Category
            }
        }

        // ── 3. Kiểm tra theo Tên danh mục (tiếng Việt có/không dấu) ────────
        val normalizedName = removeAccents(categoryName?.trim()?.lowercase(Locale.ROOT) ?: "")
        if (normalizedName.isNotEmpty()) {
            when {
                normalizedName.contains("lap trinh") || normalizedName.contains("code") -> return Icons.Default.Code
                normalizedName.contains("ngoai ngu") || normalizedName.contains("tieng") -> return Icons.Default.Language
                normalizedName.contains("am nhac") || normalizedName.contains("nhac") -> return Icons.Default.MusicNote
                normalizedName.contains("thiet ke") || normalizedName.contains("do hoa") -> return Icons.Default.Palette
                normalizedName.contains("nau an") || normalizedName.contains("am thuc") -> return Icons.Default.Restaurant
                normalizedName.contains("the thao") || normalizedName.contains("fitness") -> return Icons.Default.FitnessCenter
                normalizedName.contains("kinh doanh") || normalizedName.contains("marketing") -> return Icons.Default.Work
                normalizedName.contains("nhiep anh") || normalizedName.contains("chup anh") -> return Icons.Default.PhotoCamera
                normalizedName.contains("viet lach") || normalizedName.contains("viet content") -> return Icons.Default.Edit
                normalizedName.contains("khac") -> return Icons.Default.Category
            }
        }

        // ── 4. Fallback mặc định ──────────────────────────────────────────
        return Icons.Default.Category
    }

    private fun removeAccents(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("đ", "d")
            .replace("Đ", "d")
    }

    private fun containsEmoji(text: String): Boolean {
        for (i in 0 until text.length) {
            val type = Character.getType(text.codePointAt(i))
            if (type == Character.SURROGATE.toInt() || type == Character.OTHER_SYMBOL.toInt()) {
                return true
            }
        }
        return false
    }
}
