package com.dddheroes.cinema.modules.payments.events

import com.dddheroes.cinema.shared.events.CinemaEvent
import com.dddheroes.cinema.shared.valueobjects.PaymentId
import com.dddheroes.sdk.domain.EventStreamId

sealed interface PaymentEvent : CinemaEvent {
    val paymentId: PaymentId

    override val streamId: EventStreamId
        get() = EventStreamId.of("Payment", paymentId)
}

