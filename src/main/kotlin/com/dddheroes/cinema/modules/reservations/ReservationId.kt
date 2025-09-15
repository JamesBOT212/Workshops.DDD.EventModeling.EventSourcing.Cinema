package com.dddheroes.cinema.modules.reservations

import java.util.UUID

@JvmInline
value class ReservationId(val raw: String) {
    override fun toString(): String = raw

    companion object {
        fun random(): ReservationId = ReservationId(UUID.randomUUID().toString())
    }
}
