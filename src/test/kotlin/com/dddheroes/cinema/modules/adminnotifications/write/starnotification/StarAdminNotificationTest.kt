package com.dddheroes.cinema.modules.adminnotifications.write.starnotification

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationSent
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationStarred
import com.dddheroes.cinema.modules.adminnotifications.write.starnotification.StarAdminNotification
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["slices.adminnotifications.write.starnotification.enabled=true"])
class StarAdminNotificationTest : MessagingSpringBootTest() {

    @Test
    fun `starring sent notification - success`() {
        // given
        val notificationId = NotificationId.random()
        val now = currentTime()
        val events = listOf(
            NotificationSent(notificationId, "Test notification", now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(StarAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val expectedEvent = NotificationStarred(notificationId, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `starring already starred notification - nothing`() {
        // given
        val notificationId = NotificationId.random()
        val now = currentTime()
        val events = listOf(
            NotificationSent(notificationId, "Test notification", now),
            NotificationStarred(notificationId, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(StarAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `starring non-sent notification - failure`() {
        // given - no events, notification not sent yet

        // when
        val now = currentTime()
        val notificationId = NotificationId.random()
        val result = executeCommand(StarAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Failure("Notification must be sent before it can be starred")
        assertThat(result).isEqualTo(expectedResult)
    }
}