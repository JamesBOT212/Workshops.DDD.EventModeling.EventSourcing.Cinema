package com.dddheroes.cinema.modules.payments.automation.fakepaymentitegration

import com.dddheroes.cinema.modules.payments.events.PaymentInitiated
import com.dddheroes.cinema.modules.payments.write.ackprocessed.AcknowledgePaymentProcessed
import com.dddheroes.sdk.application.CommandResult
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.EventHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * This is just a fake implementation. But this shows you the great power of Vertical Slice Architecture.
 * You can change the implementation of a whole slice without affecting any other slice.
 * This will be replaced with a real payment gateway integration in the future.
 * Now we assume that every-time a reservation is confirmed, the payment is successful.
 *
 * It allows us to do some feature-flagging.
 */
@ConditionalOnProperty(name = ["slices.payments.automation.fakepayment.enabled"])
@Component
private class FakePaymentAutomation(val commandGateway: CommandGateway) {

    @EventHandler
    fun handle(event: PaymentInitiated) {
        val command = AcknowledgePaymentProcessed(event.paymentId, event.occurredAt)
        commandGateway.sendAndWait<CommandResult>(command).throwIfFailure()
    }
}