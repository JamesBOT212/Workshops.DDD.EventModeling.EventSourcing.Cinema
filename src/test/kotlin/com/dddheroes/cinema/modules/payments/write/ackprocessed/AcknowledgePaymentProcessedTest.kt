package com.dddheroes.cinema.modules.payments.write.ackprocessed

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.payments.events.PaymentInitiated
import com.dddheroes.cinema.modules.payments.events.PaymentProcessed
import com.dddheroes.cinema.modules.payments.write.ackprocessed.AcknowledgePaymentProcessed
import com.dddheroes.cinema.shared.valueobjects.PaymentId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["slices.payments.write.ackprocessed.enabled=true"])
class AcknowledgePaymentProcessedTest : MessagingSpringBootTest() {

    @Test
    fun `acknowledging payment processed for initiated payment - success`() {
        // given
        val now = currentTime()
        val paymentId = PaymentId.random()
        val events = listOf(
            PaymentInitiated(paymentId, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(AcknowledgePaymentProcessed(paymentId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Payment", paymentId)
        val expectedEvent = PaymentProcessed(paymentId, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `acknowledging payment processed for already processed payment - nothing`() {
        // given
        val now = currentTime()
        val paymentId = PaymentId.random()
        val events = listOf(
            PaymentInitiated(paymentId, now),
            PaymentProcessed(paymentId, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(AcknowledgePaymentProcessed(paymentId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Payment", paymentId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `acknowledging payment processed for non-initiated payment - failure`() {
        // given - no events, payment was never initiated
        val now = currentTime()
        val paymentId = PaymentId.random()

        // when
        val result = executeCommand(AcknowledgePaymentProcessed(paymentId, now))

        // then
        val expectedResult = CommandResult.Failure("Cannot process payment that was never initiated")
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Payment", paymentId)
        assertStreamEvents(streamId, emptyList())
    }
}