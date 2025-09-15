package com.dddheroes.cinema.modules.seatsblocking.read.getscreeningseats

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.cinema.modules.seatsblocking.read.getscreeningseats.GetScreeningSeats
import com.dddheroes.cinema.modules.seatsblocking.read.getscreeningseats.GetScreeningSeatsResult
import com.dddheroes.cinema.modules.seatsblocking.read.getscreeningseats.ScreeningSeatsReadModel
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.util.*

@TestPropertySource(properties = ["slices.seatsblocking.read.getscreeningseats.enabled=true"])
class GetScreeningSeatsTest : MessagingSpringBootTest() {

    @Test
    fun `empty screening seats`() {
        // when
        val screeningId = ScreeningId.random()
        val query = GetScreeningSeats(screeningId = screeningId)

        // then
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query)
            assertThat(result.items).isEmpty()
        }
    }

    @Test
    fun `single seat placed`() {
        // given
        val now = currentTime()
        val screeningId = ScreeningId.random()
        val seat = SeatNumber(1, 3)

        eventsOccurred(
            SeatPlaced(
                screeningId = screeningId,
                seat = seat,
                occurredAt = now
            )
        )

        // when
        val query = GetScreeningSeats(screeningId = screeningId)

        // then
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 1,
                    column = 3,
                    blockedBy = null
                )
            )
        }
    }

    @Test
    fun `multiple seats placed`() {
        // given
        val now = currentTime()
        val screeningId = ScreeningId.random()
        val seat1 = SeatNumber(0, 5)
        val seat2 = SeatNumber(2, 7)
        val seat3 = SeatNumber(1, 1)

        eventsOccurred(
            SeatPlaced(
                screeningId = screeningId,
                seat = seat1,
                occurredAt = now
            ),
            SeatPlaced(
                screeningId = screeningId,
                seat = seat2,
                occurredAt = now
            ),
            SeatPlaced(
                screeningId = screeningId,
                seat = seat3,
                occurredAt = now
            )
        )

        // when
        val query = GetScreeningSeats(screeningId = screeningId)

        // then
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 0,
                    column = 5,
                    blockedBy = null
                ),
                ScreeningSeatsReadModel.Seat(
                    row = 2,
                    column = 7,
                    blockedBy = null
                ),
                ScreeningSeatsReadModel.Seat(
                    row = 1,
                    column = 1,
                    blockedBy = null
                )
            )
        }
    }

    @Test
    fun `seat placed and blocked`() {
        // given
        val now = currentTime()
        val screeningId = ScreeningId.random()
        val seat = SeatNumber(2, 4)
        val blockadeOwner = aBlockadeOwner()

        eventsOccurred(
            SeatPlaced(
                screeningId = screeningId,
                seat = seat,
                occurredAt = now
            ),
            SeatBlocked(
                screeningId = screeningId,
                seat = seat,
                blockadeOwner = blockadeOwner,
                occurredAt = now
            )
        )

        // when
        val query = GetScreeningSeats(screeningId = screeningId)

        // then
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 2,
                    column = 4,
                    blockedBy = blockadeOwner
                )
            )
        }
    }

    @Test
    fun `seat placed blocked and unblocked`() {
        // given
        val now = currentTime()
        val screeningId = ScreeningId.random()
        val seat = SeatNumber(0, 0)
        val blockadeOwner = aBlockadeOwner()

        eventsOccurred(
            SeatPlaced(
                screeningId = screeningId,
                seat = seat,
                occurredAt = now
            ),
            SeatBlocked(
                screeningId = screeningId,
                seat = seat,
                blockadeOwner = blockadeOwner,
                occurredAt = now
            ),
            SeatUnblocked(
                screeningId = screeningId,
                seat = seat,
                blockadeOwner = blockadeOwner,
                occurredAt = now
            )
        )

        // when
        val query = GetScreeningSeats(screeningId = screeningId)

        // then
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 0,
                    column = 0,
                    blockedBy = null
                )
            )
        }
    }

    @Test
    fun `multiple seats with different blocking states`() {
        // given
        val now = currentTime()
        val screeningId = ScreeningId.random()
        val seat1 = SeatNumber(1, 0)
        val seat2 = SeatNumber(1, 1)
        val seat3 = SeatNumber(1, 2)
        val blockadeOwner1 = aBlockadeOwner()
        val blockadeOwner2 = aBlockadeOwner()

        eventsOccurred(
            SeatPlaced(
                screeningId = screeningId,
                seat = seat1,
                occurredAt = now
            ),
            SeatPlaced(
                screeningId = screeningId,
                seat = seat2,
                occurredAt = now
            ),
            SeatPlaced(
                screeningId = screeningId,
                seat = seat3,
                occurredAt = now
            ),
            SeatBlocked(
                screeningId = screeningId,
                seat = seat1,
                blockadeOwner = blockadeOwner1,
                occurredAt = now
            ),
            SeatBlocked(
                screeningId = screeningId,
                seat = seat3,
                blockadeOwner = blockadeOwner2,
                occurredAt = now
            )
        )

        // when
        val query = GetScreeningSeats(screeningId = screeningId)

        // then
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 1,
                    column = 0,
                    blockedBy = blockadeOwner1
                ),
                ScreeningSeatsReadModel.Seat(
                    row = 1,
                    column = 1,
                    blockedBy = null
                ),
                ScreeningSeatsReadModel.Seat(
                    row = 1,
                    column = 2,
                    blockedBy = blockadeOwner2
                )
            )
        }
    }

    @Test
    fun `seat blocked without being placed first`() {
        // given
        val now = currentTime()
        val screeningId = ScreeningId.random()
        val seat = SeatNumber(3, 8)
        val blockadeOwner = aBlockadeOwner()

        eventsOccurred(
            SeatBlocked(
                screeningId = screeningId,
                seat = seat,
                blockadeOwner = blockadeOwner,
                occurredAt = now
            )
        )

        // when
        val query = GetScreeningSeats(screeningId = screeningId)

        // then
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 3,
                    column = 8,
                    blockedBy = blockadeOwner
                )
            )
        }
    }

    @Test
    fun `different screenings have separate seats`() {
        // given
        val now = currentTime()
        val screeningId1 = ScreeningId.random()
        val screeningId2 = ScreeningId.random()
        val seat = SeatNumber(1, 1)
        val blockadeOwner = aBlockadeOwner()

        eventsOccurred(
            SeatPlaced(
                screeningId = screeningId1,
                seat = seat,
                occurredAt = now
            ),
            SeatBlocked(
                screeningId = screeningId2,
                seat = seat,
                blockadeOwner = blockadeOwner,
                occurredAt = now
            )
        )

        // when & then - first screening has available seat
        val query1 = GetScreeningSeats(screeningId = screeningId1)
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query1)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 1,
                    column = 1,
                    blockedBy = null
                )
            )
        }

        // when & then - second screening has blocked seat
        val query2 = GetScreeningSeats(screeningId = screeningId2)
        awaitUntilAsserted {
            val result: GetScreeningSeatsResult = executeQuery(query2)
            assertThat(result.items).containsExactlyInAnyOrder(
                ScreeningSeatsReadModel.Seat(
                    row = 1,
                    column = 1,
                    blockedBy = blockadeOwner
                )
            )
        }
    }

    private fun aBlockadeOwner() = "Reservation:${UUID.randomUUID()}"
}