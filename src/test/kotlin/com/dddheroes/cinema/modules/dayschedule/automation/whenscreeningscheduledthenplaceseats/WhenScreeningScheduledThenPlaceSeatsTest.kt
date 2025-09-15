package com.dddheroes.cinema.modules.dayschedule.automation.whenscreeningscheduledthenplaceseats

import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.modules.seatsblocking.write.placeseat.PlaceSeat
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalTime

@TestPropertySource(properties = ["slices.dayschedule.automation.whenscreeningscheduledthenplaceseats.enabled=true"])
class WhenScreeningScheduledThenPlaceSeatsTest : MessagingSpringBootTest() {

    @Test
    fun `when screening scheduled then place seats for the screening`() {
        // given
        val dayScheduleId = DayScheduleId.random()
        val dayScheduleScreeningId = ScreeningId.random()
        val movieId = MovieId.random()
        val startTime = LocalTime.of(14, 0)
        val endTime = LocalTime.of(16, 30)

        val now = currentTime()
        assumeCommandSuccess<PlaceSeat>()
        eventsOccurred(
            ScreeningScheduled(
                dayScheduleId = dayScheduleId,
                screeningId = dayScheduleScreeningId,
                movieId = movieId,
                startTime = startTime,
                endTime = endTime,
                occurredAt = now
            )
        )

        // when - event processed by the automation

        // then
        val seatsScreeningId = ScreeningId.of(dayScheduleScreeningId.raw)
        for (row in 0..9) {
            for (column in 0..9) {
                assertCommandExecuted(
                    PlaceSeat(seatsScreeningId, SeatNumber(row, column), now)
                )
            }
        }
    }

}