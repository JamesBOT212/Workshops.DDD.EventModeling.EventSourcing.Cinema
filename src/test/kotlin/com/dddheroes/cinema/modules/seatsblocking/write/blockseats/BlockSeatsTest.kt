package com.dddheroes.cinema.modules.seatsblocking.write.blockseats

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.write.blockseats.BlockSeats
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.seatsblocking.write.blockseats.enabled=true"])
class BlockSeatsTest : MessagingSpringBootTest() {

    @Test
    fun `should block all requested seats when all are unblocked`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seats = listOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2),
            SeatNumber(1, 3)
        )
        eventsOccurred(
            SeatPlaced(screeningId, seats[0], now),
            SeatPlaced(screeningId, seats[1], now),
            SeatPlaced(screeningId, seats[2], now),
        )

        val owner = aBlockadeOwner()
        val command = BlockSeats(screeningId, seats.toSet(), owner, now)

        // when
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        assertAll(
            seats.map { seat ->
                {
                    val streamId = EventStreamId.of("Seat", screeningId, seat)
                    assertLastStreamEvent(streamId, SeatBlocked(screeningId, seat, owner, now))
                }
            }
        )
    }

    @Test
    fun `should block only unblocked seats when owner already owns some seats`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val owner = aBlockadeOwner()
        val alreadyBlockedSeats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2)
        )
        val unblockedSeats = setOf(
            SeatNumber(1, 3),
            SeatNumber(1, 4),
            SeatNumber(1, 5)
        )
        val allSeats = alreadyBlockedSeats + unblockedSeats

        eventsOccurred(
            allSeats.map { seat -> SeatPlaced(screeningId, seat, now) } +
                    alreadyBlockedSeats.map { seat -> SeatBlocked(screeningId, seat, owner, now) }
        )

        val command = BlockSeats(screeningId, allSeats, owner, now)

        // when
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        assertAll(
            "Already blocked seats should have only the initial event",
            alreadyBlockedSeats.map { seat ->
                {
                    val streamId = EventStreamId.of("Seat", screeningId, seat)
                    assertStreamEvents(streamId, listOf(
                        SeatPlaced(screeningId, seat, now),
                        SeatBlocked(screeningId, seat, owner, now)
                    ))
                }
            }
        )

        assertAll(
            "Previously unblocked seats should now have a blocking event",
            unblockedSeats.map { seat ->
                {
                    val streamId = EventStreamId.of("Seat", screeningId, seat)
                    assertLastStreamEvent(streamId, SeatBlocked(screeningId, seat, owner, now))
                }
            }
        )
    }

    @Test
    fun `should fail when trying to block seats already blocked by others`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val owner1 = aBlockadeOwner()
        val owner2 = aBlockadeOwner()
        val seatsBlockedByOwner1 = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2)
        )
        val unblockedSeats = setOf(
            SeatNumber(1, 3),
            SeatNumber(1, 4)
        )
        val allSeats = seatsBlockedByOwner1 + unblockedSeats

        eventsOccurred(
            allSeats.map { seat -> SeatPlaced(screeningId, seat, now) } +
                seatsBlockedByOwner1.map { seat -> SeatBlocked(screeningId, seat, owner1, now) }
        )

        val command = BlockSeats(screeningId, allSeats, owner2, now)

        // when
        val result = executeCommand(command)

        // then
        val expectedResult =
            CommandResult.Failure("Cannot block seats - some seats are already blocked by others: $seatsBlockedByOwner1")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `should not generate events when trying to block seats already owned by same owner`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val owner = aBlockadeOwner()
        val seats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2)
        )

        // Place all seats first and pre-block them by the same owner
        val events = seats.flatMap { seat ->
            listOf(
                SeatPlaced(screeningId, seat, now),
                SeatBlocked(screeningId, seat, owner, now)
            )
        }
        eventsOccurred(events)

        val command = BlockSeats(screeningId, seats, owner, now)

        // when
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        assertAll(
            "Should have only the initial events, no new events generated",
            seats.map { seat ->
                {
                    val streamId = EventStreamId.of("Seat", screeningId, seat)
                    assertStreamEvents(streamId, listOf(
                        SeatPlaced(screeningId, seat, now),
                        SeatBlocked(screeningId, seat, owner, now)
                    ))
                }
            }
        )
    }

    @Test
    fun `should handle mixed scenario with some seats owned, some free, and some blocked by others`() {
        // given
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val owner1 = aBlockadeOwner()
        val owner2 = aBlockadeOwner()
        val seatsOwnedByOwner1 = setOf(SeatNumber(1, 1))
        val seatsBlockedByOwner2 = setOf(SeatNumber(1, 2))
        val freeSeats = setOf(SeatNumber(1, 3))
        val allSeats = seatsOwnedByOwner1 + seatsBlockedByOwner2 + freeSeats

        // Block seats by different owners
        val placedEvents = allSeats.map { seat -> SeatPlaced(screeningId, seat, now) }
        val blockingEvents = seatsOwnedByOwner1.map { seat -> SeatBlocked(screeningId, seat, owner1, now) } +
                seatsBlockedByOwner2.map { seat -> SeatBlocked(screeningId, seat, owner2, now) }
        eventsOccurred(placedEvents + blockingEvents)

        val command = BlockSeats(screeningId, allSeats, owner1, now)

        // when
        val result = executeCommand(command)

        // then
        val expectedResult =
            CommandResult.Failure("Cannot block seats - some seats are already blocked by others: $seatsBlockedByOwner2")
        assertThat(result).isEqualTo(expectedResult)
    }

    @Test
    fun `should fail when trying to block non-placed seats`() {
        // given - no events, seats are not placed
        val now = currentTime()

        val screeningId = ScreeningId.random()
        val seats = setOf(
            SeatNumber(1, 1),
            SeatNumber(1, 2)
        )
        val owner = aBlockadeOwner()
        val command = BlockSeats(screeningId, seats, owner, now)

        // when
        val result = executeCommand(command)

        // then
        val expectedResult = CommandResult.Failure("Cannot block seats - must be placed first")
        assertThat(result).isEqualTo(expectedResult)
    }

    private fun aBlockadeOwner() = "Reservation:${UUID.randomUUID()}"
}

