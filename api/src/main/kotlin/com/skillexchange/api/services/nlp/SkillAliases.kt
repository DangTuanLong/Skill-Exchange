package com.skillexchange.api.services.nlp

/**
 * Danh mục bí danh và từ đồng nghĩa thật (Canonical True Synonyms).
 * Tuyệt đối không chứa alias suy diễn (đã loại bỏ frontend -> React, ielts/toeic -> Tiếng Anh).
 */
object SkillAliases {

    /**
     * Map từ Canonical Skill Name (hoặc pattern nhận diện tên chuẩn) sang danh sách bí danh thật.
     * Mọi alias ở đây đều mang nghĩa tương đương 1-1 về mặt kỹ thuật/ngôn ngữ.
     */
    val CANONICAL_TO_ALIASES: Map<String, List<String>> = mapOf(
        // Lập trình
        "Android (Kotlin)" to listOf("android", "kotlin"),
        "iOS (Swift)" to listOf("ios", "swift"),
        "Web (React)" to listOf("react", "reactjs", "react.js"),
        "Web (Vue)" to listOf("vue", "vuejs", "vue.js"),
        "Python" to listOf("python", "py"),
        "Java" to listOf("java"),
        "JavaScript" to listOf("javascript", "js"),
        "C++" to listOf("c++", "cpp"),
        "C#" to listOf("c#", "csharp"),
        "C" to listOf("c"),
        "Flutter" to listOf("flutter"),
        "Node.js" to listOf("nodejs", "node.js", "node js"),
        "SQL" to listOf("sql"),
        "AI/ML" to listOf("ai/ml", "machine learning", "ml", "tri tue nhan tao"),

        // Thiết kế
        "Figma / UI Design" to listOf("figma", "ui ux", "ui/ux", "ui design", "ux design"),
        "Adobe Photoshop" to listOf("photoshop", "ps", "pts"),
        "Illustrator" to listOf("illustrator", "adobe illustrator"),
        "Video Editing" to listOf("video editing", "edit video", "dung phim", "chinh sua video"),
        "3D Modeling" to listOf("3d modeling", "blender", "maya"),
        "Motion Graphics" to listOf("motion graphics", "after effects", "ae"),

        // Ngoại ngữ
        "Tiếng Anh" to listOf("english", "tieng anh"),
        "Tiếng Nhật" to listOf("japanese", "tieng nhat"),
        "Tiếng Hàn" to listOf("korean", "tieng han"),
        "Tiếng Trung" to listOf("chinese", "tieng trung", "tieng hoa"),
        "Tiếng Pháp" to listOf("french", "tieng phap"),
        "Tiếng Đức" to listOf("german", "tieng duc"),
        "Tiếng Tây Ban Nha" to listOf("spanish", "tieng tay ban nha"),

        // Âm nhạc
        "Guitar" to listOf("guitar", "dan guitar"),
        "Piano" to listOf("piano", "dan piano"),
        "Ukulele" to listOf("ukulele"),
        "Violin" to listOf("violin"),
        "Trống" to listOf("drums", "trong"),
        "Hát" to listOf("vocal", "hat", "ca hat"),
        "Sáng tác nhạc" to listOf("sang tac nhac", "songwriting"),
        "Beat making" to listOf("beat making", "lam beat"),

        // Thể thao
        "Gym / Fitness" to listOf("gym", "fitness"),
        "Bóng đá" to listOf("football", "soccer", "bong da", "da bong"),
        "Cầu lông" to listOf("badminton", "cau long"),
        "Bơi lội" to listOf("swimming", "boi loi"),
        "Cờ vua" to listOf("chess", "co vua"),
        "Tennis" to listOf("tennis", "quan vot"),
        "Yoga" to listOf("yoga"),

        // Kinh doanh & Khác
        "Marketing Digital" to listOf("digital marketing", "marketing"),
        "Excel nâng cao" to listOf("excel"),
        "Quản lý dự án" to listOf("project management", "quan ly du an"),
        "Kế toán" to listOf("ke toan", "accounting"),
        "Copywriting" to listOf("copywriting"),
        "Dịch thuật" to listOf("dich thuat", "translation")
    )
}
