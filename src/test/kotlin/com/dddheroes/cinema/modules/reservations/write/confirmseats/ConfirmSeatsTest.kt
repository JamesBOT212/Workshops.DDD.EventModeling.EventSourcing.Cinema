package com.dddheroes.cinema.modules.reservations.write.confirmseats

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationConfirmed
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.events.SeatsConfirmed
import com.dddheroes.cinema.modules.reservations.write.confirmseats.ConfirmSeats
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["slices.reservations.write.confirmseats.enabled=true"])
class ConfirmSeatsTest : MessagingSpringBootTest() {

    @Test
    fun `confirming single seat (1st time) - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seat = aSeat()
        eventsOccurred(ReservationStarted(reservationId, screeningId, setOf(seat), now))

        // when
        val command = ConfirmSeats(reservationId, setOf(seat), now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val seatsConfirmed = SeatsConfirmed(reservationId, screeningId, setOf(seat), now.plusSeconds(30))
        val reservationConfirmed = ReservationConfirmed(reservationId, screeningId, setOf(seat), now.plusSeconds(30))
        assertLastStreamEvents(streamId, seatsConfirmed, reservationConfirmed)
    }

    @Test
    fun `confirming single seat that completes reservation - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seat = aSeat()
        eventsOccurred(ReservationStarted(reservationId, screeningId, setOf(seat), now))

        // when
        val command = ConfirmSeats(reservationId, setOf(seat), now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, setOf(seat), now),
            SeatsConfirmed(reservationId, screeningId, setOf(seat), now.plusSeconds(30)),
            ReservationConfirmed(reservationId, screeningId, setOf(seat), now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming multiple seats at once - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )
        eventsOccurred(ReservationStarted(reservationId, screeningId, seats, now))

        // when
        val command = ConfirmSeats(reservationId, seats, now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, seats, now),
            SeatsConfirmed(reservationId, screeningId, seats, now.plusSeconds(30)),
            ReservationConfirmed(reservationId, screeningId, seats, now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming partial seats - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val allSeats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )
        val seatsToConfirm = setOf(SeatNumber(1, 1), SeatNumber(1, 2))
        eventsOccurred(ReservationStarted(reservationId, screeningId, allSeats, now))

