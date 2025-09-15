package com.dddheroes.cinema.modules.reservations.write.collectreservation

import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationEvent
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.events.ReservationConfirmed
import com.dddheroes.cinema.modules.reservations.events.ReservationCancelled
import com.dddheroes.cinema.modules.reservations.events.ReservationCollected
import com.dddheroes.cinema.shared.restapi.ErrorResponse
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
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture

data class CollectReservation(
    val reservationId: ReservationId,
    val issuedAt: Instant
)

enum class ReservationStatus {
    STARTED,
    CONFIRMED, 
    CANCELLED,
    COLLECTED
}

private data class State(
    val screeningId: ScreeningId? = null,
    val seats: Set<SeatNumber> = emptySet(),
    val status: ReservationStatus? = null
)

private fun decide(command: CollectReservation, state: State): List<ReservationEvent> =
    when {
        state.status == ReservationStatus.COLLECTED -> emptyList()
        state.status == ReservationStatus.CANCELLED -> emptyList()
        state.status != ReservationStatus.CONFIRMED -> emptyList()
        state.screeningId == null -> emptyList()
        else -> listOf(
            ReservationCollected(
                reservationId = command.reservationId,
                screeningId = state.screeningId,
                seats = state.seats,
                occurredAt = command.issuedAt
            )
        )
    }

private fun evolve(state: State, event: ReservationEvent): State =
    when (event) {
        is ReservationStarted -> state.copy(
            screeningId = event.screeningId,
            seats = event.seats,
            status = ReservationStatus.STARTED
        )
        is ReservationConfirmed -> state.copy(status = ReservationStatus.CONFIRMED)
        is ReservationCancelled -> state.copy(status = ReservationStatus.CANCELLED)
        is ReservationCollected -> state.copy(status = ReservationStatus.COLLECTED)
        else -> state
    }

@ConditionalOnProperty(name = ["slices.reservations.write.collectreservation.enabled"])
@Component
private class CollectReservationCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: CollectReservation): CommandResult = resultOf {
        val streamId = EventStreamId.of("Reservation", command.reservationId)

        val events = eventStore.inSingleStreamTransaction<ReservationEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.reservations.write.collectreservation.enabled"])
@RestController
@RequestMapping("cinema/reservations/{reservationId}")
internal class CollectReservationRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/collect")
    fun postCollectReservation(
        @PathVariable reservationId: ReservationId
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            CollectReservation(
                reservationId = reservationId,
                issuedAt = clock.instant()
            )
        ).thenApply {
            when (it) {
                is CommandResult.Success -> ResponseEntity.noContent().build()
                is CommandResult.Failure -> ResponseEntity.badRequest().body(ErrorResponse(it.message))
            }
        }

}