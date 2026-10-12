package com.skillexchange.app.data.remote.notification

import com.skillexchange.app.core.common.Constants
import com.skillexchange.app.core.network.ApiErrorDto
import com.skillexchange.app.core.network.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

class NotificationRemoteDataSource(private val client: HttpClient) {

    private suspend inline fun <reified T> handleResponse(response: HttpResponse): T {
        if (response.status.isSuccess()) {
            return response.body<T>()
        } else {
            val errorBody = runCatching { response.body<ApiErrorDto>() }.getOrNull()
            throw ApiException.fromResponse(response.status.value, errorBody)
        }
    }

    suspend fun getNotifications(page: Int = 1, limit: Int = 30): NotificationListResponseDto {
        val response = client.get("${Constants.BASE_URL}/api/notifications") {
            parameter("page", page)
            parameter("limit", limit)
        }
        return handleResponse(response)
    }

    suspend fun markAsRead(notificationId: String): MarkReadResponseDto {
        val response = client.put("${Constants.BASE_URL}/api/notifications/$notificationId/read")
        return handleResponse(response)
    }

    suspend fun markAllAsRead(): MarkReadResponseDto {
        val response = client.put("${Constants.BASE_URL}/api/notifications/read-all")
        return handleResponse(response)
    }
}
