package com.skillexchange.api.config

import java.io.File

/**
 * EnvLoader — Tự động đọc file .env ở thư mục gốc khi chạy Local,
 * và ưu tiên System.getenv() khi deploy Production (Render / Docker).
 */
object EnvLoader {
    private val envMap: Map<String, String> by lazy {
        val envFile = File(".env")
        if (envFile.exists()) {
            envFile.readLines()
                .filter { line -> line.isNotBlank() && !line.trim().startsWith("#") && line.contains("=") }
                .associate { line ->
                    val idx = line.indexOf('=')
                    val key = line.substring(0, idx).trim()
                    val value = line.substring(idx + 1).trim()
                    key to value
                }
        } else {
            emptyMap()
        }
    }

    fun get(key: String, defaultValue: String = ""): String {
        val sysValue = System.getenv(key)
        if (!sysValue.isNullOrBlank()) return sysValue
        val localValue = envMap[key]
        if (!localValue.isNullOrBlank()) return localValue
        return defaultValue
    }
}
