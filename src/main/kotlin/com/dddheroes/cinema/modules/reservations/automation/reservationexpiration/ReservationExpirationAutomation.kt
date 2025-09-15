package com.dddheroes.cinema.modules.reservations.automation.reservationexpiration

import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreenings
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreeningsResult
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.events.ReservationCollected
import com.dddheroes.cinema.modules.reservations.events.ReservationStarted
import com.dddheroes.cinema.modules.reservations.write.cancelreservation.CancelReservation
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.deadline.DeadlineManager
import org.axonframework.eventhandling.DisallowReplay
import org.axonframework.eventhandling.EventHandler
import org.axonframework.extensions.kotlin.query
import org.axonframework.modelling.saga.SagaScopeDescriptor
import org.axonframework.queryhandling.QueryGateway
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Explanation:
 * The cinema expiration policy.
 * We leverage the underlying Axon Framework to schedule a deadline for the reservation.
 * If the reservation is not canceled before the deadline, the seats will be automatically blocked.
 * It can be implemented alternatively with a Durable Execution of the Passage of Time Events https://verraes.net/2019/05/patterns-for-decoupling-distsys-passage-of-time-event/.
 */
@ConditionalOnProperty(name = ["slices.reservations.automation.reservationexpiration.enabled"])
@DisallowReplay
@Component
class ReservationExpirationAutomation(
    val commandGateway: CommandGateway,
    val queryGateway: QueryGateway,
    val deadlineManager: DeadlineManager,
    val clock: Clock
) {

    @EventHandler
    fun handle(event: ReservationStarted) {
        val screeningId = event.screeningId
        val reservationId = event.reservationId
        // Explanation: trade-off, we use the ReadModel build by another slice, but we can build our own for the needs of the automation
        val screening = queryGateway.query<SearchScreeningsResult, SearchScreenings>(
            SearchScreenings(screeningId = screeningId)
        ).join().items.first()
        val screeningDay: LocalDate = screening.day
        val screeningStartTime: LocalTime = screening.startTime
        val screeningStart = screeningDay.atTime(screeningStartTime).atZone(clock.zone).toInstant()

        val cancelReservationAt = screeningStart.minus(15, ChronoUnit.MINUTES)
        scheduleReservationExpiration(reservationId, cancelReservationAt)
    }

    private fun scheduleReservationExpiration(
        reservationId: ReservationId,
        cancelReservationAt: Instant
    ) {
        deadlineManager.schedule(
            cancelReservationAt,
            "ReservationExpiration",
            CancelReservation(reservationId, "Reservation expired.", cancelReservationAt),
            SagaScopeDescriptor("ReservationExpirationAutomation", reservationId.raw)
        )
    }

    @EventHandler
    fun handle(event: ReservationCollected) {
        cancelDeadlineExpiration(event.reservationId)
    }

    private fun cancelDeadlineExpiration(
        reservationId: ReservationId
    ) {
        deadlineManager.cancelAllWithinScope(
            "ReservationExpiration",
            SagaScopeDescriptor("ReservationExpirationAutomation", reservationId.raw)
        )
    }

}