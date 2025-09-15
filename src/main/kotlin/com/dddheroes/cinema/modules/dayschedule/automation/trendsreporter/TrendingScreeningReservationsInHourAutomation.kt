package com.dddheroes.cinema.modules.dayschedule.automation.trendsreporter

import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.write.sendnotification.SendAdminNotification
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.shared.events.CinemaEvent
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inSingleStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.domain.EventStreamId
import jakarta.annotation.PostConstruct
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.DisallowReplay
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventhandling.EventMessage
import org.axonframework.eventhandling.async.SequencingPolicy
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.time.Instant

// Explanation: automation implemented with internal Event Sourced state
data class RecordScreeningReservation(val screeningId: ScreeningId, val timestamp: Instant)

private sealed interface TrendingScreeningDetectorEvent : CinemaEvent {
    val screeningId: ScreeningId

    override val streamId: EventStreamId
        get() = EventStreamId.of("ScreeningTrendingDetector", screeningId)
}

private data class ScreeningReservationRecorded(
    override val screeningId: ScreeningId,
    override val occurredAt: Instant
) : TrendingScreeningDetectorEvent;

private data class TrendingScreeningDetected(
    override val screeningId: ScreeningId,
    override val occurredAt: Instant
) : TrendingScreeningDetectorEvent

private data class State(val detected: Boolean = false, val lastHourReservations: List<Instant> = emptyList())

private fun decide(command: RecordScreeningReservation, state: State) = when {
    state.detected -> emptyList()
    else -> buildList {
        add(ScreeningReservationRecorded(command.screeningId, command.timestamp))

        // Check if this reservation makes it trending (2+ reservations in 1 hour)
        val oneHourAgo = command.timestamp.minusSeconds(3600)
        val recentReservations = state.lastHourReservations.filter { it.isAfter(oneHourAgo) }
        val totalReservationsInHour = recentReservations.size + 1 // +1 for current reservation

        if (totalReservationsInHour >= 2) {
            add(TrendingScreeningDetected(command.screeningId, command.timestamp))
        }
    }
}

private fun evolve(state: State, event: TrendingScreeningDetectorEvent): State = when (event) {
    is ScreeningReservationRecorded -> {
        val oneHourAgo = event.occurredAt.minusSeconds(3600)
        val recentTimestamps = state.lastHourReservations.filter { it.isAfter(oneHourAgo) }
        state.copy(lastHourReservations = recentTimestamps + event.occurredAt)
    }

    is TrendingScreeningDetected -> state.copy(detected = true)
}

@ConditionalOnProperty(name = ["slices.dayschedule.automation.trendsreporter.enabled"])
@Component
private class RecordScreeningReservationCommandHandler(private val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: RecordScreeningReservation): CommandResult = resultOf {
        val eventStreamId = EventStreamId.of("ScreeningTrendingDetector", command.screeningId)
        val events = eventStore.inSingleStreamTransaction<TrendingScreeningDetectorEvent>(eventStreamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }
        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.automation.trendsreporter.enabled"])
@Component
@ProcessingGroup("Automation_TrendingScreeningDetector")
private class TrendingScreeningDetectorSaga(val commandGateway: CommandGateway) {

    @DisallowReplay
    @EventHandler
    fun handle(event: SeatBlocked) {
        val commands = react(event)
        commands.forEach {
            commandGateway.sendAndWait<CommandResult>(it).throwIfFailure()
        }
    }

    private fun react(event: SeatBlocked): List<RecordScreeningReservation> {
        val isReservation = event.blockadeOwner.startsWith("Reservation")
        return when (isReservation) {
            true -> listOf(RecordScreeningReservation(event.screeningId, event.occurredAt))
            false -> emptyList()
        }
    }

    @DisallowReplay
    @EventHandler
    fun handle(event: TrendingScreeningDetected) {
        val commands = react(event)
        commands.forEach {
            commandGateway.sendAndWait<CommandResult>(it).throwIfFailure()
        }
    }

    private fun react(event: TrendingScreeningDetected): List<SendAdminNotification> {
        val screeningId = event.screeningId
        val notificationId = NotificationId("trending-screening-$screeningId")
        val command = SendAdminNotification(notificationId, "Screening $screeningId is trending", event.occurredAt)
        return listOf(command)
    }

    @Bean
    fun treningScreeningReservationInHourAutomationSequencingPolicy(): SequencingPolicy<EventMessage<CinemaEvent>> {
        return SequencingPolicy {
            when(val payload = it.payload) {
                is SeatBlocked -> payload.screeningId
                is TrendingScreeningDetected -> payload.screeningId
                else -> throw IllegalArgumentException("Unknown payload type: ${payload::class.simpleName}")
            }
        }
    }

}