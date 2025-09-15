package com.dddheroes.cinema.modules.dayschedule.write.schedulescreening

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalTime

@TestPropertySource(properties = ["slices.dayschedule.write.schedulescreening.enabled=true"])
class ScheduleScreeningTest : MessagingSpringBootTest() {

    @Test
    fun `scheduling screening (1st time) - success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val screeningId = aScreeningId()
        val movieId = aMovieId()
        val startTime = LocalTime.of(20, 0)
        val endTime = LocalTime.of(22, 30)
        
        eventsOccurred(DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now))

        // when
        val result = executeCommand(ScheduleScreening(dayScheduleId, screeningId, movieId, startTime, endTime, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = ScreeningScheduled(dayScheduleId, screeningId, movieId, startTime, endTime, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `scheduling multiple non-overlapping screenings - success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(15, 0), LocalTime.of(17, 30), now)
        )

        // when
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                secondScreeningId,
                movieId,
                LocalTime.of(20, 0),
                LocalTime.of(22, 30),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = ScreeningScheduled(dayScheduleId, secondScreeningId, movieId, LocalTime.of(20, 0), LocalTime.of(22, 30), now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `scheduling screening without day schedule - failure`() {
        // given - no events, day schedule is not created
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val screeningId = aScreeningId()
        val movieId = aMovieId()

        // when
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                screeningId,
                movieId,
                LocalTime.of(20, 0),
                LocalTime.of(22, 30),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Failure("Day schedule with id $dayScheduleId does not exist")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling screening with start time after end time - failure`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val screeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now))

        // when
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                screeningId,
                movieId,
                LocalTime.of(22, 30),
                LocalTime.of(20, 0),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Failure("Screening start time must be before end time")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling screening with start time equal to end time - failure`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val screeningId = aScreeningId()
        val movieId = aMovieId()
        val sameTime = LocalTime.of(20, 0)
        
        eventsOccurred(DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now))

        // when
        val result = executeCommand(ScheduleScreening(dayScheduleId, screeningId, movieId, sameTime, sameTime, now))

        // then
        val expectedResult = CommandResult.Failure("Screening start time must be before end time")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling screening with duplicate screening id - failure`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val screeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, screeningId, movieId, LocalTime.of(15, 0), LocalTime.of(17, 30), now)
        )

        // when
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                screeningId,
                movieId,
                LocalTime.of(20, 0),
                LocalTime.of(22, 30),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Failure("Screening with id $screeningId already scheduled")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling overlapping screening (new screening starts before existing ends) - failure`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(15, 0), LocalTime.of(17, 30), now)
        )

        // when - new screening 16:00-19:00 overlaps with existing 15:00-17:30
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                secondScreeningId,
                movieId,
                LocalTime.of(16, 0),
                LocalTime.of(19, 0),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Failure("Screening with time 16:00 - 19:00 overlaps with existing screening")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling overlapping screening (existing screening starts before new ends) - failure`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(17, 0), LocalTime.of(19, 30), now)
        )

        // when - new screening 15:00-18:00 overlaps with existing 17:00-19:30
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                secondScreeningId,
                movieId,
                LocalTime.of(15, 0),
                LocalTime.of(18, 0),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Failure("Screening with time 15:00 - 18:00 overlaps with existing screening")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling screening that completely contains existing screening - failure`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(16, 0), LocalTime.of(18, 0), now)
        )

        // when - new screening 15:00-19:00 completely contains existing 16:00-18:00
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                secondScreeningId,
                movieId,
                LocalTime.of(15, 0),
                LocalTime.of(19, 0),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Failure("Screening with time 15:00 - 19:00 overlaps with existing screening")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling screening that is completely contained by existing screening - failure`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(15, 0), LocalTime.of(19, 0), now)
        )

        // when - new screening 16:00-18:00 is completely contained by existing 15:00-19:00
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                secondScreeningId,
                movieId,
                LocalTime.of(16, 0),
                LocalTime.of(18, 0),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Failure("Screening with time 16:00 - 18:00 overlaps with existing screening")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `scheduling adjacent screenings (end time equals start time) - success`() {
        // given
        val now = currentTime()

        val dayScheduleId = aDayScheduleId()
        val day = LocalDate.of(2024, 1, 15)
        val firstScreeningId = aScreeningId()
        val secondScreeningId = aScreeningId()
        val movieId = aMovieId()
        
        eventsOccurred(
            DayScheduleCreated(dayScheduleId, LocalTime.of(10, 0), LocalTime.of(22, 0), now),
            ScreeningScheduled(dayScheduleId, firstScreeningId, movieId, LocalTime.of(15, 0), LocalTime.of(17, 30), now)
        )

        // when - new screening starts exactly when previous ends
        val result = executeCommand(
            ScheduleScreening(
                dayScheduleId,
                secondScreeningId,
                movieId,
                LocalTime.of(17, 30),
                LocalTime.of(20, 0),
                now
            )
        )

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("DaySchedule", dayScheduleId)
        val expectedEvent = ScreeningScheduled(dayScheduleId, secondScreeningId, movieId, LocalTime.of(17, 30), LocalTime.of(20, 0), now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    private fun aDayScheduleId() = DayScheduleId.random()
    private fun aScreeningId() = ScreeningId.random()
    private fun aMovieId() = MovieId.random()
}