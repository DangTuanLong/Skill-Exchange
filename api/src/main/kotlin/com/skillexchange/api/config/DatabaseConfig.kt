package com.skillexchange.api.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object DatabaseConfig {

    data class DatabaseConnectionInfo(
        val jdbcUrl: String,
        val username: String?,
        val password: String?
    )

    private val dbUriRegex = Regex("""^(?:jdbc:)?postgres(?:ql)?://(?:([^:]+)(?::([^@]+))?@)?([^:/?#]+)(?::(\d+))?(/[^?#]*)?(?:\?(.*))?""")

    fun parseDatabaseUrl(
        rawUrl: String,
        defaultUser: String = "postgres",
        defaultPassword: String = ""
    ): DatabaseConnectionInfo {
        val trimmed = rawUrl.trim()
        val match = dbUriRegex.find(trimmed)

        if (match != null) {
            val userGroup = match.groups[1]?.value
            val passGroup = match.groups[2]?.value
            val host = match.groups[3]?.value ?: "localhost"
            val port = match.groups[4]?.value ?: "5432"
            val path = match.groups[5]?.value?.takeIf { it.isNotBlank() } ?: "/postgres"
            val query = match.groups[6]?.value

            val username = if (userGroup != null) {
                URLDecoder.decode(userGroup, StandardCharsets.UTF_8)
            } else {
                defaultUser.ifBlank { null }
            }

            val password = if (passGroup != null) {
                URLDecoder.decode(passGroup, StandardCharsets.UTF_8)
            } else {
                defaultPassword
            }

            val queryPart = if (!query.isNullOrBlank()) "?$query" else ""
            val jdbcUrl = "jdbc:postgresql://$host:$port$path$queryPart"

            return DatabaseConnectionInfo(
                jdbcUrl = jdbcUrl,
                username = username,
                password = password
            )
        }

        // Fallback for non-matching strings
        val jdbcUrl = when {
            trimmed.startsWith("jdbc:") -> trimmed
            trimmed.startsWith("postgresql://") -> "jdbc:$trimmed"
            trimmed.startsWith("postgres://") -> "jdbc:postgresql://" + trimmed.removePrefix("postgres://")
            else -> "jdbc:postgresql://$trimmed"
        }

        return DatabaseConnectionInfo(
            jdbcUrl = jdbcUrl,
            username = defaultUser.ifBlank { null },
            password = defaultPassword
        )
    }

    fun init() {
        val rawUrl = EnvLoader.get(
            "DATABASE_URL",
            "jdbc:postgresql://localhost:5432/postgres"
        )
        val envUser = EnvLoader.get("DB_USER", "postgres")
        val envPass = EnvLoader.get("DB_PASSWORD", "")

        val parsed = parseDatabaseUrl(
            rawUrl = rawUrl,
            defaultUser = envUser,
            defaultPassword = envPass
        )

        val config = HikariConfig().apply {
            // Đọc từ environment variables
            // Hỗ trợ cả định dạng URI tiêu chuẩn (postgresql://user:pass@host:port/db) và JDBC URL
            jdbcUrl  = parsed.jdbcUrl
            driverClassName = "org.postgresql.Driver"
            username = parsed.username ?: envUser
            password = parsed.password ?: envPass

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
