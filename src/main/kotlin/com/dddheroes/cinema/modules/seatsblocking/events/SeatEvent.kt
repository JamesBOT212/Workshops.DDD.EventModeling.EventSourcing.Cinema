package com.dddheroes.cinema.modules.seatsblocking.events

import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.events.CinemaEvent
import com.dddheroes.sdk.domain.EventStreamId

sealed interface SeatEvent : CinemaEvent {
    val screeningId: ScreeningId
    val seat: SeatNumber

    override val streamId: EventStreamId
        get() = EventStreamId.of("Seat", screeningId, seat)
}