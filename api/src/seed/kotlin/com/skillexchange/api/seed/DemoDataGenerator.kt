package com.skillexchange.api.seed

import com.skillexchange.api.models.profile.AvailabilityWindowDto
import java.util.Random

object DemoDataGenerator {

    const val DEMO_DOMAIN = "@seed.skillexchange.test"
    const val DEMO_PASSWORD = "Demo@123456"
    private const val RANDOM_SEED = 1337L

    val AVAILABLE_SKILLS = listOf(
        "Kotlin Android",
        "Python",
        "React / Next.js",
        "Machine Learning",
        "SQL & Database",
        "UI/UX Design",
        "Tiếng Anh giao tiếp",
        "Tiếng Nhật",
        "Tiếng Hàn",
        "Tiếng Trung",
        "Figma",
        "Photoshop",
        "Illustrator",
        "Guitar",
        "Piano / Keyboard",
        "Hát / Vocal",
        "Digital Marketing",
        "Excel & Data Analysis",
        "Public Speaking",
        "Quản lý thời gian",
        "Tư duy phản biện",
        "Lãnh đạo nhóm"
    )

    val CITIES = listOf(
        "Hà Nội",
        "TP.HCM",
        "Đà Nẵng",
        "Cần Thơ",
        "Hải Phòng"
    )

    private val FIRST_NAMES = listOf(
        "Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Phan", "Vũ", "Võ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"
    )

    private val MIDDLE_NAMES = listOf(
        "Văn", "Thị", "Minh", "Thu", "Đức", "Hoàng", "Ngọc", "Thanh", "Quang", "Hải", "Tuấn", "Phương", "Anh", "Mai"
    )

    private val LAST_NAMES = listOf(
        "Nam", "Linh", "Huy", "Trang", "Dũng", "Hoa", "Tuấn", "Thảo", "Hùng", "Hương", "Long", "Lan", "Phong", "Hà", "Kiên", "Yến", "Đạt", "Nhung"
    )

    private val BIO_TEMPLATES = listOf(
        "Kỹ sư phần mềm 3 năm kinh nghiệm, muốn giao lưu học thêm ngoại ngữ và kỹ năng sáng tạo.",
        "Sinh viên năm cuối ĐH Bách Khoa, đam mê công nghệ mới và muốn nâng cao kỹ năng mềm.",
        "Chuyên viên thiết kế đồ họa, mong muốn tìm bạn cùng học lập trình ứng dụng hoặc kỹ năng số.",
        "Yêu thích âm nhạc và nhạc cụ cổ điển, sẵn sàng hướng dẫn cơ bản cho người mới bắt đầu.",
        "Làm việc trong ngành truyền thông & marketing, cần trau dồi thêm tiếng Anh để làm việc với đối tác quốc tế.",
        "Đam mê phân tích dữ liệu và tự động hóa công việc, tìm kiếm bạn cùng sở thích trao đổi kinh nghiệm.",
        "Người yêu thích học hỏi và chia sẻ kiến thức, tin vào giá trị của việc học tập cộng đồng.",
        "Có kinh nghiệm quản lý dự án nhỏ, muốn học thêm thiết kế giao diện Figma để tối ưu sản phẩm."
    )

    private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    private val TIME_SLOTS = listOf(
        Pair("08:00", "10:00"),
        Pair("09:00", "11:00"),
        Pair("14:00", "16:00"),
        Pair("18:30", "20:30"),
        Pair("19:00", "21:00"),
        Pair("20:00", "22:00")
    )

    /**
     * Sinh danh sách 40 người dùng demo (5 kịch bản + 35 ngẫu nhiên có seed cố định).
     */
    fun generateUsers(seed: Long = RANDOM_SEED): List<SeedUser> {
        val scenarioUsers = generateScenarioUsers()
        val randomUsers = generateRandomUsers(seed, count = 35)
        return scenarioUsers + randomUsers
    }

