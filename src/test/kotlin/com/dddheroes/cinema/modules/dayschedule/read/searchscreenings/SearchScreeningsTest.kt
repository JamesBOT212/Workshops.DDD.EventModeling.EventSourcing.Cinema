package com.dddheroes.cinema.modules.dayschedule.read.searchscreenings

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningCancelled
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.cinema.modules.dayschedule.randomDate
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalTime

@TestPropertySource(properties = ["slices.dayschedule.read.searchscreenings.enabled=true"])
class SearchScreeningsTest : MessagingSpringBootTest() {

    @Test
    fun `empty movie screenings`() {
        // when
        val movieId = MovieId.random()
        val query = SearchScreenings(movieId = movieId)

        // then
        awaitUntilAsserted {
            val result: SearchScreeningsResult = executeQuery(query)
            assertThat(result.items).isEmpty()
        }
    }

    @Test
    fun `many schedules screenings`() {
        // given
        val now = currentTime()

        val movieId = MovieId.random()
        val dayScheduleId1 = DayScheduleId.random()
        val day1Screening1 = ScreeningId.random()

        val dayScheduleId2 = DayScheduleId.random()
        val day2Screening1 = ScreeningId.random()
        val day2Screening2 = ScreeningId.random()

        eventsOccurred(
            ScreeningScheduled(
                dayScheduleId1,
                day1Screening1,
                movieId,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId2,
                day2Screening1,
                movieId,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId2,
                day2Screening2,
                movieId,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                now
            )
        )

        // when
        val query = SearchScreenings(movieId = movieId)

        // then
        // Explanation: we need to wait until the read model is updated, because the projection is asynchronous
        awaitUntilAsserted {
            val result: SearchScreeningsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId1.toString(),
                    screeningId = day1Screening1.raw,
                    movieId = movieId.raw,
                    day = dayScheduleId1.toLocalDate(),
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(12, 0)
                ),
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId2.toString(),
                    screeningId = day2Screening1.raw,
                    movieId = movieId.raw,
                    day = dayScheduleId2.toLocalDate(),
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(12, 0)
                ),
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId2.toString(),
                    screeningId = day2Screening2.raw,
                    movieId = movieId.raw,
                    day = dayScheduleId2.toLocalDate(),
                    startTime = LocalTime.of(13, 0),
                    endTime = LocalTime.of(14, 0)
                )
            )
        }
    }

    @Test
    fun `scheduled and cancelled screenings`() {
        // given
        val now = currentTime()

        val movieId = MovieId.random()
        val dayScheduleId1 = DayScheduleId.random()
        val day1Screening1 = ScreeningId.random()

        val dayScheduleId2 = DayScheduleId.random()
        val day2Screening1 = ScreeningId.random()
        val day2Screening2 = ScreeningId.random()

        eventsOccurred(
            ScreeningScheduled(
                dayScheduleId1,
                day1Screening1,
                movieId,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId2,
                day2Screening1,
                movieId,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                now
            ),
            ScreeningCancelled(
                dayScheduleId2,
                day2Screening1,
                now
            ),
            ScreeningScheduled(
                dayScheduleId2,
                day2Screening2,
                movieId,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                now
            )
        )

        // when
        val query = SearchScreenings(movieId = movieId)

        // then
        awaitUntilAsserted {
            val result: SearchScreeningsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId1.toString(),
                    screeningId = day1Screening1.raw,
                    movieId = movieId.raw,
                    day = dayScheduleId1.toLocalDate(),
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(12, 0)
                ),
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId2.toString(),
                    screeningId = day2Screening2.raw,
                    movieId = movieId.raw,
                    day = dayScheduleId2.toLocalDate(),
                    startTime = LocalTime.of(13, 0),
                    endTime = LocalTime.of(14, 0)
                )
            )
        }
    }

    @Test
    fun `search screenings by day only`() {
        // given
        val now = currentTime()
        val targetDay = randomDate()
        
        val movieId1 = MovieId.random()
        val movieId2 = MovieId.random()
        val dayScheduleId = DayScheduleId.of(targetDay)
        val screening1 = ScreeningId.random()
        val screening2 = ScreeningId.random()

        eventsOccurred(
            ScreeningScheduled(
                dayScheduleId,
                screening1,
                movieId1,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId,
                screening2,
                movieId2,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                now
            )
        )

        // when
        val query = SearchScreenings(day = targetDay)

        // then
        awaitUntilAsserted {
            val result: SearchScreeningsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId.toString(),
                    screeningId = screening1.raw,
                    movieId = movieId1.raw,
                    day = targetDay,
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(12, 0)
                ),
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId.toString(),
                    screeningId = screening2.raw,
                    movieId = movieId2.raw,
                    day = targetDay,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(16, 0)
                )
            )
        }
    }

    @Test
    fun `search screenings by movie and day`() {
        // given
        val now = currentTime()
        val targetDay = randomDate()
        val targetMovieId = MovieId.random()
        val otherMovieId = MovieId.random()
        
        val dayScheduleId = DayScheduleId.of(targetDay)
        val targetScreening = ScreeningId.random()
        val otherMovieScreening = ScreeningId.random()

        eventsOccurred(
            ScreeningScheduled(
                dayScheduleId,
                targetScreening,
                targetMovieId,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId,
                otherMovieScreening,
                otherMovieId,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                now
            )
        )

        // when
        val query = SearchScreenings(movieId = targetMovieId, day = targetDay)

        // then
        awaitUntilAsserted {
            val result: SearchScreeningsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId.toString(),
                    screeningId = targetScreening.raw,
                    movieId = targetMovieId.raw,
                    day = targetDay,
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(12, 0)
                )
            )
        }
    }

    @Test
    fun `search screenings by screening id`() {
        // given
        val now = currentTime()
        val targetDay = randomDate()
        val movieId = MovieId.random()
        
        val dayScheduleId = DayScheduleId.of(targetDay)
        val targetScreeningId = ScreeningId.random()
        val otherScreeningId = ScreeningId.random()

        eventsOccurred(
            ScreeningScheduled(
                dayScheduleId,
                targetScreeningId,
                movieId,
                LocalTime.of(10, 0),
                LocalTime.of(12, 0),
                now
            ),
            ScreeningScheduled(
                dayScheduleId,
                otherScreeningId,
                movieId,
                LocalTime.of(14, 0),
                LocalTime.of(16, 0),
                now
            )
        )

        // when
        val query = SearchScreenings(screeningId = targetScreeningId)

        // then
        awaitUntilAsserted {
            val result: SearchScreeningsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningReadModel(
                    dayScheduleId = dayScheduleId.toString(),
                    screeningId = targetScreeningId.raw,
                    movieId = movieId.raw,
                    day = targetDay,
                    startTime = LocalTime.of(10, 0),
                    endTime = LocalTime.of(12, 0)
                )
            )
        }
    }

}