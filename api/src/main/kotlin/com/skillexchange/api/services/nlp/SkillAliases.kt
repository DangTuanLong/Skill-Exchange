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
        "Android (Kotlin)" to listOf("android", "kotlin", "android studio", "jetpack compose", "lap trinh android"),
        "iOS (Swift)" to listOf("ios", "swift", "swiftui", "xcode", "lap trinh ios"),
        "Web (React)" to listOf("react", "reactjs", "react.js", "react native", "nextjs", "next.js"),
        "Web (Vue)" to listOf("vue", "vuejs", "vue.js", "nuxtjs", "nuxt.js"),
        "Python" to listOf("python", "py"),
        "Java" to listOf("java"),
        "JavaScript" to listOf("javascript", "js"),
        "C++" to listOf("c++", "cpp"),
        "C#" to listOf("c#", "csharp"),
        "C" to listOf("c"),
        "Flutter" to listOf("flutter"),
        "Node.js" to listOf("nodejs", "node.js", "node js", "expressjs", "express.js", "nestjs"),
        "SQL" to listOf("sql", "postgresql", "postgres", "mysql", "sqlite", "sql server", "ms sql", "mariadb"),
        "AI/ML" to listOf("ai/ml", "machine learning", "ml", "tri tue nhan tao", "deep learning", "hoc may", "hoc sau", "ai ml"),

        // Thiết kế
        "Figma / UI Design" to listOf("figma", "ui ux", "ui/ux", "ui design", "ux design", "ux/ui", "ui/ux design"),
        "Adobe Photoshop" to listOf("photoshop", "ps", "pts", "adobe ps"),
        "Illustrator" to listOf("illustrator", "adobe illustrator", "adobe ai"),
        "Video Editing" to listOf("video editing", "edit video", "dung phim", "chinh sua video", "premiere", "premiere pro", "capcut", "davinci resolve", "final cut", "quay dung phim", "dung video"),
        "3D Modeling" to listOf("3d modeling", "blender", "maya", "3ds max", "cinema 4d", "c4d", "zbrush"),
        "Motion Graphics" to listOf("motion graphics", "after effects", "ae", "adobe after effects"),

        // Ngoại ngữ
        "Tiếng Anh" to listOf("english", "tieng anh", "tienganh", "anh van"),
        "Tiếng Nhật" to listOf("japanese", "tieng nhat", "tiengnhat", "nhat ngu"),
        "Tiếng Hàn" to listOf("korean", "tieng han", "tienghan", "han ngu"),
        "Tiếng Trung" to listOf("chinese", "tieng trung", "tieng hoa", "tiengtrung", "hoa ngu", "trung van", "mandarin"),
        "Tiếng Pháp" to listOf("french", "tieng phap", "tiengphap", "phap van"),
        "Tiếng Đức" to listOf("german", "tieng duc", "tiengduc"),
        "Tiếng Tây Ban Nha" to listOf("spanish", "tieng tay ban nha", "tiengtaybannha"),

        // Âm nhạc
        "Guitar" to listOf("guitar", "dan guitar", "danguitar"),
        "Piano" to listOf("piano", "dan piano", "danpiano"),
        "Ukulele" to listOf("ukulele"),
        "Violin" to listOf("violin"),
        "Trống" to listOf("drums", "dan trong", "bo trong", "trong jazz", "danh trong"),
        "Hát" to listOf("vocal", "hat", "ca hat"),
        "Sáng tác nhạc" to listOf("sang tac nhac", "songwriting"),
        "Beat making" to listOf("beat making", "lam beat"),

        // Thể thao
        "Gym / Fitness" to listOf("gym", "fitness", "tap gym"),
        "Bóng đá" to listOf("football", "soccer", "bong da", "da bong", "bongda", "dabong"),
        "Cầu lông" to listOf("badminton", "cau long", "caulong"),
        "Bơi lội" to listOf("swimming", "boi loi", "boiloi"),
        "Cờ vua" to listOf("chess", "co vua", "covua"),
        "Tennis" to listOf("tennis", "quan vot"),
        "Yoga" to listOf("yoga"),

        // Kinh doanh & Khác
        "Marketing Digital" to listOf("digital marketing", "marketing", "marketing online", "online marketing", "digital mkt"),
        "Excel nâng cao" to listOf("excel", "microsoft excel", "ms excel"),
        "Quản lý dự án" to listOf("project management", "quan ly du an", "quan tri du an", "scrum master"),
        "Kế toán" to listOf("ke toan", "accounting"),
        "Copywriting" to listOf("copywriting"),
        "Dịch thuật" to listOf("dich thuat", "translation")
    )
}
