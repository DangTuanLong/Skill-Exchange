package com.skillexchange.app.data.remote.device

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.data.remote.auth.ApiWrapper
import com.skillexchange.app.data.remote.device.dto.DeleteDeviceRequestDto
import com.skillexchange.app.data.remote.device.dto.DeviceResponseDto
import com.skillexchange.app.data.remote.device.dto.RegisterDeviceRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class DeviceRemoteDataSource(private val client: HttpClient) {

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            val body = response.body<ApiWrapper<T>>()
            return body.data ?: throw Exception(body.message ?: "Dữ liệu trả về rỗng")
        } else {
            val errorBody = runCatching { response.body<com.skillexchange.app.core.network.ApiErrorDto>() }.getOrNull()
            throw com.skillexchange.app.core.network.ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun registerDevice(dto: RegisterDeviceRequestDto): DeviceResponseDto {
        val response = client.post("${Constants.BASE_URL}/api/devices") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        return handleResponse(response)
    }

    suspend fun unregisterDevice(dto: DeleteDeviceRequestDto): DeviceResponseDto {
        val response = client.delete("${Constants.BASE_URL}/api/devices") {
            contentType(ContentType.Application.Json)
            setBody(dto)
        }
        return handleResponse(response)
    }
}
