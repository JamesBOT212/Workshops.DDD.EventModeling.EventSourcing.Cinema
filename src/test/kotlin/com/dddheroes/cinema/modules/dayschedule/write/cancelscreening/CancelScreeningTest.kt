package com.dddheroes.cinema.modules.dayschedule.write.cancelscreening

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningCancelled
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalTime

@TestPropertySource(properties = ["slices.dayschedule.write.cancelscreening.enabled=true"])
class CancelScreeningTest : MessagingSpringBootTest() {

    @Test
    fun `cancelling existing screening - success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val screeningId = aScreeningId()
        val movieId = aMovieId()
        val startTime = LocalTime.of(20, 0)
        val endTime = LocalTime.of(22, 30)
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, screeningId, movieId, startTime, endTime, now)
        )

        // when
        val result = executeCommand(CancelScreening(dayScheduleId, screeningId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = ScreeningCancelled(dayScheduleId, screeningId, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `cancelling one of multiple screenings - success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(15, 0), LocalTime.of(17, 30), now),
            ScreeningScheduled(dayScheduleId, secondScreeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now)
        )

        // when - cancel the first screening
        val result = executeCommand(CancelScreening(dayScheduleId, firstScreeningId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = ScreeningCancelled(dayScheduleId, firstScreeningId, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `cancelling screening without day schedule - failure`() {
        // given - no events, day schedule is not created
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val screeningId = aScreeningId()

        // when
        val result = executeCommand(CancelScreening(dayScheduleId, screeningId, now))

        // then
        val expectedResult = CommandResult.Failure("Day schedule with id $dayScheduleId does not exist")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `cancelling non-existent screening - idempotent success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val existingScreeningId = aScreeningId()
        val nonExistentScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, existingScreeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now)
        )

        // when
        val result = executeCommand(CancelScreening(dayScheduleId, nonExistentScreeningId, now))

        // then - should succeed but generate no events (idempotent)
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)
        
        // then - verify no new events were generated
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(
                DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
                ScreeningScheduled(dayScheduleId, existingScreeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now)
            )
        )
    }

    @Test
    fun `cancelling already cancelled screening - idempotent success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val screeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, screeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now),
            ScreeningCancelled(dayScheduleId, screeningId, now)
        )

        // when - try to cancel already cancelled screening
        val result = executeCommand(CancelScreening(dayScheduleId, screeningId, now))

        // then - should succeed but generate no new events (idempotent)
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)
        
        // then - verify no new events were generated beyond the original cancellation
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(
                DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
                ScreeningScheduled(dayScheduleId, screeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now),
                ScreeningCancelled(dayScheduleId, screeningId, now)
            )
        )
    }

    @Test
    fun `cancelling screening in day schedule with only that screening - success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val screeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, screeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now)
        )

        // when
        val result = executeCommand(CancelScreening(dayScheduleId, screeningId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - verify the stream contains all expected events
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val allEvents = streamEvents(streamId)
        assertThat(allEvents).isEqualTo(
            listOf(
                DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
                ScreeningScheduled(dayScheduleId, screeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now),
                ScreeningCancelled(dayScheduleId, screeningId, now)
            )
        )
    }

    @Test
    fun `cancelling multiple screenings sequentially - success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val thirdScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(12, 0), LocalTime.of(14, 30), now),
            ScreeningScheduled(dayScheduleId, secondScreeningId, movieId, LocalTime.of(15, 0), LocalTime.of(17, 30), now),
            ScreeningScheduled(dayScheduleId, thirdScreeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now)
        )

        // when - cancel first screening
        val firstResult = executeCommand(CancelScreening(dayScheduleId, firstScreeningId, now))
        
        // then
        assertThat(firstResult).isEqualTo(CommandResult.Success)

        // when - cancel third screening
        val secondResult = executeCommand(CancelScreening(dayScheduleId, thirdScreeningId, now))
        
        // then
        assertThat(secondResult).isEqualTo(CommandResult.Success)

        // when - try to cancel second screening (still exists)
        val thirdResult = executeCommand(CancelScreening(dayScheduleId, secondScreeningId, now))
        
        // then
        assertThat(thirdResult).isEqualTo(CommandResult.Success)
    }

    private fun aDayScheduleId() = DayScheduleId.random()
    private fun aScreeningId() = ScreeningId.random()
    private fun aMovieId() = MovieId.random()
}