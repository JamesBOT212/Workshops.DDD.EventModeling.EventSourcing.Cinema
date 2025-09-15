package com.dddheroes.cinema.modules.reservations.events

import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import java.time.Instant

data class ReservationStarted(
    override val reservationId: ReservationId,
    override val screeningId: ScreeningId,
    val seats: Set<SeatNumber>,
    override val occurredAt: Instant
) : ReservationEvent