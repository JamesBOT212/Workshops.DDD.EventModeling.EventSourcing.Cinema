package com.dddheroes.cinema.modules.reservations.write.startreservation

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.write.startreservation.StartReservation
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.Instant

@TestPropertySource(properties = ["slices.reservations.write.startreservation.enabled=true"])
class StartReservationTest : MessagingSpringBootTest() {

    @Test
    fun `starting reservation (1st time) - success`() {
        // given - no events, new reservation
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seat = aSeat()

        // when
        val command = StartReservation(reservationId, screeningId, setOf(seat), now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationStarted(reservationId, screeningId, setOf(seat), now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `starting reservation with multiple seats - success`() {
        // given - no events, new reservation
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )

        // when
        val command = StartReservation(reservationId, screeningId, seats, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationStarted(reservationId, screeningId, seats, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `starting already started reservation - idempotent behavior`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seat = aSeat()
        val events = listOf(
            ReservationStarted(reservationId, screeningId, setOf(seat), now)
        )
        eventsOccurred(events)

        // when
        val command = StartReservation(reservationId, screeningId, setOf(seat), now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        // Should still have only the initial event, no new events generated
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `starting already started reservation with different parameters - idempotent behavior`() {
        // given
        val now = currentTime()
        val reservationId = aReservationId()
        val originalScreeningId = aScreeningId()
        val originalSeat = SeatNumber(1, 1)
        val events = listOf(
            ReservationStarted(reservationId, originalScreeningId, setOf(originalSeat), now)
        )
        eventsOccurred(events)

        // when - attempt to start with different screening and seat
        val differentScreeningId = aScreeningId()
        val differentSeat = SeatNumber(2, 2)
        val command = StartReservation(reservationId, differentScreeningId, setOf(differentSeat), now.plusSeconds(60))
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        // Should still have only the original event, ignoring the new parameters
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `starting reservation with single seat - success`() {
        // given - no events, new reservation
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seat = SeatNumber(5, 3)

        // when
        val command = StartReservation(reservationId, screeningId, setOf(seat), now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationStarted(reservationId, screeningId, setOf(seat), now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `starting reservation with large set of seats - success`() {
        // given - no events, new reservation
        val now = currentTime()
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seats = (1..9).map { SeatNumber(1, it) }.toSet()

        // when
        val command = StartReservation(reservationId, screeningId, seats, now)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationStarted(reservationId, screeningId, seats, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `multiple reservations with different ids - success`() {
        // given - no events for either reservation
        val now = currentTime()
        val reservation1Id = aReservationId()
        val reservation2Id = aReservationId()
        val screeningId = aScreeningId()
        val seat1 = SeatNumber(1, 1)
        val seat2 = SeatNumber(1, 2)

        // when - start first reservation
        val command1 = StartReservation(reservation1Id, screeningId, setOf(seat1), now)
        val result1 = executeCommand(command1)

        // then
        val expectedResult1 = CommandResult.Success
        assertThat(result1).isEqualTo(expectedResult1)

        // when - start second reservation
        val command2 = StartReservation(reservation2Id, screeningId, setOf(seat2), now)
        val result2 = executeCommand(command2)

        // then
        val expectedResult2 = CommandResult.Success
        assertThat(result2).isEqualTo(expectedResult2)

        // then - check both streams have their respective events
        val streamId1 = EventStreamId.of("Reservation", reservation1Id)
        val expectedEvent1 = ReservationStarted(reservation1Id, screeningId, setOf(seat1), now)
        assertLastStreamEvent(streamId1, expectedEvent1)

        val streamId2 = EventStreamId.of("Reservation", reservation2Id)
        val expectedEvent2 = ReservationStarted(reservation2Id, screeningId, setOf(seat2), now)
        assertLastStreamEvent(streamId2, expectedEvent2)
    }

    @Test
    fun `starting reservation at different timestamps - success`() {
        // given - no events, new reservation
        val reservationId = aReservationId()
        val screeningId = aScreeningId()
        val seat = aSeat()
        
        val specificTime = Instant.parse("2023-12-15T19:30:00Z")
        currentTimeIs(specificTime)

        // when
        val command = StartReservation(reservationId, screeningId, setOf(seat), specificTime)
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Reservation", reservationId)
        val expectedEvent = ReservationStarted(reservationId, screeningId, setOf(seat), specificTime)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    // Data generators
    private fun aReservationId() = ReservationId.random()
    private fun aScreeningId() = ScreeningId.random()
    private fun aSeat() = SeatNumber(1, 1)
}