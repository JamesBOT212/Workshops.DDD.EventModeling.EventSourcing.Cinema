package com.dddheroes.cinema.modules.adminnotifications.events

import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.shared.events.CinemaEvent
import com.dddheroes.sdk.domain.EventStreamId
import java.time.Instant

sealed interface AdminNotificationEvent : CinemaEvent {
    val notificationId: NotificationId

    override val streamId: EventStreamId
        get() = EventStreamId.of("AdminNotification", notificationId)
}

data class NotificationSent(
    override val notificationId: NotificationId,
    val content: String,
    override val occurredAt: Instant,
) : AdminNotificationEvent

data class NotificationStarred(
    override val notificationId: NotificationId,
    override val occurredAt: Instant,
) : AdminNotificationEvent

data class NotificationRead(
    override val notificationId: NotificationId,
    override val occurredAt: Instant,
) : AdminNotificationEvent