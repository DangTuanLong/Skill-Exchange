package com.skillexchange.api

import com.skillexchange.api.config.DatabaseConfig
import com.skillexchange.api.models.db.ProfilesTable
import com.skillexchange.api.models.db.SkillCategoriesTable
import com.skillexchange.api.models.db.SkillsTable
import com.skillexchange.api.models.db.UserSkillsTable
import com.skillexchange.api.plugins.*
import com.skillexchange.api.services.SkillService
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main() {
    val port = System.getenv("PORT")?.toInt() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }

    configureSerialization()
    configureCORS()
    configureMonitoring()
    configureStatusPages()
    configureAuth()
    configureRouting()

    // Database
    DatabaseConfig.init()
    initDatabase()
}

fun Application.initDatabase() {
    val createTables = System.getenv("DB_CREATE_TABLES")?.toBoolean() ?: false
    if (createTables) {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                ProfilesTable,
                SkillCategoriesTable,
                SkillsTable,
                UserSkillsTable
            )
        }
    }
    // Seed skill categories nếu chưa có
    val skillService: SkillService by inject()
    skillService.seedIfEmpty()

    // Đồng bộ phòng chat Firestore cho các trao đổi đã chấp nhận
    try {
        val exchangeService: com.skillexchange.api.services.exchange.ExchangeService by inject()
        exchangeService.syncExistingAcceptedExchanges()
    } catch (e: Exception) {
        // Không làm gián đoạn khởi động máy chủ nếu mạng lỗi
    }
}
