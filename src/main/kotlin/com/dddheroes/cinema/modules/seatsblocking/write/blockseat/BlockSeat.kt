package com.dddheroes.cinema.modules.seatsblocking.write.blockseat

import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatEvent
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotUnblocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
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

data class BlockSeat(
    val screeningId: ScreeningId,
    val seat: SeatNumber,
    val blockadeOwner: String,
    val issuedAt: Instant
)

internal data class State(val placed: Boolean = false, val blockedBy: String? = null)

internal fun decide(command: BlockSeat, state: State): List<SeatEvent> = listOf()

internal fun evolve(state: State, event: SeatEvent): State = state

@ConditionalOnProperty(name = ["slices.seatsblocking.write.blockseat.enabled"])
@Component
private class BlockSeatCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: BlockSeat): CommandResult = resultOf {
        // todo: implement application layer
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
        @PathVariable screeningId: ScreeningId,
        @PathVariable seat: String,
        @RequestBody requestBody: Body
    ): CommandResult = CommandResult.Success // todo: execute command

}