package com.dddheroes.cinema.modules.seatsblocking.write.blockseat

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.BlockSeat
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.seatsblocking.write.blockseat.enabled=true"])
class BlockSeatTest : MessagingSpringBootTest() {

    @Test
    fun `blocking seat (1st time) - success`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val events = listOf(
            SeatPlaced(screeningId, seatR1C1, now)
        )
        eventsOccurred(events)

        // when
        val blockadeOwner = aBlockadeOwner()
        val result = executeCommand(BlockSeat(screeningId, seatR1C1, blockadeOwner, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        val expectedEvent = SeatBlocked(screeningId, seatR1C1, blockadeOwner, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `blocking unblocked seat - success`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val firstBlockadeOwner = aBlockadeOwner()
        val events = listOf(
            SeatPlaced(screeningId, seatR1C1, now),
            SeatBlocked(screeningId, seatR1C1, firstBlockadeOwner, now),
            SeatUnblocked(screeningId, seatR1C1, firstBlockadeOwner, now)
        )
        eventsOccurred(events)

        // when
        val secondBlockadeOwner = aBlockadeOwner()
        val result = executeCommand(BlockSeat(screeningId, seatR1C1, secondBlockadeOwner, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        val expectedEvent = SeatBlocked(screeningId, seatR1C1, secondBlockadeOwner, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `owner blocking already blocked seat by same owner - nothing`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val blockadeOwner = aBlockadeOwner()
        val events = listOf(
            SeatPlaced(screeningId, seatR1C1, now),
            SeatBlocked(screeningId, seatR1C1, blockadeOwner, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(BlockSeat(screeningId, seatR1C1, blockadeOwner, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `different owner blocking already blocked seat - failure`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val firstBlockadeOwner = aBlockadeOwner()
        val events = listOf(
            SeatPlaced(screeningId, seatR1C1, now),
            SeatBlocked(screeningId, seatR1C1, firstBlockadeOwner, now)
        )
        eventsOccurred(events)

        // when
        val secondBlockadeOwner = aBlockadeOwner()
        val result = executeCommand(BlockSeat(screeningId, seatR1C1, secondBlockadeOwner, now))

        // then
        val expectedResult = CommandResult.Failure("Seat is already blocked by $firstBlockadeOwner")
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        val expectedEvent = SeatNotBlocked(screeningId, seatR1C1, "Seat is already blocked by $firstBlockadeOwner", triedBy = secondBlockadeOwner, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `blocking non-placed seat - failure`() {
        // given - no events, seat is not placed
        val now = currentTime()

        // when
        val screeningId = ScreeningId.random()
        val seatR1C1 = SeatNumber(1, 1)
        val blockadeOwner = aBlockadeOwner()
        val result = executeCommand(BlockSeat(screeningId, seatR1C1, blockadeOwner, now))

        // then
        val expectedResult = CommandResult.Failure("Seat must be placed before it can be blocked")
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Seat", screeningId, seatR1C1)
        val expectedEvent = SeatNotBlocked(screeningId, seatR1C1, "Seat must be placed before it can be blocked", triedBy = blockadeOwner, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    fun aBlockadeOwner() = "Reservation:${UUID.randomUUID()}"
}

