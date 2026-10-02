package com.skillexchange.api.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

object DatabaseConfig {
    fun init() {
        val config = HikariConfig().apply {
            // Đọc từ environment variables
            // Local: set từ .env, Production: inject bởi Render
            jdbcUrl  = EnvLoader.get(
                "DATABASE_URL",
                "jdbc:postgresql://localhost:5432/postgres"
            )
            driverClassName = "org.postgresql.Driver"
            username = EnvLoader.get("DB_USER", "postgres")
            password = EnvLoader.get("DB_PASSWORD", "")

            // Connection pool tối ưu cho Render Free (512MB RAM)
            maximumPoolSize = 5
            minimumIdle     = 2
            idleTimeout     = 300_000    // 5 phút
            connectionTimeout = 20_000   // 20 giây
            maxLifetime     = 600_000    // 10 phút

            // Verify connection khi lấy từ pool
            connectionTestQuery = "SELECT 1"
        }

        Database.connect(HikariDataSource(config))
    }
}
