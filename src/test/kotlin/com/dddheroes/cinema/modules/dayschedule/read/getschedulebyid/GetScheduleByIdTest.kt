package com.dddheroes.cinema.modules.dayschedule.read.getschedulebyid

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningCancelled
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalTime

@TestPropertySource(properties = ["slices.dayschedule.read.getschedulebyid.enabled=true"])
class GetScheduleByIdTest : MessagingSpringBootTest() {

    @Test
    fun `empty schedule`() {
        // when
        val scheduleId = DayScheduleId.random()
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result).isNull()
    }

    @Test
    fun `created schedule`() {
        // given
        val now = currentTime()
        val scheduleId = DayScheduleId.random()
        eventsOccurred(
            DayScheduleCreated(
                scheduleId,
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                now
            )
        )

        // when
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result).isEqualTo(
            GetScheduleByIdResult(
                id = scheduleId,
                day = scheduleId.toLocalDate(),
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                screeningTimes = emptyList()
            )
        )
    }

    @Test
    fun `schedule with single screening`() {
        // given
        val now = currentTime()

        val scheduleId = DayScheduleId.random()
        val screeningId = ScreeningId.random()
        val movieId = MovieId.random()
        eventsOccurred(
            DayScheduleCreated(
                scheduleId,
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screeningId,
                movieId = movieId,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30),
                now
            )
        )

        // when
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result).isEqualTo(
            GetScheduleByIdResult(
                id = scheduleId,
                day = scheduleId.toLocalDate(),
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                screeningTimes = listOf(
                    GetScheduleByIdResult.Screening(
                        screeningId = screeningId,
                        movieId = movieId,
                        startTime = LocalTime.of(14, 0),
                        endTime = LocalTime.of(16, 30)
                    )
                )
            )
        )
    }

    @Test
    fun `schedule with multiple screenings`() {
        // given
        val now = currentTime()

        val scheduleId = DayScheduleId.random()
        val screening1Id = ScreeningId.random()
        val screening2Id = ScreeningId.random()
        val screening3Id = ScreeningId.random()
        val movie1Id = MovieId.random()
        val movie2Id = MovieId.random()
        val movie3Id = MovieId.random()

        eventsOccurred(
            DayScheduleCreated(
                scheduleId,
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening1Id,
                movieId = movie1Id,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening2Id,
                movieId = movie2Id,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening3Id,
                movieId = movie3Id,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(21, 30),
                now
            )
        )

        // when
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result.screeningTimes).hasSize(3)
        assertThat(result.screeningTimes).containsExactlyInAnyOrder(
            GetScheduleByIdResult.Screening(
                screeningId = screening1Id,
                movieId = movie1Id,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(12, 0)
            ),
            GetScheduleByIdResult.Screening(
                screeningId = screening2Id,
                movieId = movie2Id,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30)
            ),
            GetScheduleByIdResult.Screening(
                screeningId = screening3Id,
                movieId = movie3Id,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(21, 30)
            )
        )
    }

    @Test
    fun `schedule with cancelled screening`() {
        // given
        val now = currentTime()

        val scheduleId = DayScheduleId.random()
        val screeningId = ScreeningId.random()
        val movieId = MovieId.random()
        eventsOccurred(
            DayScheduleCreated(
                scheduleId,
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screeningId,
                movieId = movieId,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30),
                now
            ),
            ScreeningCancelled(
                dayScheduleId = scheduleId,
                screeningId = screeningId,
                now
            )
        )

        // when
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result).isEqualTo(
            GetScheduleByIdResult(
                id = scheduleId,
                day = scheduleId.toLocalDate(),
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                screeningTimes = emptyList()
            )
        )
    }

    @Test
    fun `schedule with partially cancelled screenings`() {
        // given
        val now = currentTime()

        val scheduleId = DayScheduleId.random()
        val screening1Id = ScreeningId.random()
        val screening2Id = ScreeningId.random()
        val screening3Id = ScreeningId.random()
        val movie1Id = MovieId.random()
        val movie2Id = MovieId.random()
        val movie3Id = MovieId.random()

        eventsOccurred(
            DayScheduleCreated(
                scheduleId,
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening1Id,
                movieId = movie1Id,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening2Id,
                movieId = movie2Id,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening3Id,
                movieId = movie3Id,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(21, 30),
                now
            ),
            ScreeningCancelled(
                dayScheduleId = scheduleId,
                screeningId = screening2Id,
                now
            )
        )

        // when
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result.screeningTimes).hasSize(2)
        assertThat(result.screeningTimes).containsExactlyInAnyOrder(
            GetScheduleByIdResult.Screening(
                screeningId = screening1Id,
                movieId = movie1Id,
                startTime = LocalTime.of(10, 0),
                endTime = LocalTime.of(12, 0)
            ),
            GetScheduleByIdResult.Screening(
                screeningId = screening3Id,
                movieId = movie3Id,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(21, 30)
            )
        )
    }

    @Test
    fun `schedule with screening added after cancellation`() {
        // given
        val now = currentTime()

        val scheduleId = DayScheduleId.random()
        val originalScreeningId = ScreeningId.random()
        val newScreeningId = ScreeningId.random()
        val originalMovieId = MovieId.random()
        val newMovieId = MovieId.random()

        eventsOccurred(
            DayScheduleCreated(
                scheduleId,
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = originalScreeningId,
                movieId = originalMovieId,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30),
                now
            ),
            ScreeningCancelled(
                dayScheduleId = scheduleId,
                screeningId = originalScreeningId,
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = newScreeningId,
                movieId = newMovieId,
                startTime = LocalTime.of(15, 0),
                endTime = LocalTime.of(17, 30),
                now
            )
        )

        // when
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result.screeningTimes).hasSize(1)
        assertThat(result.screeningTimes[0]).isEqualTo(
            GetScheduleByIdResult.Screening(
                screeningId = newScreeningId,
                movieId = newMovieId,
                startTime = LocalTime.of(15, 0),
                endTime = LocalTime.of(17, 30)
            )
        )
    }

    @Test
    fun `schedule with same movie multiple times`() {
        // given
        val now = currentTime()

        val scheduleId = DayScheduleId.random()
        val screening1Id = ScreeningId.random()
        val screening2Id = ScreeningId.random()
        val movieId = MovieId.random() // same movie for both screenings

        eventsOccurred(
            DayScheduleCreated(
                scheduleId,
                openingTime = LocalTime.of(9, 0),
                closingTime = LocalTime.of(22, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening1Id,
                movieId = movieId,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30),
                now
            ),
            ScreeningScheduled(
                dayScheduleId = scheduleId,
                screeningId = screening2Id,
                movieId = movieId,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(21, 30),
                now
            )
        )

        // when
        val query = GetScheduleById(scheduleId)

        // then
        val result: GetScheduleByIdResult = executeQuery(query)
        assertThat(result.screeningTimes).hasSize(2)
        assertThat(result.screeningTimes).containsExactlyInAnyOrder(
            GetScheduleByIdResult.Screening(
                screeningId = screening1Id,
                movieId = movieId,
                startTime = LocalTime.of(14, 0),
                endTime = LocalTime.of(16, 30)
            ),
            GetScheduleByIdResult.Screening(
                screeningId = screening2Id,
                movieId = movieId,
                startTime = LocalTime.of(19, 0),
                endTime = LocalTime.of(21, 30)
            )
        )
    }

}