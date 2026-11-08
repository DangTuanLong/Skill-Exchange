package com.skillexchange.api.services.nlp

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Mẫu dữ liệu đánh giá trích xuất kỹ năng từ tài liệu CV hư cấu.
 * Tách biệt rõ 4 loại nhãn:
 * - [expectedHave]: Kỹ năng ứng viên thực sự sở hữu (Ground Truth HAVE)
 * - [expectedWant]: Kỹ năng ứng viên muốn học hỏi (Ground Truth WANT)
 * - [negativeSkills]: Kỹ năng bị phủ định ("chưa biết", "không thạo", "chưa từng làm")
 * - [orgSkills]: Kỹ năng xuất hiện trong tên trường, cơ quan, công ty, CLB
 */
data class CvBenchmarkSample(
    val id: String,
    val title: String,
    val content: String,
    val isHeldOut: Boolean,
    val expectedHave: Set<String>,
    val expectedWant: Set<String> = emptySet(),
    val negativeSkills: Set<String> = emptySet(),
    val orgSkills: Set<String> = emptySet(),
    val notes: String = ""
)

class CvSkillExtractionBenchmarkTest {

    private val extractor = SkillExtractionService()

    /**
     * BỘ DỮ LIỆU 24 CV HƯ CẤU ĐÁNH GIÁ TRÍCH XUẤT KỸ NĂNG:
     * - 10 CV: Tập Tinh chỉnh (Tuning Set) - Dùng để phân tích lỗi và hiệu chỉnh.
     * - 14 CV: Tập Kiểm tra Giữ kín (Held-out Test Set) - Tuyệt đối không dùng để chỉnh alias hay regex.
     */
    val cvDataset = listOf(
        // ════════════════════════════════════════════════════════════════════
        // TẬP TINH CHỈNH (TUNING SET - 10 CV)
        // ════════════════════════════════════════════════════════════════════
        CvBenchmarkSample(
            id = "CV-01",
            title = "Backend Developer (1 cột, bullet, có số năm kinh nghiệm)",
            isHeldOut = false,
            content = """
                HỌ TÊN: LÊ VĂN BÌNH
                Vị trí: Kỹ sư Phần mềm Backend
                Tóm tắt: 3 năm kinh nghiệm phát triển hệ thống backend phân tán và microservices.
                KỸ NĂNG CHUYÊN MÔN:
                - Ngôn ngữ lập trình: Java, Python
                - Cơ sở dữ liệu: SQL
                - Ngoại ngữ: Tiếng Anh lưu loát (TOEIC 850)
                KINH NGHIỆM LÀM VIỆC:
                - Backend Engineer tại TechCorp (2022 - Nay): Tối ưu truy vấn SQL, xây dựng API bằng Java Spring Boot.
                SỞ THÍCH:
                - Thích bơi lội và chạy bộ mỗi buổi sáng.
            """.trimIndent(),
            expectedHave = setOf("Java", "Python", "SQL", "Tiếng Anh", "Bơi lội"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "CV chuẩn 1 cột, kỹ năng bullet rõ ràng."
        ),

        CvBenchmarkSample(
            id = "CV-02",
            title = "Frontend & UI/UX (2 cột song ngữ Anh - Việt)",
            isHeldOut = false,
            content = """
                TRẦN THỊ HỒNG NHUNG - Frontend Developer & UI Designer
                
                [CỘT TRÁI - SKILLS & TOOLS]
                - Web (React), Node.js
                - Figma / UI Design
                - Adobe Photoshop
                - Tiếng Nhật (N3)
                
                [CỘT PHẢI - WORK EXPERIENCE]
                - Web Developer tại Creative Agency (2021 - 2024):
                  + Thiết kế giao diện trên figma và chuyển đổi sang component Web (React).
                  + Chỉnh sửa hình ảnh banner bằng photoshop.
                  + Phối hợp cùng đội ngũ phát triển backend Node.js.
            """.trimIndent(),
            expectedHave = setOf("Web (React)", "Node.js", "Figma / UI Design", "Adobe Photoshop", "Tiếng Nhật"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "2 cột song ngữ, có alias figma và photoshop."
        ),

        CvBenchmarkSample(
            id = "CV-03",
            title = "[CV KHÓ] Backend Developer có chứa bẫy phủ định",
            isHeldOut = false,
            content = """
                HỌ TÊN: NGUYỄN VĂN AN
                Vị trí: Lập trình viên Backend
                TÓM TẮT NĂNG LỰC:
                - Thành thạo lập trình Python, Java và xử lý cơ sở dữ liệu SQL.
                - Đam mê thể thao, thường xuyên tham gia đá bóng cuối tuần.
                LƯU Ý VỀ KINH NGHIỆM:
                - Tôi chưa từng làm việc với Flutter và hoàn toàn không có kinh nghiệm về Android (Kotlin).
                - Xin không giao các tác vụ liên quan đến mobile app.
            """.trimIndent(),
            expectedHave = setOf("Python", "Java", "SQL", "Bóng đá"),
            expectedWant = emptySet(),
            negativeSkills = setOf("Flutter", "Android (Kotlin)"),
            orgSkills = emptySet(),
            notes = "Chứa phủ định rõ ràng: 'chưa từng làm việc với Flutter', 'không có kinh nghiệm về Android (Kotlin)'."
        ),

        CvBenchmarkSample(
            id = "CV-04",
            title = "Kỹ sư AI có mục tiêu muốn học thêm (WANT)",
            isHeldOut = false,
            content = """
                HỌ TÊN: PHẠM QUỐC BẢO
                Chức danh: Kỹ sư Trí tuệ nhân tạo (AI/ML)
                KỸ NĂNG HIỆN CÓ:
                - AI/ML, Python, SQL
                - Đọc hiểu tài liệu Tiếng Anh chuyên ngành tốt
                MỤC TIÊU CÁ NHÂN & KỸ NĂNG MUỐN HỌC:
                - Đang tìm người hướng dẫn học chơi Guitar để giảm căng thẳng sau giờ làm việc.
                - Kế hoạch năm tới: muốn học thêm môn Bơi lội để rèn luyện thể lực.
            """.trimIndent(),
            expectedHave = setOf("AI/ML", "Python", "SQL", "Tiếng Anh"),
            expectedWant = setOf("Guitar", "Bơi lội"),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Tách biệt rõ ràng kỹ năng hiện có (HAVE) và mục tiêu muốn học (WANT: Guitar, Bơi lội)."
        ),

        CvBenchmarkSample(
            id = "CV-05",
            title = "[CV KHÓ] UI/UX Designer có bẫy tên trường học và công ty",
            isHeldOut = false,
            content = """
                HỒ SƠ ỨNG VIÊN: TRẦN THỊ MAI
                Vị trí: Chuyên viên Thiết kế Giao diện
                HỌC VẤN:
                - Cử nhân tại Trường Đại học Ngoại ngữ - Đại học Quốc gia Hà Nội (2018 - 2022)
                - Thành viên tích cực của Câu lạc bộ Cờ vua sinh viên
                KINH NGHIỆM LÀM VIỆC:
                - Nhân viên Thiết kế tại Công ty Cổ phần Bơi lội Sao Vàng (2022 - 2024):
                  + Phụ trách vẽ minh họa bằng Illustrator.
                  + Thiết kế giao diện app mobile trên Figma / UI Design.
                KỸ NĂNG CÁ NHÂN:
                - Thành thạo Illustrator, Figma / UI Design.
                - Giao tiếp tốt bằng Tiếng Nhật.
            """.trimIndent(),
            expectedHave = setOf("Illustrator", "Figma / UI Design", "Tiếng Nhật"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = setOf("Bơi lội", "Cờ vua"),
            notes = "Bẫy tên tổ chức: 'Đại học Ngoại ngữ' không phải Tiếng Anh; 'Công ty Cổ phần Bơi lội' không phải kỹ năng Bơi lội; 'CLB Cờ vua' là tên CLB."
        ),

        CvBenchmarkSample(
            id = "CV-06",
            title = "Junior Graphic & 3D Designer (Full English)",
            isHeldOut = false,
            content = """
                CURRICULUM VITAE: EMILY VU
                Role: Graphic & 3D Visual Artist
                EDUCATION:
                - Bachelor of Arts in Multimedia Design (2020 - 2024)
                CORE COMPETENCIES:
                - Adobe Photoshop, Illustrator
                - 3D Modeling (Blender, Maya)
                - Motion Graphics
                - English (Fluent professional proficiency)
                WORK EXPERIENCE:
                - Junior 3D Artist at Pixel Studio:
                  + Created 3D assets and character models using 3D Modeling software.
                  + Edited promotional teasers using Motion Graphics tools.
            """.trimIndent(),
            expectedHave = setOf("Adobe Photoshop", "Illustrator", "3D Modeling", "Motion Graphics", "Tiếng Anh"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Tiếng Anh hoàn toàn, có alias Blender/Maya -> 3D Modeling."
        ),

        CvBenchmarkSample(
            id = "CV-07",
            title = "Lập trình viên Web viết tiếng Việt không dấu",
            isHeldOut = false,
            content = """
                ho va ten: nguyen hoang long
                chuc danh: lap trinh vien fullstack
                ky nang chuyen mon:
                - lap trinh web frontend bang reactjs va backend nodejs.
                - quan tri co so du lieu sql.
                - ngoai ngu: tieng anh doc hieu tot.
                tai le: biet danh dan piano va thich choi cau long vao cuoi tuan.
            """.trimIndent(),
            expectedHave = setOf("Web (React)", "Node.js", "SQL", "Tiếng Anh", "Piano", "Cầu lông"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Toàn bộ văn bản không dấu, kiểm tra khả năng unaccent."
        ),

        CvBenchmarkSample(
            id = "CV-08",
            title = "Senior Mobile Engineer văn xuôi dài có thâm niên",
            isHeldOut = false,
            content = """
                HỒ SƠ NĂNG LỰC CHUYÊN GIA
                Tôi là Đỗ Tuấn Kiệt, kỹ sư di động với hơn 5 năm kinh nghiệm chuyên sâu về Android (Kotlin).
                Trong suốt sự nghiệp, tôi đã trực tiếp thiết kế kiến trúc và phát hành 8 ứng dụng lớn trên Google Play Store.
                Ngoài ra tôi thành thạo xử lý cơ sở dữ liệu SQL cục bộ với Room và viết script tự động hóa bằng Python.
                Về kỹ năng mềm và quản trị, tôi có hơn 2 năm đảm nhận vai trò Quản lý dự án cho nhóm 10 lập trình viên.
                Giao tiếp thành thạo bằng Tiếng Anh với các đối tác nước ngoài.
            """.trimIndent(),
            expectedHave = setOf("Android (Kotlin)", "SQL", "Python", "Quản lý dự án", "Tiếng Anh"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Văn xuôi dài, có từ khóa Senior 5 năm kinh nghiệm."
        ),

        CvBenchmarkSample(
            id = "CV-09",
            title = "CV Thể thao, Sức khỏe & Quản lý dự án",
            isHeldOut = false,
            content = """
                HỌ TÊN: NGUYỄN THỊ THU HÀ
                Nghề nghiệp: Huấn luyện viên Thể chất & Điều phối viên Sự kiện
                KỸ NĂNG VẬN ĐỘNG & THỂ THAO:
                - Huấn luyện viên Yoga chứng chỉ quốc tế 200 giờ
                - Chơi tốt bộ môn Cầu lông và Tennis
                KỸ NĂNG QUẢN LÝ & CÔNG VIỆC:
                - Kỹ năng Quản lý dự án sự kiện thể thao
                - Sử dụng thành thạo Excel nâng cao để lập ngân sách và theo dõi chi phí
                - Ngoại ngữ: Tiếng Pháp giao tiếp lưu loát
            """.trimIndent(),
            expectedHave = setOf("Yoga", "Cầu lông", "Tennis", "Quản lý dự án", "Excel nâng cao", "Tiếng Pháp"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Đa ngành: Thể thao kết hợp Kinh doanh/Văn phòng."
        ),

        CvBenchmarkSample(
            id = "CV-10",
            title = "Fullstack Dev với các từ viết tắt kỹ thuật",
            isHeldOut = false,
            content = """
                HỒ SƠ ỨNG VIÊN: VŨ ĐỨC THÀNH
                Vị trí: Fullstack Developer
                CÔNG NGHỆ & KỸ NĂNG CHÍNH:
                - Ngôn ngữ: Java, Python
                - Web: Web (React), Node.js
                - Cơ sở dữ liệu: SQL
                - Thiết kế: Figma / UI Design
                - Ngoại ngữ: Tiếng Hàn trung cấp (TOPIK 4)
                SỞ THÍCH:
                - Thích chơi đàn Ukulele và hát mộc.
            """.trimIndent(),
            expectedHave = setOf("Java", "Python", "Web (React)", "Node.js", "SQL", "Figma / UI Design", "Tiếng Hàn", "Ukulele", "Hát"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Nhiều kỹ năng công nghệ và âm nhạc kết hợp."
        ),

        // ════════════════════════════════════════════════════════════════════
        // TẬP KIỂM TRA GIỮ KÍN (HELD-OUT TEST SET - 14 CV)
        // ════════════════════════════════════════════════════════════════════
        CvBenchmarkSample(
            id = "CV-11",
            title = "Digital Marketing Specialist có từ vựng đời thường",
            isHeldOut = true,
            content = """
                HỌ TÊN: NGUYỄN THẢO NGUYÊN
                Vị trí: Chuyên viên Marketing Digital
                KINH NGHIỆM NGHỀ NGHIỆP:
                - 3 năm làm việc trong lĩnh vực Marketing Digital và truyền thông số.
                - Sử dụng thành thạo Excel nâng cao để phân tích dữ liệu chiến dịch.
                - Biết chỉnh sửa ảnh cơ bản bằng Adobe Photoshop.
                - Khả năng đọc dịch tài liệu Tiếng Anh tốt.
                GHI CHÚ ĐỜI SỐNG:
                - Thích uống cà phê sữa đá, xem phim truyền hình vào mỗi tối chủ nhật cùng gia đình.
            """.trimIndent(),
            expectedHave = setOf("Marketing Digital", "Excel nâng cao", "Adobe Photoshop", "Tiếng Anh"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Chứa từ ngữ đời sống (cà phê, xem phim) không có kỹ năng giả."
        ),

        CvBenchmarkSample(
            id = "CV-12",
            title = "[CV KHÓ] Âm nhạc & Bẫy tên trường học viện",
            isHeldOut = true,
            content = """
                HỒ SƠ CÁ NHÂN: LÊ HOÀNG NAM
                HỌC VẤN:
                - Tốt nghiệp chuyên ngành Sáng tác nhạc tại một trường đào tạo nghệ thuật (2019 - 2023)
                - Cựu chủ nhiệm Câu lạc bộ Cờ vua sinh viên
                KỸ NĂNG THỰC TẾ:
                - Thành thạo nhạc cụ Guitar và Piano.
                - Có khả năng Hát và đệm hát acoustic biểu diễn phòng trà.
                - Sáng tác nhạc và hòa âm phối khí cơ bản.
                KINH NGHIỆM: 3 năm gia sư dạy đàn piano cho thanh thiếu niên.
            """.trimIndent(),
            expectedHave = setOf("Sáng tác nhạc", "Guitar", "Piano", "Hát"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = setOf("Cờ vua"),
            notes = "'Câu lạc bộ Cờ vua sinh viên' là tên CLB trong trường, không phải kỹ năng thi đấu cờ vua của ứng viên."
        ),

        CvBenchmarkSample(
            id = "CV-13",
            title = "[CV KHÓ] Web Developer có phủ định mạnh đa mệnh đề",
            isHeldOut = true,
            content = """
                HỒ SƠ: PHẠM VĂN ĐỨC
                Chuyên môn: Lập trình viên Web Frontend
                KỸ NĂNG CHÍNH:
                - Thành thạo Web (React) và Node.js.
                - Ngoại ngữ: Tiếng Anh giao tiếp công việc tốt.
                LƯU Ý QUAN TRỌNG VỀ PHẠM VI NĂNG LỰC:
                - Tôi không có kinh nghiệm Java, chưa từng học Tiếng Nhật và hoàn toàn không biết gì về iOS (Swift).
                - Xin quý công ty không sắp xếp tôi vào các dự án di động hoặc backend Java.
                SỞ THÍCH:
                - Rèn luyện thể lực qua môn Gym / Fitness đều đặn.
            """.trimIndent(),
            expectedHave = setOf("Web (React)", "Node.js", "Tiếng Anh", "Gym / Fitness"),
            expectedWant = emptySet(),
            negativeSkills = setOf("Java", "Tiếng Nhật", "iOS (Swift)"),
            orgSkills = emptySet(),
            notes = "Phủ định liên tiếp 3 kỹ năng trong 1 đoạn văn."
        ),

        CvBenchmarkSample(
            id = "CV-14",
            title = "Người tìm học kỹ năng âm nhạc (WANT)",
            isHeldOut = true,
            content = """
                HỌ TÊN: BÙI QUANG HUY
                Nghề nghiệp: Kỹ sư Phần mềm Python
                KỸ NĂNG ĐANG CÓ (HAVE):
                - Lập trình Python, phân tích dữ liệu với SQL.
                - Tiếng Đức trình độ B1.
                NHU CẦU TRAO ĐỔI & HỌC TẬP (WANT):
                - Đang rất cần tìm người hướng dẫn học đàn Piano căn bản từ đầu.
                - Muốn học thêm về Beat making và sản xuất âm nhạc số trên máy tính.
            """.trimIndent(),
            expectedHave = setOf("Python", "SQL", "Tiếng Đức"),
            expectedWant = setOf("Piano", "Beat making"),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Mục WANT rất rõ: Piano, Beat making."
        ),

        CvBenchmarkSample(
            id = "CV-15",
            title = "Senior DevOps & Cloud Engineer (English)",
            isHeldOut = true,
            content = """
                RESUME: ALEXANDER TRAN
                Position: Senior Infrastructure & DevOps Engineer
                SUMMARY:
                - 6+ years of experience in system architecture, automation, and database administration.
                TECHNICAL SKILLS:
                - Languages & Scripting: Python, Java
                - Databases: SQL
                - Mobile & Others: None
                - Languages: English (Native bilingual)
                INTERESTS:
                - Playing tennis and chess in spare time.
            """.trimIndent(),
            expectedHave = setOf("Python", "Java", "SQL", "Tiếng Anh", "Tennis", "Cờ vua"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "CV tiếng Anh chuẩn với tennis và cờ vua (chess)."
        ),

        CvBenchmarkSample(
            id = "CV-16",
            title = "Dựng phim & Video Editing viết không dấu",
            isHeldOut = true,
            content = """
                ho ten: phan dinh tung
                nghe nghiep: chuyen vien dung phim va truyen thong
                kinh nghiem:
                - 2 nam lam video editing cho kenh youtube cong nghe.
                - su dung thanh thao adobe photoshop va illustrator de ve poster.
                - co the choi dan guitar dem hat giao luu.
            """.trimIndent(),
            expectedHave = setOf("Video Editing", "Adobe Photoshop", "Illustrator", "Guitar", "Hát"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Tiếng Việt không dấu kết hợp Video Editing và đồ họa."
        ),

        CvBenchmarkSample(
            id = "CV-17",
            title = "Giáo viên Ngoại ngữ & Thể thao",
            isHeldOut = true,
            content = """
                HỒ SƠ GIẢNG VIÊN: NGUYỄN MAI PHƯƠNG
                Chuyên môn: Giảng viên Ngoại ngữ
                NĂNG LỰC NGÔN NGỮ:
                - Tiếng Anh: C1 Advanced, 4 năm kinh nghiệm giảng dạy giao tiếp.
                - Tiếng Pháp: B2 DELF, có thể dạy kèm từ vựng và ngữ pháp.
                HOẠT ĐỘNG THỂ THAO & SỨC KHỎE:
                - Bơi lội: Từng đạt giải bơi cấp trường thời đại học.
                - Tập luyện Gym / Fitness hàng ngày.
            """.trimIndent(),
            expectedHave = setOf("Tiếng Anh", "Tiếng Pháp", "Bơi lội", "Gym / Fitness"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Kỹ năng ngoại ngữ kết hợp thể thao cá nhân."
        ),

        CvBenchmarkSample(
            id = "CV-18",
            title = "[CV KHÓ] Ngắt dòng giữa từ bằng dấu gạch nối (Hyphenation)",
            isHeldOut = true,
            content = """
                HỒ SƠ ỨNG VIÊN: HOÀNG THU HÀ
                Vị trí mong muốn: Chuyên viên Thiết kế & Truyền thông
                KỸ NĂNG:
                - Chuyên môn sâu về Adobe Pho-
                toshop và thiết kế đồ họa vector với Illus-
                trator chuyên nghiệp.
                - Có kinh nghiệm quay dựng phim ngắn và thực hiện video edi-
                ting trên các phần mềm hiện đại.
                - Ngoại ngữ: Tiếng Anh giao tiếp tốt trong môi trường quốc tế.
            """.trimIndent(),
            expectedHave = setOf("Adobe Photoshop", "Illustrator", "Video Editing", "Tiếng Anh"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Các từ kỹ năng bị ngắt dòng có gạch nối: 'Pho-\ntoshop', 'Illus-\ntrator', 'edi-\nting'."
        ),

        CvBenchmarkSample(
            id = "CV-19",
            title = "Kế toán & Phân tích số liệu nhiều cấp độ",
            isHeldOut = true,
            content = """
                HỌ TÊN: ĐẶNG THỊ HUYỀN
                Nghề nghiệp: Chuyên viên Phân tích Dữ liệu Tài chính
                KỸ NĂNG & CÔNG CỤ:
                - Thành thạo Excel nâng cao (VLOOKUP, Pivot Table, Macro).
                - Sử dụng được SQL để trích xuất báo cáo từ kho dữ liệu.
                - Biết cơ bản về Python để tự động hóa xử lý bảng tính.
                - Ngoại ngữ: Tiếng Trung giao tiếp cơ bản (HSK 3).
                SỞ THÍCH:
                - Chơi môn Cầu lông vào chiều thứ Bảy.
            """.trimIndent(),
            expectedHave = setOf("Excel nâng cao", "SQL", "Python", "Tiếng Trung", "Cầu lông"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Nhiều cấp độ thành thạo khác nhau (thành thạo, sử dụng được, biết cơ bản)."
        ),

        CvBenchmarkSample(
            id = "CV-20",
            title = "[CV KHÓ] Mobile Dev tiếng Anh có phủ định (English Negation)",
            isHeldOut = true,
            content = """
                CURRICULUM VITAE: DAVID NGUYEN
                Role: Mobile Application Engineer
                SKILLS & TECHNOLOGIES:
                - Proficient in Android (Kotlin) development with 4 years of hands-on experience.
                - Solid understanding of Python for backend scripting and test automation.
                - Not familiar with iOS (Swift) and have no experience with Flutter framework.
                - Fluent in English and intermediate in Tiếng Nhật.
                SPORTS & HOBBIES:
                - Enjoy playing tennis and badminton on weekends.
            """.trimIndent(),
            expectedHave = setOf("Android (Kotlin)", "Python", "Tiếng Anh", "Tiếng Nhật", "Tennis", "Cầu lông"),
            expectedWant = emptySet(),
            negativeSkills = setOf("iOS (Swift)", "Flutter"),
            orgSkills = emptySet(),
            notes = "Phủ định bằng tiếng Anh: 'Not familiar with iOS (Swift)', 'have no experience with Flutter'."
        ),

        CvBenchmarkSample(
            id = "CV-21",
            title = "Nghệ sĩ nhạc cụ dân gian & hiện đại",
            isHeldOut = true,
            content = """
                HỌ TÊN: NGUYỄN XUÂN TÙNG
                Nghề nghiệp: Nghệ sĩ Biểu diễn Âm nhạc Tự do
                KỸ NĂNG NGHỆ THUẬT CÓ SẴN (HAVE):
                - Sử dụng thành thạo đàn Ukulele và Guitar acoustic.
                - Khả năng Hát chính và bè phụ trong ban nhạc mộc.
                NHU CẦU HỌC HỎI THÊM (WANT):
                - Đang tìm giảng viên có kinh nghiệm để học đàn Violin cổ điển.
                - Muốn học thêm bộ môn Trống jazz để mở rộng kỹ năng nhịp điệu.
            """.trimIndent(),
            expectedHave = setOf("Ukulele", "Guitar", "Hát"),
            expectedWant = setOf("Violin", "Trống"),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "HAVE: Ukulele, Guitar, Hát. WANT: Violin, Trống."
        ),

        CvBenchmarkSample(
            id = "CV-22",
            title = "Huấn luyện viên Fitness & Vận động viên",
            isHeldOut = true,
            content = """
                HỒ SƠ HUẤN LUYỆN VIÊN: VÕ MINH TRÍ
                Chuyên môn: Đào tạo Thể hình & Sức khỏe Cá nhân
                CHỨNG CHỈ & KỸ NĂNG:
                - Chuyên gia huấn luyện Gym / Fitness (5 năm kinh nghiệm).
                - Khả năng Bơi lội cự ly dài (chứng chỉ cứu hộ hồ bơi).
                - Từng là cầu thủ Bóng đá phong trào cấp tỉnh.
                - Giao tiếp cơ bản bằng Tiếng Anh với khách nước ngoài tại phòng tập.
            """.trimIndent(),
            expectedHave = setOf("Gym / Fitness", "Bơi lội", "Bóng đá", "Tiếng Anh"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "Kỹ năng thể thao toàn diện."
        ),

        CvBenchmarkSample(
            id = "CV-23",
            title = "Nghiên cứu sinh Khoa học Máy tính (Dài, chuyên sâu)",
            isHeldOut = true,
            content = """
                LÝ LỊCH KHOA HỌC & NĂNG LỰC NGHIÊN CỨU
                Ứng viên: TS. TRẦN BẢO LONG
                Lĩnh vực chuyên sâu: Khoa học Dữ liệu và Trí tuệ Nhân tạo
                KỸ NĂNG CÔNG NGHỆ CHÍNH:
                - AI/ML: Nghiên cứu mô hình học máy, thị giác máy tính và xử lý ngôn ngữ tự nhiên.
                - Lập trình: Python, Java, C++, SQL.
                - Ngoại ngữ: Tiếng Anh thành thạo (IELTS 8.0), có thể công bố bài báo quốc tế.
                - Quản lý: Có kinh nghiệm Quản lý dự án nghiên cứu khoa học cấp viện.
                DANH MỤC CÔNG TRÌNH & DỰ ÁN TIÊU BIỂU:
                - Dự án hệ thống gợi ý thông minh dựa trên phân tích dữ liệu lớn.
                - Hướng dẫn sinh viên thực hiện đồ án tốt nghiệp về thị giác máy tính và học máy.
            """.trimIndent(),
            expectedHave = setOf("AI/ML", "Python", "Java", "SQL", "Tiếng Anh", "Quản lý dự án"),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "CV nghiên cứu dài, có C++ (không có trong DB nên không tính vào expectedHave, không bắt C)."
        ),

        CvBenchmarkSample(
            id = "CV-24",
            title = "CV hoàn toàn không chứa kỹ năng nào thuộc từ điển (Nhiễu âm tính thuần)",
            isHeldOut = true,
            content = """
                HỒ SƠ ỨNG VIÊN: HOÀNG ĐĂNG KHOA
                Vị trí: Nhân viên Hành chính & Lễ tân
                TÓM TẮT BẢN THÂN:
                Tôi là người hòa đồng, trung thực, có tinh thần trách nhiệm cao trong công việc.
                Luôn sẵn sàng hỗ trợ đồng nghiệp và tiếp đón khách hàng niềm nở, chu đáo.
                KINH NGHIỆM LÀM VIỆC:
                - Trực điện thoại bàn tổng đài, đón tiếp khách đến liên hệ công tác tại sảnh.
                - Soạn thảo công văn hành chính nội bộ, phát chuyển thư từ bưu phẩm.
                - Quản lý văn phòng phẩm và điều phối phòng họp nội bộ.
                SỞ THÍCH:
                - Thích đi dạo công viên, chăm sóc cây cảnh và nghe nhạc hòa tấu không lời.
            """.trimIndent(),
            expectedHave = emptySet(),
            expectedWant = emptySet(),
            negativeSkills = emptySet(),
            orgSkills = emptySet(),
            notes = "CV không có kỹ năng nào thuộc từ điển, kiểm tra False Positive rác."
        )
    )

    @Test
    fun `measure baseline accuracy on 24 CV synthetic benchmark dataset`() {
        var tuningTP = 0
        var tuningFP = 0
        var tuningFN = 0

        var heldOutTP = 0
        var heldOutFP = 0
        var heldOutFN = 0

        val tuningMisclassified = mutableListOf<String>()
        val heldOutMisclassified = mutableListOf<String>()

        println("\n" + "=".repeat(85))
        println("  SKILLEXCHANGE - CV SKILL EXTRACTION BENCHMARK (BASELINE MEASUREMENT)")
        println("=".repeat(85))

        for (sample in cvDataset) {
            val extracted = extractor.extractSkills(sample.content)
            val predicted = extracted.map { it.skillName }.toSet()
            val expected = sample.expectedHave

            val tp = predicted.intersect(expected).size
            val fp = predicted.minus(expected).size
            val fn = expected.minus(predicted).size

            if (sample.isHeldOut) {
                heldOutTP += tp
                heldOutFP += fp
                heldOutFN += fn
            } else {
                tuningTP += tp
                tuningFP += fp
                tuningFN += fn
            }

            if (fp > 0 || fn > 0) {
                val sb = StringBuilder()
                sb.appendLine("  [${sample.id}] [${if (sample.isHeldOut) "HELD-OUT" else "TUNING"}] ${sample.title}")
                sb.appendLine("    Ground Truth HAVE: $expected")
                sb.appendLine("    Predicted        : $predicted")
                if (fp > 0) {
                    val falsePositives = predicted.minus(expected)
                    sb.appendLine("    [!] False Positive (Thừa/Bắt nhầm): $falsePositives")
                    val negOverlap = falsePositives.intersect(sample.negativeSkills)
                    if (negOverlap.isNotEmpty()) {
                        sb.appendLine("        -> BẮT NHẦM CÂU PHỦ ĐỊNH (NEGATIVE): $negOverlap")
                    }
                    val orgOverlap = falsePositives.intersect(sample.orgSkills)
                    if (orgOverlap.isNotEmpty()) {
                        sb.appendLine("        -> BẮT NHẦM TÊN CƠ QUAN / TRƯỜNG HỌC (ORG): $orgOverlap")
                    }
                    val wantOverlap = falsePositives.intersect(sample.expectedWant)
                    if (wantOverlap.isNotEmpty()) {
                        sb.appendLine("        -> BẮT NHẦM MỤC TIÊU MUỐN HỌC (WANT gán vào HAVE): $wantOverlap")
                    }
                }
                if (fn > 0) {
                    sb.appendLine("    [?] False Negative (Bỏ sót): ${expected.minus(predicted)}")
                }
                if (sample.isHeldOut) {
                    heldOutMisclassified.add(sb.toString())
                } else {
                    tuningMisclassified.add(sb.toString())
                }
            }
        }

        val totalTP = tuningTP + heldOutTP
        val totalFP = tuningFP + heldOutFP
        val totalFN = tuningFN + heldOutFN

        fun calcMetrics(tp: Int, fp: Int, fn: Int): Triple<Double, Double, Double> {
            val precision = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
            val recall = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
            val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
            return Triple(precision, recall, f1)
        }

        val (tuningPrecision, tuningRecall, tuningF1) = calcMetrics(tuningTP, tuningFP, tuningFN)
        val (heldOutPrecision, heldOutRecall, heldOutF1) = calcMetrics(heldOutTP, heldOutFP, heldOutFN)
        val (totalPrecision, totalRecall, totalF1) = calcMetrics(totalTP, totalFP, totalFN)

        println("\n>>> KẾT QUẢ ĐO LƯỜNG HIỆN TRẠNG (BASELINE) TRÊN TẬP TINH CHỈNH (10 CV):")
        println("    - True Positives  (TP) : $tuningTP")
        println("    - False Positives (FP) : $tuningFP")
        println("    - False Negatives (FN) : $tuningFN")
        println("    - Precision            : ${"%.2f".format(tuningPrecision * 100)}%")
        println("    - Recall               : ${"%.2f".format(tuningRecall * 100)}%")
        println("    - F1-Score             : ${"%.2f".format(tuningF1 * 100)}%")

        println("\n>>> KẾT QUẢ ĐO LƯỜNG HIỆN TRẠNG (BASELINE) TRÊN TẬP KIỂM TRA GIỮ KÍN (14 CV):")
        println("    - True Positives  (TP) : $heldOutTP")
        println("    - False Positives (FP) : $heldOutFP")
        println("    - False Negatives (FN) : $heldOutFN")
        println("    - Precision            : ${"%.2f".format(heldOutPrecision * 100)}%")
        println("    - Recall               : ${"%.2f".format(heldOutRecall * 100)}%")
        println("    - F1-Score             : ${"%.2f".format(heldOutF1 * 100)}%")

        println("\n>>> TỔNG HỢP TOÀN BỘ 24 CV (BASELINE):")
        println("    - Total TP: $totalTP | Total FP: $totalFP | Total FN: $totalFN")
        println("    - Overall Precision : ${"%.2f".format(totalPrecision * 100)}%")
        println("    - Overall Recall    : ${"%.2f".format(totalRecall * 100)}%")
        println("    - Overall F1-Score  : ${"%.2f".format(totalF1 * 100)}%")

        println("\n>>> CHI TIẾT CÁC LỖI TRÊN TẬP TINH CHỈNH (TUNING MISCLASSIFIED):")
        if (tuningMisclassified.isEmpty()) println("    (Không có lỗi)")
        else tuningMisclassified.forEach { println(it) }

        println("\n>>> CHI TIẾT CÁC LỖI TRÊN TẬP KIỂM TRA GIỮ KÍN (HELD-OUT MISCLASSIFIED):")
        if (heldOutMisclassified.isEmpty()) println("    (Không có lỗi)")
        else heldOutMisclassified.forEach { println(it) }

        // ════════════════════════════════════════════════════════════════════
        // ĐO LƯỜNG TẬP CV MỚI BỔ SUNG (BÁO CÁO TÁCH BIỆT KHỎI 24 CV CŨ)
        // ════════════════════════════════════════════════════════════════════
        val additionalSamples = loadAdditionalCvSamples()
        if (additionalSamples.isEmpty()) {
            println("\n>>> TẬP CV MỚI BỔ SUNG (cv_additional):")
            println("    - Thư mục: api/src/test/resources/cv_additional/")
            println("    - Trạng thái: Hiện chưa có tệp CV bổ sung nào (Sẵn sàng để Chủ sở hữu đưa tệp và rà nhãn).")
            println("=".repeat(85) + "\n")
        } else {
            var addTP = 0
            var addFP = 0
            var addFN = 0
            val addMisclassified = mutableListOf<String>()

            for (sample in additionalSamples) {
                val extracted = extractor.extractSkills(sample.content)
                val predicted = extracted.map { it.skillName }.toSet()
                val expected = sample.expectedHave

                val tp = predicted.intersect(expected).size
                val fp = predicted.minus(expected).size
                val fn = expected.minus(predicted).size

                addTP += tp
                addFP += fp
                addFN += fn

                if (fp > 0 || fn > 0) {
                    val sb = StringBuilder()
                    sb.appendLine("  [${sample.id}] [ADDITIONAL_SET] ${sample.title}")
                    sb.appendLine("    Ground Truth HAVE: $expected")
                    sb.appendLine("    Predicted        : $predicted")
                    if (fp > 0) {
                        val falsePositives = predicted.minus(expected)
                        sb.appendLine("    [!] False Positive (Thừa/Bắt nhầm): $falsePositives")
                    }
                    if (fn > 0) {
                        sb.appendLine("    [?] False Negative (Bỏ sót): ${expected.minus(predicted)}")
                    }
                    addMisclassified.add(sb.toString())
                }
            }

            val (addPrecision, addRecall, addF1) = calcMetrics(addTP, addFP, addFN)
            println("\n>>> KẾT QUẢ ĐO LƯỜNG TRÊN TẬP CV MỚI BỔ SUNG (${additionalSamples.size} CV):")
            println("    - True Positives  (TP) : $addTP")
            println("    - False Positives (FP) : $addFP")
            println("    - False Negatives (FN) : $addFN")
            println("    - Precision            : ${"%.2f".format(addPrecision * 100)}%")
            println("    - Recall               : ${"%.2f".format(addRecall * 100)}%")
            println("    - F1-Score             : ${"%.2f".format(addF1 * 100)}%")

            println("\n>>> CHI TIẾT CÁC LỖI TRÊN TẬP CV MỚI BỔ SUNG (ADDITIONAL MISCLASSIFIED):")
            if (addMisclassified.isEmpty()) println("    (Không có lỗi)")
            else addMisclassified.forEach { println(it) }
            println("=".repeat(85) + "\n")
        }

        // Kiểm tra chạy thành công (không crash)
        assertTrue(totalTP > 0, "Benchmark baseline phải ghi nhận TP > 0")
    }

    /**
     * Tải các mẫu CV bổ sung từ thư mục `cv_additional` (nếu có).
     */
    fun loadAdditionalCvSamples(): List<CvBenchmarkSample> {
        val searchDirs = listOf(
            java.io.File("src/test/resources/cv_additional"),
            java.io.File("api/src/test/resources/cv_additional"),
            java.io.File("../api/src/test/resources/cv_additional"),
            java.io.File("cv_additional")
        )
        val dir = searchDirs.firstOrNull { it.exists() && it.isDirectory } ?: return emptyList()

        val files = dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".txt", ignoreCase = true) || f.name.endsWith(".md", ignoreCase = true))
                    && !f.name.equals("README.md", ignoreCase = true)
        }?.sortedBy { it.name } ?: return emptyList()

        return files.mapNotNull { file ->
            try {
                parseSampleFromFile(file)
            } catch (e: Exception) {
                System.err.println("Lỗi đọc tệp CV bổ sung ${file.name}: ${e.message}")
                null
            }
        }
    }

    private fun parseSampleFromFile(file: java.io.File): CvBenchmarkSample {
        val lines = file.readLines(Charsets.UTF_8)
        var id = file.nameWithoutExtension
        var title = file.nameWithoutExtension
        val expectedHave = mutableSetOf<String>()
        val expectedWant = mutableSetOf<String>()
        val negativeSkills = mutableSetOf<String>()
        val orgSkills = mutableSetOf<String>()
        var notes = ""

        val contentBuilder = StringBuilder()
        var inFrontmatter = false
        var frontmatterPassed = false

        var lineIdx = 0
        while (lineIdx < lines.size) {
            val line = lines[lineIdx]
            val trimmed = line.trim()

            if (!frontmatterPassed) {
                if (lineIdx == 0 && trimmed == "---") {
                    inFrontmatter = true
                    lineIdx++
                    continue
                }
                if (inFrontmatter) {
                    if (trimmed == "---") {
                        inFrontmatter = false
                        frontmatterPassed = true
                        lineIdx++
                        continue
                    }
                    parseHeaderLine(trimmed, { id = it }, { title = it }, expectedHave, expectedWant, negativeSkills, orgSkills, { notes = it })
                    lineIdx++
                    continue
                }
                if (trimmed.startsWith("#")) {
                    val withoutHash = trimmed.removePrefix("#").trim()
                    if (parseHeaderLine(withoutHash, { id = it }, { title = it }, expectedHave, expectedWant, negativeSkills, orgSkills, { notes = it })) {
                        lineIdx++
                        continue
                    }
                }
                frontmatterPassed = true
            }

            contentBuilder.appendLine(line)
            lineIdx++
        }

        return CvBenchmarkSample(
            id = id,
            title = title,
            content = contentBuilder.toString().trim(),
            isHeldOut = true,
            expectedHave = expectedHave,
            expectedWant = expectedWant,
            negativeSkills = negativeSkills,
            orgSkills = orgSkills,
            notes = notes
        )
    }

    private fun parseHeaderLine(
        line: String,
        setId: (String) -> Unit,
        setTitle: (String) -> Unit,
        expectedHave: MutableSet<String>,
        expectedWant: MutableSet<String>,
        negativeSkills: MutableSet<String>,
        orgSkills: MutableSet<String>,
        setNotes: (String) -> Unit
    ): Boolean {
        val colonIdx = line.indexOf(':')
        if (colonIdx == -1) return false
        val key = line.substring(0, colonIdx).trim().lowercase()
        val value = line.substring(colonIdx + 1).trim()

        fun parseSet(str: String): Set<String> = str.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        when (key) {
            "id" -> setId(value)
            "title" -> setTitle(value)
            "have", "expectedhave", "expected_have" -> expectedHave.addAll(parseSet(value))
            "want", "expectedwant", "expected_want" -> expectedWant.addAll(parseSet(value))
            "negative", "negativeskills", "negative_skills" -> negativeSkills.addAll(parseSet(value))
            "org", "orgskills", "org_skills" -> orgSkills.addAll(parseSet(value))
            "notes", "note" -> setNotes(value)
            else -> return false
        }
        return true
    }
}
