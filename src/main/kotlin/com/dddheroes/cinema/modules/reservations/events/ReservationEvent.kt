package com.dddheroes.cinema.modules.reservations.events

import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.shared.events.CinemaEvent
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.domain.EventStreamId

sealed interface ReservationEvent : CinemaEvent {
    val reservationId: ReservationId
    val screeningId: ScreeningId

    override val streamId: EventStreamId
        get() = EventStreamId.of("Reservation", reservationId)
}

