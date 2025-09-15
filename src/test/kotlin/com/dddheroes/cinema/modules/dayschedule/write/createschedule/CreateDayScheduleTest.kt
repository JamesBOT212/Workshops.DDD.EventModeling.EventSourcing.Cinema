package com.dddheroes.cinema.modules.dayschedule.write.createschedule

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalTime

@TestPropertySource(properties = ["slices.dayschedule.write.createschedule.enabled=true"])
class CreateDayScheduleTest : MessagingSpringBootTest() {

    @Test
    fun `creating day schedule for the first time - success`() {
        // given
        val now = currentTime()
        val dayScheduleId = aDayScheduleId()
        val openingTime = LocalTime.of(9, 0)
        val closingTime = LocalTime.of(21, 0)

        // when
        val result = executeCommand(CreateDaySchedule(dayScheduleId, openingTime, closingTime, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = DayScheduleCreated(dayScheduleId, openingTime, closingTime, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `creating day schedule with different times - success`() {
        // given
        val now = currentTime()
        val dayScheduleId = aDayScheduleId()
        val openingTime = LocalTime.of(8, 30)
        val closingTime = LocalTime.of(23, 30)

        // when
        val result = executeCommand(CreateDaySchedule(dayScheduleId, openingTime, closingTime, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = DayScheduleCreated(dayScheduleId, openingTime, closingTime, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `creating day schedule with midnight opening - success`() {
        // given
        val now = currentTime()
        val dayScheduleId = aDayScheduleId()
        val openingTime = LocalTime.MIDNIGHT
        val closingTime = LocalTime.of(23, 59)

        // when
        val result = executeCommand(CreateDaySchedule(dayScheduleId, openingTime, closingTime, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = DayScheduleCreated(dayScheduleId, openingTime, closingTime, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `creating day schedule twice with same data - idempotent success`() {
        // given
        val now = currentTime()
        val dayScheduleId = aDayScheduleId()
        val openingTime = LocalTime.of(9, 0)
        val closingTime = LocalTime.of(21, 0)

        eventsOccurred(DayScheduleCreated(dayScheduleId, openingTime, closingTime, now))

        // when - attempt to create the same schedule again
        val result = executeCommand(CreateDaySchedule(dayScheduleId, openingTime, closingTime, now))

        // then - should succeed but generate no new events (idempotent)
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - verify no new events were generated
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(DayScheduleCreated(dayScheduleId, openingTime, closingTime, now))
        )
    }

    @Test
    fun `creating day schedule twice with different times - idempotent success with original times`() {
        // given
        val now = currentTime()
        val dayScheduleId = aDayScheduleId()
        val originalOpeningTime = LocalTime.of(9, 0)
        val originalClosingTime = LocalTime.of(21, 0)
        val newOpeningTime = LocalTime.of(10, 0)
        val newClosingTime = LocalTime.of(22, 0)

        eventsOccurred(DayScheduleCreated(dayScheduleId, originalOpeningTime, originalClosingTime, now))

        // when - attempt to create schedule with different times
        val result = executeCommand(CreateDaySchedule(dayScheduleId, newOpeningTime, newClosingTime, now))

        // then - should succeed but generate no new events (idempotent behavior)
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - verify no new events were generated and original times are preserved
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(DayScheduleCreated(dayScheduleId, originalOpeningTime, originalClosingTime, now))
        )
    }

    @Test
    fun `creating multiple different day schedules - success`() {
        // given
        val now = currentTime()
        val firstDayScheduleId = aDayScheduleId()
        val secondDayScheduleId = aDayScheduleId()
        val openingTime = LocalTime.of(9, 0)
        val closingTime = LocalTime.of(21, 0)

        // when - create first day schedule
        val firstResult = executeCommand(CreateDaySchedule(firstDayScheduleId, openingTime, closingTime, now))

        // then
        assertThat(firstResult).isEqualTo(CommandResult.Success)

        // when - create second day schedule
        val secondResult = executeCommand(CreateDaySchedule(secondDayScheduleId, openingTime, closingTime, now))

        // then
        assertThat(secondResult).isEqualTo(CommandResult.Success)

        // then - verify both schedules have their own events
        val firstStreamId = EventStreamId.of("DaySchedule", firstDayScheduleId)
        val secondStreamId = EventStreamId.of("DaySchedule", secondDayScheduleId)

        val firstExpectedEvent = DayScheduleCreated(firstDayScheduleId, openingTime, closingTime, now)
        val secondExpectedEvent = DayScheduleCreated(secondDayScheduleId, openingTime, closingTime, now)

        assertLastStreamEvent(firstStreamId, firstExpectedEvent)
        assertLastStreamEvent(secondStreamId, secondExpectedEvent)
    }

    @Test
    fun `creating day schedule with same opening and closing time - success`() {
        // given
        val now = currentTime()
        val dayScheduleId = aDayScheduleId()
        val time = LocalTime.of(12, 0)

        // when
        val result = executeCommand(CreateDaySchedule(dayScheduleId, time, time, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = DayScheduleCreated(dayScheduleId, time, time, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    private fun aDayScheduleId() = DayScheduleId.random()
}