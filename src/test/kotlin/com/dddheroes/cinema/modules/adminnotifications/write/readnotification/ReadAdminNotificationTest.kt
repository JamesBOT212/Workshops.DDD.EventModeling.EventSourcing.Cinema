package com.dddheroes.cinema.modules.adminnotifications.write.readnotification

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationRead
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationSent
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationStarred
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["slices.adminnotifications.write.readnotification.enabled=true"])
class ReadAdminNotificationTest : MessagingSpringBootTest() {

    @Test
    fun `reading sent notification for the first time - success`() {
        // given
        val notificationId = aNotificationId()
        val now = currentTime()
        val content = "Test notification content"

        eventsOccurred(NotificationSent(notificationId, content, now))

        // when
        val result = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val expectedEvent = NotificationRead(notificationId, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `reading starred notification - success`() {
        // given
        val notificationId = aNotificationId()
        val now = currentTime()
        val content = "Test notification content"

        eventsOccurred(
            NotificationSent(notificationId, content, now),
            NotificationStarred(notificationId, now)
        )

        // when
        val result = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val expectedEvent = NotificationRead(notificationId, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `reading already read notification - generates another read event`() {
        // given
        val notificationId = aNotificationId()
        val now = currentTime()
        val content = "Test notification content"

        eventsOccurred(
            NotificationSent(notificationId, content, now),
            NotificationRead(notificationId, now)
        )

        // when
        val result = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - verify another read event is generated (not idempotent)
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(
                NotificationSent(notificationId, content, now),
                NotificationRead(notificationId, now),
                NotificationRead(notificationId, now)
            )
        )
    }

    @Test
    fun `reading notification multiple times - generates multiple read events`() {
        // given
        val notificationId = aNotificationId()
        val now = currentTime()
        val content = "Test notification content"

        eventsOccurred(NotificationSent(notificationId, content, now))

        // when - read first time
        val firstResult = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        assertThat(firstResult).isEqualTo(CommandResult.Success)

        // when - read second time
        val secondResult = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        assertThat(secondResult).isEqualTo(CommandResult.Success)

        // when - read third time
        val thirdResult = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        assertThat(thirdResult).isEqualTo(CommandResult.Success)

        // then - verify all read events are stored
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(
                NotificationSent(notificationId, content, now),
                NotificationRead(notificationId, now),
                NotificationRead(notificationId, now),
                NotificationRead(notificationId, now)
            )
        )
    }

    @Test
    fun `reading non-existent notification - failure`() {
        // given - no events, notification was never sent
        val notificationId = aNotificationId()
        val now = currentTime()

        // when
        val result = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Failure("Notification must be sent before it can be read")
        assertThat(result).isEqualTo(expectedResult)

        // then - verify no events were generated
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(emptyList())
    }

    @Test
    fun `reading notification after it was starred and read - success`() {
        // given
        val notificationId = aNotificationId()
        val now = currentTime()
        val content = "Test notification content"

        eventsOccurred(
            NotificationSent(notificationId, content, now),
            NotificationStarred(notificationId, now),
            NotificationRead(notificationId, now)
        )

        // when
        val result = executeCommand(ReadAdminNotification(notificationId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - verify another read event is added
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(
                NotificationSent(notificationId, content, now),
                NotificationStarred(notificationId, now),
                NotificationRead(notificationId, now),
                NotificationRead(notificationId, now)
            )
        )
    }

    @Test
    fun `reading notification with only starred event without sent - failure`() {
        // given - notification was somehow starred without being sent (edge case)
        val notificationId = aNotificationId()
        val now = currentTime()

        eventsOccurred(NotificationStarred(notificationId, now))

        // when
        val result = executeCommand(ReadAdminNotification(notificationId, now))

        // then - should fail because notification must be sent first
        val expectedResult = CommandResult.Failure("Notification must be sent before it can be read")
        assertThat(result).isEqualTo(expectedResult)

        // then - verify no new events were generated
        val streamId = EventStreamId.of("AdminNotification", notificationId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(NotificationStarred(notificationId, now))
        )
    }

    @Test
    fun `reading different notifications independently - success`() {
        // given
        val firstNotificationId = aNotificationId()
        val secondNotificationId = aNotificationId()
        val now = currentTime()
        val content1 = "First notification"
        val content2 = "Second notification"

        eventsOccurred(
            NotificationSent(firstNotificationId, content1, now),
            NotificationSent(secondNotificationId, content2, now)
        )

        // when - read first notification
        val firstResult = executeCommand(ReadAdminNotification(firstNotificationId, now))

        // then
        assertThat(firstResult).isEqualTo(CommandResult.Success)

        // when - read second notification
        val secondResult = executeCommand(ReadAdminNotification(secondNotificationId, now))

        // then
        assertThat(secondResult).isEqualTo(CommandResult.Success)

        // then - verify events are in correct streams
        val firstStreamId = EventStreamId.of("AdminNotification", firstNotificationId)
        val secondStreamId = EventStreamId.of("AdminNotification", secondNotificationId)

        val firstExpectedEvents = listOf(
            NotificationSent(firstNotificationId, content1, now),
            NotificationRead(firstNotificationId, now)
        )
        val secondExpectedEvents = listOf(
            NotificationSent(secondNotificationId, content2, now),
            NotificationRead(secondNotificationId, now)
        )

        assertStreamEvents(firstStreamId, firstExpectedEvents)
        assertStreamEvents(secondStreamId, secondExpectedEvents)
    }

    private fun aNotificationId() = NotificationId.random()
}