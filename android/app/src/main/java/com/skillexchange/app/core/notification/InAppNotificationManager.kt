package com.skillexchange.app.core.notification

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class InAppNotificationData(
    val title: String,
    val body: String,
    val type: String?,
    val entityId: String?
)

object InAppNotificationManager {

    private val _notificationFlow = MutableSharedFlow<InAppNotificationData>(
        extraBufferCapacity = 1,
        replay = 0
    )
    val notificationFlow: SharedFlow<InAppNotificationData> = _notificationFlow.asSharedFlow()

    fun showNotification(notification: InAppNotificationData) {
        _notificationFlow.tryEmit(notification)
    }
}
