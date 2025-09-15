package com.dddheroes.cinema.modules.reservations.write.collectreservation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.events.ReservationConfirmed
import com.dddheroes.cinema.modules.reservations.events.ReservationCancelled
import com.dddheroes.cinema.modules.reservations.events.ReservationCollected
import com.dddheroes.cinema.modules.reservations.write.collectreservation.CollectReservation
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.Instant

@TestPropertySource(properties = ["slices.reservations.write.collectreservation.enabled=true"])
class CollectReservationTest : MessagingSpringBootTest() {

    @Test
    fun `collecting confirmed reservation - success`() {
        // given - reservation is confirmed
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationConfirmed(reservationId, screeningId, seats, now.minusSeconds(60))
        )
        eventsOccurred(events)

        // when
        val command = CollectReservation(reservationId, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCollected(reservationId, screeningId, seats, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `collecting already collected reservation - idempotent behavior`() {
        // given - reservation is already collected
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationConfirmed(reservationId, screeningId, seats, now.minusSeconds(60)),
            ReservationCollected(reservationId, screeningId, seats, now.minusSeconds(30))
        )
        eventsOccurred(events)

        // when
        val command = CollectReservation(reservationId, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no new events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `collecting cancelled reservation - no operation`() {
        // given - reservation is cancelled
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationCancelled(reservationId, screeningId, seats, "Timeout", now.minusSeconds(60))
        )
        eventsOccurred(events)

        // when
        val command = CollectReservation(reservationId, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no new events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `collecting not confirmed reservation - no operation`() {
        // given - reservation is started but not confirmed
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300))
        )
        eventsOccurred(events)

        // when
        val command = CollectReservation(reservationId, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no new events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `collecting non-existent reservation - no operation`() {
        // given - no events for reservation
        val now = currentTime()
        val reservationId = aReservationId()

        // when
        val command = CollectReservation(reservationId, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        assertStreamEvents(streamId, emptyList())
    }

    @Test
    fun `collecting confirmed reservation with multiple seats - success`() {
        // given - reservation with multiple seats is confirmed
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationConfirmed(reservationId, screeningId, seats, now.minusSeconds(60))
        )
        eventsOccurred(events)

        // when
        val command = CollectReservation(reservationId, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCollected(reservationId, screeningId, seats, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `collecting confirmed reservation at different time - success`() {
        // given - reservation is confirmed
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationConfirmed(reservationId, screeningId, seats, now.minusSeconds(60))
        )
        eventsOccurred(events)

        // when
        val command = CollectReservation(reservationId, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCollected(reservationId, screeningId, seats, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `collecting confirmed reservation at specific timestamp - success`() {
        // given - reservation is confirmed
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        
        val startTime = Instant.parse("2023-12-15T19:00:00Z")
        val confirmTime = Instant.parse("2023-12-15T19:30:00Z")
        val collectTime = Instant.parse("2023-12-15T20:00:00Z")
        
        currentTimeIs(collectTime)
        
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, startTime),
            ReservationConfirmed(reservationId, screeningId, seats, confirmTime)
        )
        eventsOccurred(events)

        // when
        val command = CollectReservation(reservationId, collectTime)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCollected(reservationId, screeningId, seats, collectTime)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    // Data generators
    private fun aReservationId() = ReservationId.random()
    private fun aScreeningId() = ScreeningId.random()
    private fun aSeat() = SeatNumber(1, 1)
}