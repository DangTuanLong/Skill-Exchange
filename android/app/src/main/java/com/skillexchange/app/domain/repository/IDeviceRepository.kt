package com.skillexchange.app.domain.repository

interface IDeviceRepository {
    suspend fun registerDevice(token: String, deviceInfo: String? = null): Result<Unit>
    suspend fun unregisterDevice(token: String): Result<Unit>
}
