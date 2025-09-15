package com.dddheroes.cinema.modules.payments.events

import com.dddheroes.cinema.shared.valueobjects.PaymentId
import java.time.Instant

data class PaymentInitiated(
    override val paymentId: PaymentId,
    override val occurredAt: Instant
) : PaymentEvent