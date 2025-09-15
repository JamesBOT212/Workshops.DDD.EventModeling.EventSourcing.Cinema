package com.dddheroes.cinema.modules.payments.events

import com.dddheroes.cinema.shared.valueobjects.PaymentId
import com.dddheroes.sdk.domain.FailureEvent
import java.time.Instant

data class PaymentFailed(
    override val paymentId: PaymentId,
    override val occurredAt: Instant,
    override val reason: String
) : PaymentEvent, FailureEvent