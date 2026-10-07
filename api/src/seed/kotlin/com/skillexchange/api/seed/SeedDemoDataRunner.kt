package com.skillexchange.api.seed

import com.skillexchange.api.config.DatabaseConfig
import com.skillexchange.api.config.EnvLoader
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.net.URI
import java.time.LocalDateTime
import kotlin.system.exitProcess

fun main() {
    println("==================================================")
    println("SkillExchange — Demo Data Seeder (TASK-013)")
    println("==================================================")

    val supabaseUrl = EnvLoader.get("SUPABASE_URL", "")
    val serviceKey = EnvLoader.get("SUPABASE_SERVICE_KEY", "")

    if (supabaseUrl.isBlank() || serviceKey.isBlank()) {
        System.err.println("[ERROR] Chưa cấu hình SUPABASE_URL hoặc SUPABASE_SERVICE_KEY trong môi trường hoặc .env.")
        System.err.println("Vui lòng bổ sung vào .env theo mẫu trong api/.env.example trước khi chạy.")
        exitProcess(1)
    }

    val targetHost = try {
        URI(supabaseUrl).host ?: "unknown"
    } catch (e: Exception) {
        "invalid-url"
    }

    println("Target Supabase Host: $targetHost")

    // Cơ chế chống chạy nhầm (Requirement 2)
    val confirm = EnvLoader.get("SEED_CONFIRM", "")
    if (confirm != "yes") {
        System.err.println("""
            
            [ABORTED] Chưa xác nhận chạy seed dữ liệu!
            Để tránh ghi nhầm vào môi trường không mong muốn, bạn cần thiết lập biến SEED_CONFIRM=yes.
            Ví dụ chạy qua PowerShell:
              ${'$'}env:SEED_CONFIRM="yes"; .\gradlew seedDemoData
            Hoặc thêm dòng sau vào api/.env:
              SEED_CONFIRM=yes
        """.trimIndent())
        exitProcess(1)
    }

    println("\n[1/3] Khởi tạo kết nối cơ sở dữ liệu...")
    DatabaseConfig.init()

    val adminClient = SupabaseAdminClient(supabaseUrl, serviceKey)

    println("[2/3] Đọc danh mục kỹ năng từ cơ sở dữ liệu...")
    val skillsMap = transaction {
        SkillsTable.selectAll().associate {
            it[SkillsTable.name] to it[SkillsTable.id]
        }
    }

    if (skillsMap.isEmpty()) {
        System.err.println("[WARNING] Bảng skills hiện đang rỗng. Vui lòng chạy API một lần để kích hoạt seedIfEmpty() hoặc nạp schema trước.")
        exitProcess(1)
    }

    println("Đã tải ${skillsMap.size} kỹ năng từ hệ thống.")

    println("[3/3] Bắt đầu sinh 40 người dùng demo (5 kịch bản + 35 ngẫu nhiên)...")
    val seedUsers = DemoDataGenerator.generateUsers()

    var successCount = 0
    for ((index, user) in seedUsers.withIndex()) {
        print("  (${index + 1}/40) Đang xử lý: ${user.email} (${user.fullName})... ")
        try {
            // 1. Tạo hoặc lấy user qua Supabase Admin API
            val adminUser = adminClient.createUser(
                email = user.email,
                password = DemoDataGenerator.DEMO_PASSWORD,
                fullName = user.fullName
            )
            val userId = adminUser.id

            // 2. Cập nhật Profile và Skills trong Database (Idempotent: Requirement 3)
            transaction {
                val existingProfile = ProfilesTable.selectAll()
                    .where { ProfilesTable.userId eq userId }
                    .firstOrNull()

                if (existingProfile == null) {
                    ProfilesTable.insert {
                        it[ProfilesTable.userId]   = userId
                        it[fullName]               = user.fullName
                        it[bio]                    = user.bio
                        it[city]                   = user.city
                        it[avatarUrl]              = null
                        it[availability]           = user.availability
                        it[updatedAt]              = LocalDateTime.now()
                    }
                } else {
                    ProfilesTable.update({ ProfilesTable.userId eq userId }) {
                        it[fullName]     = user.fullName
                        it[bio]          = user.bio
                        it[city]         = user.city
                        it[availability] = user.availability
                        it[updatedAt]    = LocalDateTime.now()
                    }
                }

                // Xóa kỹ năng cũ để tránh trùng lặp
                UserSkillsTable.deleteWhere { UserSkillsTable.userId eq userId }

                // Chèn các kỹ năng HAVE và WANT
                user.skills.forEach { skill ->
                    // Tìm skillId theo tên, fallback tìm kiếm mờ nếu tên có chút khác biệt
                    val skillId = skillsMap[skill.name]
                        ?: skillsMap.entries.firstOrNull { it.key.contains(skill.name, ignoreCase = true) }?.value
                        ?: skillsMap.values.first()

                    UserSkillsTable.insert {
                        it[UserSkillsTable.userId] = userId
                        it[UserSkillsTable.skillId] = skillId
                        it[type] = skill.type
                        it[proficiencyLevel] = skill.level
                        it[note] = if (skill.type == "HAVE") "Kinh nghiệm thực tế" else "Mong muốn học hỏi"
                        it[createdAt] = LocalDateTime.now()
                    }
                }
            }

            println("OK")
            successCount++
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
        }
    }

    println("\n==================================================")
    println("Hoàn tất! Đã tạo thành công $successCount/${seedUsers.size} người dùng demo.")
    println("Mật khẩu dùng chung cho tất cả tài khoản: ${DemoDataGenerator.DEMO_PASSWORD}")
    println("5 tài khoản kịch bản chính:")
    println("  - sender${DemoDataGenerator.DEMO_DOMAIN}    (Dạy Kotlin Android, Cần Python - Hà Nội)")
    println("  - receiver${DemoDataGenerator.DEMO_DOMAIN}  (Dạy Python, Cần Kotlin Android - Hà Nội)")
    println("  - uiux${DemoDataGenerator.DEMO_DOMAIN}      (Dạy Figma, Cần Tiếng Anh - TP.HCM)")
    println("  - lang${DemoDataGenerator.DEMO_DOMAIN}      (Dạy Tiếng Anh, Cần Guitar - Đà Nẵng)")
    println("  - music${DemoDataGenerator.DEMO_DOMAIN}     (Dạy Guitar, Cần Figma - Hà Nội)")
    println("==================================================")
}
