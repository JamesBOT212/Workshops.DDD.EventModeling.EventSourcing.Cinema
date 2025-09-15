package com.dddheroes.cinema.modules.payments.write.initpayment

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.payments.events.PaymentInitiated
import com.dddheroes.cinema.modules.payments.write.initpayment.InitiatePayment
import com.dddheroes.cinema.shared.valueobjects.PaymentId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["slices.payments.write.initpayment.enabled=true"])
class InitiatePaymentTest : MessagingSpringBootTest() {

    @Test
    fun `initiating payment (1st time) - success`() {
        // given
        val now = currentTime()
        val paymentId = PaymentId.random()

        // when
        val result = executeCommand(InitiatePayment(paymentId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Payment", paymentId)
        val expectedEvent = PaymentInitiated(paymentId, now)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `initiating already initiated payment - nothing`() {
        // given
        val now = currentTime()
        val paymentId = PaymentId.random()
        val events = listOf(
            PaymentInitiated(paymentId, now)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(InitiatePayment(paymentId, now))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Payment", paymentId)
        assertStreamEvents(streamId, events)
    }
}