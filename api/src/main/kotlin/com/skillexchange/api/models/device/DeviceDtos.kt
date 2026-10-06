package com.skillexchange.api.models.device

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequest(
    val token: String,
    val deviceInfo: String? = null
)

@Serializable
data class DeleteDeviceRequest(
    val token: String
)

@Serializable
data class DeviceResponse(
    val token: String,
    val message: String
)
