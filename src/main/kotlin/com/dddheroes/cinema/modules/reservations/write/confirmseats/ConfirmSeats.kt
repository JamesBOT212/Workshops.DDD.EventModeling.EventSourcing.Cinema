package com.dddheroes.cinema.modules.reservations.write.confirmseats

import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationConfirmed
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.events.ReservationEvent
import com.dddheroes.cinema.modules.reservations.events.SeatsConfirmed
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inSingleStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Instant

data class ConfirmSeats(
    val reservationId: ReservationId,
    val seats: Set<SeatNumber>,
    val issuedAt: Instant
)

private data class State(
    val screeningId: ScreeningId? = null,
    val requestedSeats: Set<SeatNumber> = emptySet(),
    val confirmedSeats: Set<SeatNumber> = emptySet()
)

private fun decide(command: ConfirmSeats, state: State): List<ReservationEvent> {
    val seatsToConfirm = command.seats - state.confirmedSeats
    if (state.screeningId == null || seatsToConfirm.isEmpty() || state.confirmedSeats.containsAll(state.requestedSeats)) {
        return emptyList()
    }
    val seatsConfirmed = SeatsConfirmed(command.reservationId, state.screeningId, seatsToConfirm, command.issuedAt)
    return buildList {
        add(seatsConfirmed)
        if ((seatsToConfirm + state.confirmedSeats).containsAll(state.requestedSeats)) {
            add(
                ReservationConfirmed(
                    command.reservationId,
                    state.screeningId,
                    state.requestedSeats,
                    command.issuedAt
                )
            )
        }
    }
}

private fun evolve(state: State, event: ReservationEvent): State =
    when (event) {
        is ReservationStarted -> state.copy(screeningId = event.screeningId, requestedSeats = event.seats)
        is SeatsConfirmed -> state.copy(confirmedSeats = state.confirmedSeats + event.seats)
        else -> state
    }

@ConditionalOnProperty(name = ["slices.reservations.write.confirmseats.enabled"])
@Component
private class ConfirmSeatsCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: ConfirmSeats): CommandResult = resultOf {
        val streamId = EventStreamId.of("Reservation", command.reservationId)

        val events = eventStore.inSingleStreamTransaction<ReservationEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}