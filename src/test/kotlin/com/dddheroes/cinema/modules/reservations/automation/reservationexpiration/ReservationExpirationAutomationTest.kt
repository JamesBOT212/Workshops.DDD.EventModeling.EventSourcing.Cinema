package com.dddheroes.cinema.modules.reservations.automation.reservationexpiration

import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreenings
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreeningsResult
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.ScreeningReadModel
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationCollected
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.write.cancelreservation.CancelReservation
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@TestPropertySource(properties = ["slices.reservations.automation.reservationexpiration.enabled=true"])
class ReservationExpirationAutomationTest : MessagingSpringBootTest() {

    @Nested
    inner class HandleReservationStarted {

        @Test
        fun `when reservation started then schedule cancellation 15 minutes before screening`() {
            // given
            val reservationId = ReservationId.random()
            val screeningId = ScreeningId.random()
            val seats = setOf(SeatNumber(0, 0), SeatNumber(0, 1))
            val now = currentTime()

            val screeningDay = LocalDate.of(2024, 12, 25)
            val screeningStartTime = LocalTime.of(18, 30)
            val screeningEndTime = LocalTime.of(20, 30)

            val screeningReadModel = ScreeningReadModel(
                screeningId = screeningId.raw,
                dayScheduleId = "day-schedule-1",
                day = screeningDay,
                movieId = "movie-1",
                startTime = screeningStartTime,
                endTime = screeningEndTime
            )

            assumeQueryReturns(
                SearchScreenings(screeningId = screeningId),
                SearchScreeningsResult(listOf(screeningReadModel))
            )

            eventsOccurred(
                ReservationStarted(
                    reservationId = reservationId,
                    screeningId = screeningId,
                    seats = seats,
                    occurredAt = now
                )
            )

            // when - event processed by the automation

            // then
            val screeningStart = screeningDay.atTime(screeningStartTime).atZone(clock().zone).toInstant()
            val expectedCancellationTime = screeningStart.minus(15, ChronoUnit.MINUTES)

            assertCommandScheduled(
                CancelReservation(
                    reservationId = reservationId,
                    reason = "Reservation expired.",
                    issuedAt = expectedCancellationTime
                ),
                expectedCancellationTime
            )
        }

        @Test
        fun `when reservation started for morning screening then schedule cancellation correctly`() {
            // given
            val reservationId = ReservationId.random()
            val screeningId = ScreeningId.random()
            val seats = setOf(SeatNumber(1, 5))
            val now = currentTime()

            val screeningDay = LocalDate.of(2024, 12, 25)
            val screeningStartTime = LocalTime.of(10, 0) // Morning screening
            val screeningEndTime = LocalTime.of(12, 0)

            val screeningReadModel = ScreeningReadModel(
                screeningId = screeningId.raw,
                dayScheduleId = "day-schedule-2",
                day = screeningDay,
                movieId = "movie-2",
                startTime = screeningStartTime,
                endTime = screeningEndTime
            )

            assumeQueryReturns(
                SearchScreenings(screeningId = screeningId),
                SearchScreeningsResult(listOf(screeningReadModel))
            )

            eventsOccurred(
                ReservationStarted(
                    reservationId = reservationId,
                    screeningId = screeningId,
                    seats = seats,
                    occurredAt = now
                )
            )

            // when - event processed by the automation

            // then
            val screeningStart = screeningDay.atTime(screeningStartTime).atZone(clock().zone).toInstant()
            val expectedCancellationTime = screeningStart.minus(15, ChronoUnit.MINUTES)

            assertCommandScheduled(
                CancelReservation(
                    reservationId = reservationId,
                    reason = "Reservation expired.",
                    issuedAt = expectedCancellationTime
                ),
                expectedCancellationTime
            )
        }
    }

    @Nested
    inner class HandleReservationCollected {

        @Test
        fun `when reservation collected then cancel scheduled expiration`() {
            // given
            val reservationId = ReservationId.random()
            val screeningId = ScreeningId.random()
            val seats = setOf(SeatNumber(2, 3), SeatNumber(2, 4))
            val now = currentTime()

            eventsOccurred(
                ReservationCollected(
                    reservationId = reservationId,
                    screeningId = screeningId,
                    seats = seats,
                    occurredAt = now
                )
            )

            // when - event processed by the automation
            // then - deadline cancellation is handled internally by DeadlineManager
            // We can't easily assert deadline cancellation with current test framework,
            // todo: figure it out
        }
    }
}