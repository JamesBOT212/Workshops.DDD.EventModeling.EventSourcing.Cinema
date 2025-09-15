package com.dddheroes.cinema.modules.seatsblocking.write.placeseat

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.write.placeseat.PlaceSeat
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["slices.seatsblocking.write.placeseat.enabled=true"])
class PlaceSeatTest : MessagingSpringBootTest() {

    @Test
    fun `placing seat (1st time) - success`() {
        // given - no events, seat is not placed yet
        val now = currentTime()

        // when
        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val result = executeCommand(PlaceSeat(screeningId, seatR1C1, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        val expectedEvent = SeatPlaced(screeningId, seatR1C1, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `placing already placed seat - nothing`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val events = listOf(
            SeatPlaced(screeningId, seatR1C1, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(PlaceSeat(screeningId, seatR1C1, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        assertStreamEvents(streamId, events) // Only the original event, no new event
    }

    @Test
    fun `placing seat that was placed then blocked - nothing`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val blockadeOwner = "Reservation:12345"
        val events = listOf(
            SeatPlaced(screeningId, seatR1C1, now),
            SeatBlocked(screeningId, seatR1C1, blockadeOwner, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(PlaceSeat(screeningId, seatR1C1, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        assertStreamEvents(streamId, events) // Only the original events, no new event
    }
}