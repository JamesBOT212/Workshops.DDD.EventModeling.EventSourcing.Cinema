package com.dddheroes.cinema.modules.reservations.write.cancelreservation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.events.ReservationConfirmed
import com.dddheroes.cinema.modules.reservations.events.ReservationCancelled
import com.dddheroes.cinema.modules.reservations.events.ReservationCollected
import com.dddheroes.cinema.modules.reservations.write.cancelreservation.CancelReservation
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.Instant

@TestPropertySource(properties = ["slices.reservations.write.cancelreservation.enabled=true"])
class CancelReservationTest : MessagingSpringBootTest() {

    @Test
    fun `cancelling started reservation - success`() {
        // given - reservation is started
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val reason = "Customer requested cancellation"
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300))
        )
        eventsOccurred(events)

        // when
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCancelled(reservationId, screeningId, seats, reason, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `cancelling confirmed reservation - success`() {
        // given - reservation is confirmed
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val reason = "Payment failed"
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationConfirmed(reservationId, screeningId, seats, now.minusSeconds(60))
        )
        eventsOccurred(events)

        // when
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCancelled(reservationId, screeningId, seats, reason, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `cancelling already cancelled reservation - idempotent behavior`() {
        // given - reservation is already cancelled
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val reason = "New cancellation attempt"
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationCancelled(reservationId, screeningId, seats, "Original cancellation", now.minusSeconds(60))
        )
        eventsOccurred(events)

        // when
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no new events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `cancelling collected reservation - throws exception`() {
        // given - reservation is collected
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val reason = "Attempt to cancel collected reservation"
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300)),
            ReservationConfirmed(reservationId, screeningId, seats, now.minusSeconds(120)),
            ReservationCollected(reservationId, screeningId, seats, now.minusSeconds(60))
        )
        eventsOccurred(events)

        // when & then
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        assertThat(result).isInstanceOf<CommandResult.Failure>()
        val failure = result as CommandResult.Failure
        assertThat(failure.message).isEqualTo("Cannot cancel reservation that has already been collected")
    }

    @Test
    fun `cancelling non-existent reservation - throws exception`() {
        // given - no events for reservation
        val now = currentTime()
        val reservationId = aReservationId()
        val reason = "Cancel non-existent reservation"

        // when & then
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        assertThat(result).isInstanceOf<CommandResult.Failure>()
        val failure = result as CommandResult.Failure
        assertThat(failure.message).isEqualTo("Cannot cancel reservation that has not been started")
    }

    @Test
    fun `cancelling reservation with multiple seats - success`() {
        // given - reservation with multiple seats is started
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )
        val reason = "Family changed plans"
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300))
        )
        eventsOccurred(events)

        // when
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCancelled(reservationId, screeningId, seats, reason, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `cancelling reservation with different reasons - success`() {
        // given - reservation is started
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val reason = "Emergency situation"
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(300))
        )
        eventsOccurred(events)

        // when
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCancelled(reservationId, screeningId, seats, reason, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `cancelling reservation at specific timestamp - success`() {
        // given - reservation is started
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val reason = "Scheduled cancellation"
        
        val startTime = Instant.parse("2023-12-15T19:00:00Z")
        val cancelTime = Instant.parse("2023-12-15T19:30:00Z")
        
        currentTimeIs(cancelTime)
        
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, startTime)
        )
        eventsOccurred(events)

        // when
        val command = CancelReservation(reservationId, reason, cancelTime)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCancelled(reservationId, screeningId, seats, reason, cancelTime)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `cancelling reservation with timeout reason - success`() {
        // given - reservation is started
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(aSeat())
        val reason = "Reservation timeout"
        val events = listOf(
            ReservationStarted(reservationId, screeningId, seats, now.minusSeconds(600))
        )
        eventsOccurred(events)

        // when
        val command = CancelReservation(reservationId, reason, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationCancelled(reservationId, screeningId, seats, reason, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    // Data generators
    private fun aReservationId() = ReservationId.random()
    private fun aScreeningId() = ScreeningId.random()
    private fun aSeat() = SeatNumber(1, 1)
}