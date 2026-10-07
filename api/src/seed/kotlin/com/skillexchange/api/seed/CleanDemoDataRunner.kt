package com.skillexchange.api.seed

import com.skillexchange.api.config.DatabaseConfig
import com.skillexchange.api.config.EnvLoader
import com.skillexchange.api.models.db.ProfilesTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI
import kotlin.system.exitProcess

fun main() {
    println("==================================================")
    println("SkillExchange — Clean Demo Data (TASK-013)")
    println("==================================================")

    val supabaseUrl = EnvLoader.get("SUPABASE_URL", "")
    val serviceKey = EnvLoader.get("SUPABASE_SERVICE_KEY", "")

    if (supabaseUrl.isBlank() || serviceKey.isBlank()) {
        System.err.println("[ERROR] Chưa cấu hình SUPABASE_URL hoặc SUPABASE_SERVICE_KEY trong môi trường hoặc .env.")
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
            
            [ABORTED] Chưa xác nhận dọn dẹp demo data!
            Để tránh xóa nhầm trên môi trường không mong muốn, bạn cần thiết lập biến SEED_CONFIRM=yes.
            Ví dụ chạy qua PowerShell:
              ${'$'}env:SEED_CONFIRM="yes"; .\gradlew cleanDemoData
            Hoặc thêm dòng sau vào api/.env:
              SEED_CONFIRM=yes
        """.trimIndent())
        exitProcess(1)
    }

    val adminClient = SupabaseAdminClient(supabaseUrl, serviceKey)

    println("\n[1/3] Đang tìm kiếm các tài khoản có đuôi ${DemoDataGenerator.DEMO_DOMAIN}...")
    val allUsers = adminClient.listAllUsers()
    val demoUsers = allUsers.filter { it.email?.endsWith(DemoDataGenerator.DEMO_DOMAIN, ignoreCase = true) == true }

    println("Tìm thấy ${demoUsers.size} tài khoản demo cần xóa.")

    if (demoUsers.isEmpty()) {
        println("Không có tài khoản demo nào để dọn dẹp.")
        return
    }

    println("[2/3] Bắt đầu xóa tài khoản qua Supabase Admin API (tự động xóa cascade sang database)...")
    var deletedCount = 0
    for ((index, user) in demoUsers.withIndex()) {
        print("  (${index + 1}/${demoUsers.size}) Đang xóa: ${user.email} (${user.id})... ")
        val success = adminClient.deleteUser(user.id)
        if (success) {
            println("OK")
            deletedCount++
        } else {
            println("FAILED")
        }
    }

    println("[3/3] Xác minh dọn dẹp cascade trên Database...")
    DatabaseConfig.init()
    val remainingProfiles = transaction {
        ProfilesTable.selectAll().count()
    }

    println("\n==================================================")
    println("Hoàn tất dọn dẹp!")
    println("Đã xóa: $deletedCount/${demoUsers.size} tài khoản auth.")
    println("Tổng số profile còn lại trong hệ thống: $remainingProfiles")
    println("==================================================")
}
