package com.skillexchange.api.models.db

import com.skillexchange.api.models.profile.AvailabilityWindowDto
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.json.jsonb
import java.time.LocalDateTime

object ProfilesTable : Table("profiles") {
    val id           = uuid("id").autoGenerate()
    val userId       = uuid("user_id").transform({ it.toString() }, { java.util.UUID.fromString(it) }).uniqueIndex()
    val fullName     = varchar("full_name", 255)
    val bio          = text("bio").nullable()
    val city         = varchar("city", 100).nullable()
    val avatarUrl    = varchar("avatar_url", 500).nullable()
    val availability = jsonb<List<AvailabilityWindowDto>>("availability", Json.Default).default(emptyList())
    val updatedAt    = datetime("updated_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}


object SkillCategoriesTable : Table("skill_categories") {
    val id          = integer("id").autoIncrement()
    val name        = varchar("name", 100).uniqueIndex()
    val icon        = varchar("icon", 50)
    val description = varchar("description", 300).nullable()
    override val primaryKey = PrimaryKey(id)
}

object SkillsTable : Table("skills") {
    val id          = integer("id").autoIncrement()
    val categoryId  = integer("category_id").references(SkillCategoriesTable.id)
    val name        = varchar("name", 150)
    val description = varchar("description", 300).nullable()
    override val primaryKey = PrimaryKey(id)
}

object UserSkillsTable : Table("user_skills") {
    val id               = uuid("id").autoGenerate()
    val userId           = uuid("user_id").transform({ it.toString() }, { java.util.UUID.fromString(it) })
    val skillId          = integer("skill_id").references(SkillsTable.id)
    val type             = varchar("type", 10)
    val proficiencyLevel = integer("proficiency_level").default(1)
    val note             = text("note").nullable()
    val createdAt        = datetime("created_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

object ExchangeRequestsTable : Table("exchange_requests") {
    val id                  = uuid("id").autoGenerate()
    val senderId            = uuid("sender_id").transform({ it.toString() }, { java.util.UUID.fromString(it) })
    val receiverId          = uuid("receiver_id").transform({ it.toString() }, { java.util.UUID.fromString(it) })
    val skillOfferedId      = integer("skill_offered_id").references(SkillsTable.id)
    val skillWantedId       = integer("skill_wanted_id").references(SkillsTable.id)
    val status              = varchar("status", 20).default("PENDING")
    val durationMinutes     = integer("duration_minutes").default(60)
    val meetingMode         = varchar("meeting_mode", 20).default("UNDECIDED")
    val message             = text("message").nullable()
    val cancellationReason  = text("cancellation_reason").nullable()
    val scheduledAt         = datetime("scheduled_at")
    val senderCompletedAt   = datetime("sender_completed_at").nullable()
    val receiverCompletedAt = datetime("receiver_completed_at").nullable()
    val createdAt           = datetime("created_at").default(LocalDateTime.now())
    val updatedAt           = datetime("updated_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}

object FcmTokensTable : Table("fcm_tokens") {
    val id         = uuid("id").autoGenerate()
    val userId     = uuid("user_id").transform({ it.toString() }, { java.util.UUID.fromString(it) })
    val token      = text("token").uniqueIndex()
    val deviceInfo = varchar("device_info", 200).nullable()
    val createdAt  = datetime("created_at").default(LocalDateTime.now())
    val updatedAt  = datetime("updated_at").default(LocalDateTime.now())
    override val primaryKey = PrimaryKey(id)
}