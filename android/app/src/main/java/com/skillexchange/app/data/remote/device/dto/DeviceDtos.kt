package com.skillexchange.app.data.remote.device.dto

import kotlinx.serialization.Serializable

@Serializable
data class RegisterDeviceRequestDto(
    val token: String,
    val deviceInfo: String? = null
)

@Serializable
data class DeleteDeviceRequestDto(
    val token: String
)

@Serializable
data class DeviceResponseDto(
    val token: String,
    val message: String
)
