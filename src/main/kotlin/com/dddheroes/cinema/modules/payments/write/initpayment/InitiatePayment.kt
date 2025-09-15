package com.dddheroes.cinema.modules.payments.write.initpayment

import com.dddheroes.cinema.modules.payments.events.PaymentEvent
import com.dddheroes.cinema.modules.payments.events.PaymentInitiated
import com.dddheroes.cinema.shared.valueobjects.PaymentId
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

data class InitiatePayment(
    val paymentId: PaymentId,
    val issuedAt: Instant
)

private data class State(val initiated: Boolean = false)

private fun decide(command: InitiatePayment, state: State): List<PaymentEvent> = when {
    state.initiated -> emptyList()
    else -> listOf(PaymentInitiated(command.paymentId, command.issuedAt))
}

private fun evolve(state: State, event: PaymentEvent): State = when (event) {
    is PaymentInitiated -> state.copy(initiated = true)
    else -> state
}

@ConditionalOnProperty(name = ["slices.payments.write.initpayment.enabled"])
@Component
private class InitiatePaymentCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: InitiatePayment): CommandResult = resultOf {
        val streamId = EventStreamId.of("Payment", command.paymentId)

        val events = eventStore.inSingleStreamTransaction<PaymentEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }
}