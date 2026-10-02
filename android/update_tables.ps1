$kotlinCode = @"
package com.skillexchange.api.models.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object ProfilesTable : Table("profiles") {
    val id        = uuid("id").autoGenerate()
    val userId    = uuid("user_id").transform({ it.toString() }, { java.util.UUID.fromString(it) }).uniqueIndex()
    val fullName  = varchar("full_name", 255)
    val bio       = text("bio").nullable()
    val city      = varchar("city", 100).nullable()
    val avatarUrl = varchar("avatar_url", 500).nullable()
    val updatedAt = datetime("updated_at").default(LocalDateTime.now())
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
"@

[System.IO.File]::WriteAllText('$PSScriptRoot\..\api\src\main\kotlin\com\skillexchange\api\models\db\Tables.kt', $kotlinCode, [System.Text.Encoding]::UTF8)
Write-Host "Updated Tables.kt successfully"
