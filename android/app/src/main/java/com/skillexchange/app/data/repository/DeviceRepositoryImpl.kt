package com.skillexchange.app.data.repository

import com.skillexchange.app.data.remote.device.DeviceRemoteDataSource
import com.skillexchange.app.data.remote.device.dto.DeleteDeviceRequestDto
import com.skillexchange.app.data.remote.device.dto.RegisterDeviceRequestDto
import com.skillexchange.app.domain.repository.IDeviceRepository

class DeviceRepositoryImpl(
    private val remoteDataSource: DeviceRemoteDataSource
) : IDeviceRepository {

    override suspend fun registerDevice(token: String, deviceInfo: String?): Result<Unit> = runCatching {
        remoteDataSource.registerDevice(RegisterDeviceRequestDto(token = token, deviceInfo = deviceInfo))
        Unit
    }

    override suspend fun unregisterDevice(token: String): Result<Unit> = runCatching {
        remoteDataSource.unregisterDevice(DeleteDeviceRequestDto(token = token))
        Unit
    }
}
