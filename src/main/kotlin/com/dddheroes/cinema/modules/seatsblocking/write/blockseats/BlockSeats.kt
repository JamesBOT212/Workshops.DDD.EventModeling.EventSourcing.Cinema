package com.dddheroes.cinema.modules.seatsblocking.write.blockseats

import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatEvent
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotUnblocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.restapi.ErrorResponse
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inMultiStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture

data class BlockSeats(
    val screeningId: ScreeningId,
    val seats: Set<SeatNumber>,
    val blockadeOwner: String,
    val issuedAt: Instant
)

private data class State(val blockadeBySeat: Map<SeatNumber, String?> = emptyMap())

private fun decide(command: BlockSeats, state: State): List<SeatEvent> {
    // Check if seats are placed (exist in state map)
    val seatsNotPlaced = command.seats.filter { seat ->
        !state.blockadeBySeat.containsKey(seat)
    }

    if (seatsNotPlaced.isNotEmpty()) {
        throw IllegalStateException("Cannot block seats - must be placed first")
    }

    val seatsBlockedByOthers = command.seats.filter { seat ->
        val blockedBy = state.blockadeBySeat[seat]
        blockedBy != null && blockedBy != command.blockadeOwner
    }

    if (seatsBlockedByOthers.isNotEmpty()) {
        throw IllegalStateException("Cannot block seats - some seats are already blocked by others: $seatsBlockedByOthers")
    }

    return command.seats.mapNotNull { seat ->
        val blockedBy = state.blockadeBySeat[seat]
        when (blockedBy) {
            null -> SeatBlocked(command.screeningId, seat, command.blockadeOwner, command.issuedAt)
            command.blockadeOwner -> null // Already blocked by same owner, no event needed
            else -> null
        }
    }
}

private fun evolve(state: State, event: SeatEvent): State = when (event) {
    is SeatPlaced -> state.copy(blockadeBySeat = state.blockadeBySeat + (event.seat to null))
    is SeatBlocked -> state.copy(blockadeBySeat = state.blockadeBySeat + (event.seat to event.blockadeOwner))
    is SeatUnblocked -> state.copy(blockadeBySeat = state.blockadeBySeat + (event.seat to null))
    is SeatNotBlocked -> state
    is SeatNotUnblocked -> state
}

@ConditionalOnProperty(name = ["slices.seatsblocking.write.blockseats.enabled"])
@Component
private class BlockSeatsCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: BlockSeats): CommandResult = resultOf {
        val streamIds = command.seats.map { seat ->
            EventStreamId.of("Seat", command.screeningId, seat)
        }

        val events = eventStore.inMultiStreamTransaction<SeatEvent>(streamIds) { events ->
            val currentState = events.fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }
}

@ConditionalOnProperty(name = ["slices.seatsblocking.write.blockseats.enabled"])
@RestController
@RequestMapping("cinema/screenings/{screeningId}")
internal class BlockSeatsRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    data class Body(
        val seats: Set<String>,
        val blockadeOwner: String,
    )

    @PutMapping("/seats-blockades")
    fun putSeatsBlockades(
        @PathVariable screeningId: ScreeningId,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            BlockSeats(
                screeningId = screeningId,
                seats = requestBody.seats.map { SeatNumber.from(it) }.toSet(),
                blockadeOwner = requestBody.blockadeOwner,
                issuedAt = clock.instant()
            )
        ).thenApply {
            when (it) {
                is CommandResult.Success -> ResponseEntity.noContent().build()
                is CommandResult.Failure -> ResponseEntity.badRequest().body(ErrorResponse(it.message))
            }
        }

}