        // when
        val command = ConfirmSeats(reservationId, seatsToConfirm, now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, seatsToConfirm, now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming remaining seats after partial confirmation - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val allSeats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )
        val firstBatch = setOf(SeatNumber(1, 1))
        val secondBatch = setOf(SeatNumber(1, 2), SeatNumber(1, 3))
        eventsOccurred(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, firstBatch, now.plusSeconds(30))
        )

        // when
        val command = ConfirmSeats(reservationId, secondBatch, now.plusSeconds(60))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, firstBatch, now.plusSeconds(30)),
            SeatsConfirmed(reservationId, screeningId, secondBatch, now.plusSeconds(60)),
            ReservationConfirmed(reservationId, screeningId, allSeats, now.plusSeconds(60))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming seats incrementally one by one - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val allSeats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2)
        )
        eventsOccurred(ReservationStarted(reservationId, screeningId, allSeats, now))

        // when - confirm first seat
        val firstCommand = ConfirmSeats(reservationId, setOf(SeatNumber(1, 1)), now.plusSeconds(30))
        val firstResult = executeCommand(firstCommand)

        // then
        val expectedFirstResult = CommandResult.Success
        assertThat(firstResult).isEqualTo(expectedFirstResult)

        // when - confirm second seat
        val secondCommand = ConfirmSeats(reservationId, setOf(SeatNumber(1, 2)), now.plusSeconds(60))
        val secondResult = executeCommand(secondCommand)

        // then
        val expectedSecondResult = CommandResult.Success
        assertThat(secondResult).isEqualTo(expectedSecondResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, setOf(SeatNumber(1, 1)), now.plusSeconds(30)),
            SeatsConfirmed(reservationId, screeningId, setOf(SeatNumber(1, 2)), now.plusSeconds(60)),
            ReservationConfirmed(reservationId, screeningId, allSeats, now.plusSeconds(60))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming already confirmed seats - idempotent behavior`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(SeatNumber(1, 1), SeatNumber(1, 2))
        val confirmedSeats = setOf(SeatNumber(1, 1))
        eventsOccurred(
            ReservationStarted(reservationId, screeningId, seats, now),
            SeatsConfirmed(reservationId, screeningId, confirmedSeats, now.plusSeconds(30))
        )

        // when - try to confirm already confirmed seat
        val command = ConfirmSeats(reservationId, confirmedSeats, now.plusSeconds(60))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no new events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, seats, now),
            SeatsConfirmed(reservationId, screeningId, confirmedSeats, now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming seats when reservation already fully confirmed - idempotent behavior`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(SeatNumber(1, 1), SeatNumber(1, 2))
        eventsOccurred(
            ReservationStarted(reservationId, screeningId, seats, now),
            SeatsConfirmed(reservationId, screeningId, seats, now.plusSeconds(30)),
            ReservationConfirmed(reservationId, screeningId, seats, now.plusSeconds(30))
        )

        // when - try to confirm seats again
        val command = ConfirmSeats(reservationId, seats, now.plusSeconds(60))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no new events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, seats, now),
            SeatsConfirmed(reservationId, screeningId, seats, now.plusSeconds(30)),
            ReservationConfirmed(reservationId, screeningId, seats, now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming seats without reservation started - no operation`() {
        // given - no events (no reservation started)
        val now = currentTime()
        val reservationId = aReservationId()
        val seat = aSeat()

        // when
        val command = ConfirmSeats(reservationId, setOf(seat), now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        assertStreamEmpty(streamId)
    }

    @Test
    fun `confirming empty seats - no operation`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seat = aSeat()
        eventsOccurred(ReservationStarted(reservationId, screeningId, setOf(seat), now))

        // when - confirm empty set of seats
        val command = ConfirmSeats(reservationId, emptySet(), now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - no new events should be generated
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, setOf(seat), now)
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming seats not part of reservation - partial operation`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val reservedSeats = setOf(SeatNumber(1, 1), SeatNumber(1, 2))
        val seatsToConfirm = setOf(SeatNumber(1, 1), SeatNumber(2, 1)) // includes seat not in reservation
        eventsOccurred(ReservationStarted(reservationId, screeningId, reservedSeats, now))

        // when
        val command = ConfirmSeats(reservationId, seatsToConfirm, now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then - should confirm the seats that were provided (including non-reserved ones)
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, reservedSeats, now),
            SeatsConfirmed(reservationId, screeningId, seatsToConfirm, now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming mix of confirmed and unconfirmed seats - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val allSeats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )
        val alreadyConfirmed = setOf(SeatNumber(1, 1))
        val mixedSeats = setOf(SeatNumber(1, 1), SeatNumber(1, 2)) // mix of confirmed and unconfirmed
        eventsOccurred(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, alreadyConfirmed, now.plusSeconds(30))
        )

        // when
        val command = ConfirmSeats(reservationId, mixedSeats, now.plusSeconds(60))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, alreadyConfirmed, now.plusSeconds(30)),
            SeatsConfirmed(reservationId, screeningId, mixedSeats - alreadyConfirmed, now.plusSeconds(60))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming seats with large reservation - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val allSeats = (1..9).map { SeatNumber(1, it) }.toSet()
        val seatsToConfirm = (1..5).map { SeatNumber(1, it) }.toSet()
        eventsOccurred(ReservationStarted(reservationId, screeningId, allSeats, now))

        // when
        val command = ConfirmSeats(reservationId, seatsToConfirm, now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, seatsToConfirm, now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    @Test
    fun `confirming all seats of large reservation at once - success`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val allSeats = (1..9).map { SeatNumber(1, it) }.toSet()
        eventsOccurred(ReservationStarted(reservationId, screeningId, allSeats, now))

        // when
        val command = ConfirmSeats(reservationId, allSeats, now.plusSeconds(30))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvents = listOf(
            ReservationStarted(reservationId, screeningId, allSeats, now),
            SeatsConfirmed(reservationId, screeningId, allSeats, now.plusSeconds(30)),
            ReservationConfirmed(reservationId, screeningId, allSeats, now.plusSeconds(30))
        )
        assertStreamEvents(streamId, expectedEvents)
    }

    // Data generators
    private fun aReservationId() = ReservationId.random()
    private fun aScreeningId() = ScreeningId.random()
    private fun aSeat() = SeatNumber(1, 1)
}