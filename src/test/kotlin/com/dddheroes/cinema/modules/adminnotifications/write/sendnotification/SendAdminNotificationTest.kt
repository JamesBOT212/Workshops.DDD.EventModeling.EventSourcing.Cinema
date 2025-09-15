package com.dddheroes.cinema.modules.adminnotifications.write.sendnotification

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationSent
import com.dddheroes.cinema.modules.adminnotifications.write.sendnotification.SendAdminNotification
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.adminnotifications.write.sendnotification.enabled=true"])
class SendAdminNotificationTest : MessagingSpringBootTest() {

    @Test
    fun `sending notification (1st time) - success`() {
        // given - no events, notification not sent yet

        // when
        val notificationId = NotificationId.random()
        val content = aNotificationContent()
        val now = currentTime()
        val result = executeCommand(SendAdminNotification(notificationId, content, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val expectedEvent = NotificationSent(notificationId, content, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `sending same notification again - nothing`() {
        // given
        val notificationId = NotificationId.random()
        val content = aNotificationContent()
        val now = currentTime()
        val events = listOf(
            NotificationSent(notificationId, content, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(SendAdminNotification(notificationId, content, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `sending same notification with different content - nothing`() {
        // given
        val notificationId = NotificationId.random()
        val originalContent = aNotificationContent()
        val now = currentTime()
        val events = listOf(
            NotificationSent(notificationId, originalContent, now)
        )
        eventsOccurred(events)

        // when
        val differentContent = aNotificationContent()
        val result = executeCommand(SendAdminNotification(notificationId, differentContent, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        assertStreamEvents(streamId, events)
    }

    fun aNotificationContent() = "System alert: ${UUID.randomUUID()}"
}