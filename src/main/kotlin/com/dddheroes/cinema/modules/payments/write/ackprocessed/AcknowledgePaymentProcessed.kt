package com.dddheroes.cinema.modules.payments.write.ackprocessed

import com.dddheroes.cinema.modules.payments.events.PaymentEvent
import com.dddheroes.cinema.modules.payments.events.PaymentInitiated
import com.dddheroes.cinema.modules.payments.events.PaymentProcessed
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

data class AcknowledgePaymentProcessed(
    val paymentId: PaymentId,
    val issuedAt: Instant
)

private data class State(val initiated: Boolean = false, val processed: Boolean = false)

private fun decide(command: AcknowledgePaymentProcessed, state: State): List<PaymentEvent> = when {
    !state.initiated -> throw IllegalStateException("Cannot process payment that was never initiated")
    state.processed -> emptyList()  // Already processed
    else -> listOf(PaymentProcessed(command.paymentId, command.issuedAt))
}

private fun evolve(state: State, event: PaymentEvent): State = when (event) {
    is PaymentInitiated -> state.copy(initiated = true)
    is PaymentProcessed -> state.copy(processed = true)
    else -> state
}

@ConditionalOnProperty(name = ["slices.payments.write.ackprocessed.enabled"])
@Component
private class AcknowledgePaymentProcessedCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: AcknowledgePaymentProcessed): CommandResult = resultOf {
        val streamId = EventStreamId.of("Payment", command.paymentId)

        val events = eventStore.inSingleStreamTransaction<PaymentEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }
}