    fun generateScenarioUsers(): List<SeedUser> {
        return listOf(
            // 1. Sender: Dạy Kotlin Android (Lv 4), Cần Python (Lv 2)
            SeedUser(
                email = "sender$DEMO_DOMAIN",
                fullName = "Đặng Tuấn Long",
                city = "Hà Nội",
                bio = "Lập trình viên Android 3 năm kinh nghiệm với Jetpack Compose & Kotlin. Muốn học Python cơ bản để làm Data Science.",
                availability = listOf(
                    AvailabilityWindowDto(day = "MON", from = "19:00", to = "21:00"),
                    AvailabilityWindowDto(day = "WED", from = "19:00", to = "21:00"),
                    AvailabilityWindowDto(day = "FRI", from = "19:00", to = "21:00")
                ),
                skills = listOf(
                    SeedSkill(name = "Kotlin Android", level = 4, type = "HAVE"),
                    SeedSkill(name = "Python", level = 2, type = "WANT")
                ),
                isScenarioUser = true
            ),

            // 2. Receiver: Dạy Python (Lv 3), Cần Kotlin Android (Lv 2) — Lịch rảnh MON và WED 19:00-21:00 CHỒNG NHAU với sender!
            SeedUser(
                email = "receiver$DEMO_DOMAIN",
                fullName = "Nguyễn Minh Quân",
                city = "Hà Nội",
                bio = "Chuyên viên Python Backend & Automation. Đang ấp ủ viết app di động nên rất cần tìm người hướng dẫn Kotlin Android cơ bản.",
                availability = listOf(
                    AvailabilityWindowDto(day = "MON", from = "19:00", to = "21:00"),
                    AvailabilityWindowDto(day = "WED", from = "19:00", to = "21:00"),
                    AvailabilityWindowDto(day = "SAT", from = "19:00", to = "21:00")
                ),
                skills = listOf(
                    SeedSkill(name = "Python", level = 3, type = "HAVE"),
                    SeedSkill(name = "Kotlin Android", level = 2, type = "WANT")
                ),
                isScenarioUser = true
            ),

            // 3. UI/UX: Dạy Figma (Lv 4), Cần Tiếng Anh (Lv 2)
            SeedUser(
                email = "uiux$DEMO_DOMAIN",
                fullName = "Lê Thu Trang",
                city = "TP.HCM",
                bio = "Product Designer tại startup công nghệ. Sẵn sàng chia sẻ kinh nghiệm thiết kế UI/UX trên Figma, đổi lại muốn luyện nói Tiếng Anh.",
                availability = listOf(
                    AvailabilityWindowDto(day = "TUE", from = "18:30", to = "20:30"),
                    AvailabilityWindowDto(day = "THU", from = "18:30", to = "20:30")
                ),
                skills = listOf(
                    SeedSkill(name = "Figma", level = 4, type = "HAVE"),
                    SeedSkill(name = "Tiếng Anh giao tiếp", level = 2, type = "WANT")
                ),
                isScenarioUser = true
            ),

            // 4. Lang: Dạy Tiếng Anh (Lv 5), Cần Guitar (Lv 1)
            SeedUser(
                email = "lang$DEMO_DOMAIN",
                fullName = "Hoàng Văn Đức",
                city = "Đà Nẵng",
                bio = "Cựu du học sinh với IELTS 8.0, yêu thích giao lưu và luyện phản xạ tiếng Anh giao tiếp. Muốn học đàn Guitar từ số 0.",
                availability = listOf(
                    AvailabilityWindowDto(day = "SAT", from = "09:00", to = "11:00"),
                    AvailabilityWindowDto(day = "SUN", from = "09:00", to = "11:00")
                ),
                skills = listOf(
                    SeedSkill(name = "Tiếng Anh giao tiếp", level = 5, type = "HAVE"),
                    SeedSkill(name = "Guitar", level = 1, type = "WANT")
                ),
                isScenarioUser = true
            ),

            // 5. Music: Dạy Guitar (Lv 3), Cần Figma (Lv 2)
            SeedUser(
                email = "music$DEMO_DOMAIN",
                fullName = "Phạm Thuỳ Linh",
                city = "Hà Nội",
                bio = "Chơi acoustic guitar 5 năm, nhận hướng dẫn đệm hát cơ bản. Muốn học thêm Figma để tự thiết kế poster và bìa nhạc cá nhân.",
                availability = listOf(
                    AvailabilityWindowDto(day = "TUE", from = "20:00", to = "22:00"),
                    AvailabilityWindowDto(day = "THU", from = "20:00", to = "22:00"),
                    AvailabilityWindowDto(day = "SUN", from = "20:00", to = "22:00")
                ),
                skills = listOf(
                    SeedSkill(name = "Guitar", level = 3, type = "HAVE"),
                    SeedSkill(name = "Figma", level = 2, type = "WANT")
                ),
                isScenarioUser = true
            )
        )
    }

    fun generateRandomUsers(seed: Long, count: Int = 35): List<SeedUser> {
        val rand = Random(seed)
        val users = mutableListOf<SeedUser>()

        for (i in 1..count) {
            val email = "user$i$DEMO_DOMAIN"
            val fullName = "${FIRST_NAMES[rand.nextInt(FIRST_NAMES.size)]} ${MIDDLE_NAMES[rand.nextInt(MIDDLE_NAMES.size)]} ${LAST_NAMES[rand.nextInt(LAST_NAMES.size)]}"
            val city = CITIES[rand.nextInt(CITIES.size)]
            val bio = BIO_TEMPLATES[rand.nextInt(BIO_TEMPLATES.size)]

            // Availability: 1 đến 3 khung giờ khác nhau
            val daysCount = 1 + rand.nextInt(3)
            val selectedDays = DAYS.shuffled(rand).take(daysCount)
            val availability = selectedDays.map { day ->
                val slot = TIME_SLOTS[rand.nextInt(TIME_SLOTS.size)]
                AvailabilityWindowDto(day = day, from = slot.first, to = slot.second)
            }.sortedBy { DAYS.indexOf(it.day) }

            // Kỹ năng: 1 đến 3 HAVE, 1 đến 3 WANT, không trùng nhau
            val shuffledSkills = AVAILABLE_SKILLS.shuffled(rand)
            val haveCount = 1 + rand.nextInt(3) // 1..3
            val wantCount = 1 + rand.nextInt(3) // 1..3

            val haveSkills = shuffledSkills.take(haveCount).map { name ->
                SeedSkill(name = name, level = 2 + rand.nextInt(4), type = "HAVE") // Level 2..5
            }

            val remainingSkills = shuffledSkills.drop(haveCount)
            val wantSkills = remainingSkills.take(wantCount).map { name ->
                SeedSkill(name = name, level = 1 + rand.nextInt(3), type = "WANT") // Level 1..3
            }

            users.add(
                SeedUser(
                    email = email,
                    fullName = fullName,
                    city = city,
                    bio = bio,
                    availability = availability,
                    skills = haveSkills + wantSkills,
                    isScenarioUser = false
                )
            )
        }

        return users
    }
}
