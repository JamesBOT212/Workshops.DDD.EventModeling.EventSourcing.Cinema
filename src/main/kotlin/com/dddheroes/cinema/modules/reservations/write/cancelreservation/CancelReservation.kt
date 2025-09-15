package com.dddheroes.cinema.modules.reservations.write.cancelreservation

import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationEvent
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.events.ReservationConfirmed
import com.dddheroes.cinema.modules.reservations.events.ReservationCancelled
import com.dddheroes.cinema.modules.reservations.events.ReservationCollected
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inSingleStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.application.toResponseEntity
import com.dddheroes.sdk.domain.EventStreamId
import com.dddheroes.sdk.domain.DomainRuleViolatedException
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

data class CancelReservation(
    val reservationId: ReservationId,
    val reason: String,
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

private fun decide(command: CancelReservation, state: State): List<ReservationEvent> =
    when {
        state.status == null -> throw DomainRuleViolatedException("Cannot cancel reservation that has not been started")
        state.status == ReservationStatus.CANCELLED -> emptyList()
        state.status == ReservationStatus.COLLECTED -> throw DomainRuleViolatedException("Cannot cancel reservation that has already been collected")
        state.screeningId == null -> throw DomainRuleViolatedException("Cannot cancel reservation without screening information")
        else -> listOf(
            ReservationCancelled(
                reservationId = command.reservationId,
                screeningId = state.screeningId,
                seats = state.seats,
                reason = command.reason,
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

@ConditionalOnProperty(name = ["slices.reservations.write.cancelreservation.enabled"])
@Component
private class CancelReservationCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: CancelReservation): CommandResult = resultOf {
        val streamId = EventStreamId.of("Reservation", command.reservationId)

        val events = eventStore.inSingleStreamTransaction<ReservationEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.reservations.write.cancelreservation.enabled"])
@RestController
@RequestMapping("cinema/reservations/{reservationId}")
internal class CancelReservationRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    data class Body(
        val reason: String
    )

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    fun deleteReservation(
        @PathVariable reservationId: ReservationId,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            CancelReservation(
                reservationId = reservationId,
                reason = requestBody.reason,
                issuedAt = clock.instant()
            )
        ).toResponseEntity()

}