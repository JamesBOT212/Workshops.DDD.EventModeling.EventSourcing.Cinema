package com.dddheroes.cinema.modules.seatsblocking.write.placeseat

import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatEvent
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotUnblocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inSingleStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.Instant

data class PlaceSeat(
    val screeningId: ScreeningId,
    val seat: SeatNumber,
    val issuedAt: Instant
)

// Just data needed to make a decision, we don't think about DB tables.
data class State(val placed: Boolean = false)

fun decide(command: PlaceSeat, state: State): List<SeatEvent> = when {
    state.placed -> emptyList() // Seat already placed, no event needed
    else -> listOf(SeatPlaced(command.screeningId, command.seat, command.issuedAt))
}

// Which events have an impact on my decision. What I need to know to make a decision?
fun evolve(state: State, event: SeatEvent): State = when (event) {
    is SeatPlaced -> state.copy(placed = true)
    is SeatBlocked -> state // Best practice! Do not use else.
    is SeatUnblocked -> state
    is SeatNotBlocked -> state
    is SeatNotUnblocked -> state
}

@ConditionalOnProperty(name = ["slices.seatsblocking.write.placeseat.enabled"])
@Component
data class PlaceSeatCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: PlaceSeat): CommandResult = resultOf {
        val streamId = EventStreamId.of("Seat", command.screeningId, command.seat)

        val events = eventStore.inSingleStreamTransaction<SeatEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.seatsblocking.write.placeseat.enabled"])
@RestController
@RequestMapping("cinema/screenings/{screeningId}")
internal class PlaceSeatRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/seats/{seat}")
    fun putSeat(
        @PathVariable screeningId: ScreeningId,
        @PathVariable seat: String
    ): CommandResult =
        commandGateway.sendAndWait<CommandResult>(
            PlaceSeat(
                screeningId = screeningId,
                seat = SeatNumber.from(seat),
                issuedAt = clock.instant()
            )
        ).throwIfFailure()

}