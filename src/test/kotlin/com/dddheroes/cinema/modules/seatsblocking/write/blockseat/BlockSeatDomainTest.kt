package com.dddheroes.cinema.modules.seatsblocking.write.blockseat

import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import org.junit.jupiter.api.Test
import java.time.Instant
import assertk.assertThat
import assertk.assertions.*
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatEvent
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.BlockSeat
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.State
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.decide
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.evolve

class BlockSeatDomainTest {

    private val screeningId = ScreeningId.of("screening-123")
    private val seatNumber = SeatNumber(5, 3)
    private val blockadeOwner = "user-456"
    private val issuedAt = Instant.parse("2023-12-01T10:00:00Z")

    @Test
    fun `given no events, when BlockSeat command, then SeatNotBlocked event due to seat not placed`() {
        val givenEvents = emptyList<SeatEvent>()
        val command = BlockSeat(screeningId, seatNumber, blockadeOwner, issuedAt)

        val result = execute(givenEvents, command)

        val expectedEvent = SeatNotBlocked(
            screeningId,
            seatNumber,
            "Seat must be placed before it can be blocked",
            blockadeOwner,
            issuedAt
        )
        assertThat(result).containsExactly(expectedEvent)
    }

    @Test
    fun `given SeatPlaced event, when BlockSeat command, then SeatBlocked event`() {
        val givenEvents = listOf(
            SeatPlaced(screeningId, seatNumber, issuedAt.minusSeconds(60))
        )
        val command = BlockSeat(screeningId, seatNumber, blockadeOwner, issuedAt)

        val result = execute(givenEvents, command)

        val expectedEvent = SeatBlocked(screeningId, seatNumber, blockadeOwner, issuedAt)
        assertThat(result).containsExactly(expectedEvent)
    }

    @Test
    fun `given SeatPlaced and SeatBlocked by same owner, when BlockSeat command by same owner, then no events`() {
        val givenEvents = listOf(
            SeatPlaced(screeningId, seatNumber, issuedAt.minusSeconds(120)),
            SeatBlocked(screeningId, seatNumber, blockadeOwner, issuedAt.minusSeconds(60))
        )
        val command = BlockSeat(screeningId, seatNumber, blockadeOwner, issuedAt)

        val result = execute(givenEvents, command)

        assertThat(result).isEmpty()
    }

    @Test
    fun `given SeatPlaced and SeatBlocked by different owner, when BlockSeat command, then SeatNotBlocked event`() {
        val otherOwner = "other-user-789"
        val givenEvents = listOf(
            SeatPlaced(screeningId, seatNumber, issuedAt.minusSeconds(120)),
            SeatBlocked(screeningId, seatNumber, otherOwner, issuedAt.minusSeconds(60))
        )
        val command = BlockSeat(screeningId, seatNumber, blockadeOwner, issuedAt)

        val result = execute(givenEvents, command)

        val expectedEvent = SeatNotBlocked(
            screeningId,
            seatNumber,
            "Seat is already blocked by $otherOwner",
            blockadeOwner,
            issuedAt
        )
        assertThat(result).containsExactly(expectedEvent)
    }

    @Test
    fun `given SeatPlaced, SeatBlocked and SeatUnblocked, when BlockSeat command, then SeatBlocked event`() {
        val givenEvents = listOf(
            SeatPlaced(screeningId, seatNumber, issuedAt.minusSeconds(180)),
            SeatBlocked(screeningId, seatNumber, "previous-owner", issuedAt.minusSeconds(120)),
            SeatUnblocked(screeningId, seatNumber, "previous-owner", issuedAt.minusSeconds(60))
        )
        val command = BlockSeat(screeningId, seatNumber, blockadeOwner, issuedAt)

        val result = execute(givenEvents, command)

        val expectedEvent = SeatBlocked(screeningId, seatNumber, blockadeOwner, issuedAt)
        assertThat(result).containsExactly(expectedEvent)
    }

    private fun execute(givenEvents: List<SeatEvent>, command: BlockSeat): List<SeatEvent> {
        val currentState = givenEvents.fold(State()) { state, event -> evolve(state, event) }
        return decide(command, currentState)
    }
}