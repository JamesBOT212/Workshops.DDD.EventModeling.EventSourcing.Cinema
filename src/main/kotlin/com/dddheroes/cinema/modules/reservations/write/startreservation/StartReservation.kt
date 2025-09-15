package com.dddheroes.cinema.modules.reservations.write.startreservation

import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.events.ReservationEvent
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

data class StartReservation(
    val reservationId: ReservationId,
    val screeningId: ScreeningId,
    val seats: Set<SeatNumber>,
    val issuedAt: Instant
)

private data class State(val started: Boolean = false)

private fun decide(command: StartReservation, state: State): List<ReservationEvent> =
    if (state.started) {
        emptyList()
    } else {
        listOf(ReservationStarted(command.reservationId, command.screeningId, command.seats, command.issuedAt))
    }

private fun evolve(state: State, event: ReservationEvent): State =
    when (event) {
        is ReservationStarted -> state.copy(started = true)
        else -> state
    }

@ConditionalOnProperty(name = ["slices.reservations.write.startreservation.enabled"])
@Component
private class StartReservationCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: StartReservation): CommandResult = resultOf {
        val streamId = EventStreamId.of("Reservation", command.reservationId)

        val events = eventStore.inSingleStreamTransaction<ReservationEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.reservations.write.startreservation.enabled"])
@RestController
@RequestMapping("cinema/screenings/{screeningId}")
internal class StartReservationRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    data class Body(
        val seats: Set<SeatNumber>
    )

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/reservations/{reservationId}")
    fun putReservation(
        @PathVariable screeningId: ScreeningId,
        @PathVariable reservationId: ReservationId,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            StartReservation(
                reservationId = reservationId,
                screeningId = screeningId,
                seats = requestBody.seats,
                issuedAt = clock.instant()
            )
        ).thenApply {
            when (it) {
                is CommandResult.Success -> ResponseEntity.noContent().build()
                is CommandResult.Failure -> ResponseEntity.badRequest().body(ErrorResponse(it.message))
            }
        }

}