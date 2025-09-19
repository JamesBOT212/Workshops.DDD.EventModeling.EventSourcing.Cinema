package com.dddheroes.cinema.modules.seatsblocking.write.blockseat

import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatEvent
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotUnblocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant

data class BlockSeat(
    val screeningId: ScreeningId, val seat: SeatNumber, val blockadeOwner: String, val issuedAt: Instant
)

internal data class State(val placed: Boolean = false, val blockedBy: String? = null)

internal fun decide(command: BlockSeat, state: State): List<SeatEvent> {
    if (!state.placed) {
        return listOf(
            SeatNotBlocked(
                command.screeningId,
                command.seat,
                "Seat must be placed before it can be blocked",
                command.blockadeOwner,
                command.issuedAt
            )
        )
    }
    if (state.blockedBy.equals(command.blockadeOwner)) {
        return emptyList()
    }
    if (state.blockedBy != null && state.blockedBy != command.blockadeOwner) {
        return listOf(
            SeatNotBlocked(
                command.screeningId,
                command.seat,
                "Seat is already blocked by ${state.blockedBy}",
                command.blockadeOwner,
                command.issuedAt
            )
        )
    }
    return listOf(
        SeatBlocked(
            command.screeningId, command.seat, command.blockadeOwner, command.issuedAt
        )
    )
}

internal fun evolve(state: State, event: SeatEvent): State {
    return when (event) {
        is SeatBlocked -> state.copy(blockedBy = event.blockadeOwner)
        is SeatNotBlocked -> state
        is SeatNotUnblocked -> state
        is SeatPlaced -> state.copy(placed = true)
        is SeatUnblocked -> state.copy(blockedBy = null)
    }
}

@ConditionalOnProperty(name = ["slices.seatsblocking.write.blockseat.enabled"])
@Component
private class BlockSeatCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: BlockSeat): CommandResult = resultOf {
        val streamId = EventStreamId.of("Seat", command.screeningId, command.seat)

        val events = eventStore.inSingleStreamTransaction<SeatEvent>(streamId) { events ->
            val currentState = events.fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.seatsblocking.write.blockseat.enabled"])
@RestController
@RequestMapping("cinema/screenings/{screeningId}")
internal class BlockSeatRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    data class Body(
        val blockadeOwner: String,
    )

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/seats-blockades/{seat}")
    fun putSeatBlockade(
        @PathVariable screeningId: ScreeningId, @PathVariable seat: String, @RequestBody requestBody: Body
    ): CommandResult {

        return commandGateway.sendAndWait<CommandResult>(
            BlockSeat(screeningId, SeatNumber.from(seat), requestBody.blockadeOwner, Instant.now(clock))
        ).throwIfFailure()
    }
}
