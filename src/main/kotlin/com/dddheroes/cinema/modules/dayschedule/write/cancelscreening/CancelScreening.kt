package com.dddheroes.cinema.modules.dayschedule.write.cancelscreening

import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleEvent
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningCancelled
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inSingleStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.application.toResponseEntity
import com.dddheroes.sdk.domain.EventStreamId
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CompletableFuture

data class CancelScreening(
    val dayScheduleId: DayScheduleId,
    val screeningId: ScreeningId,
    val issuedAt: Instant
)

private data class State(val day: LocalDate? = null, val screenings: Set<Screening> = setOf()) {
    data class Screening(val screeningId: ScreeningId, val startTime: LocalTime, val endTime: LocalTime)
}

private fun decide(command: CancelScreening, state: State): List<DayScheduleEvent> {
    if (state.day == null) {
        throw IllegalStateException("Day schedule with id ${command.dayScheduleId} does not exist")
    }
    
    val screeningToCancel = state.screenings.find { it.screeningId == command.screeningId }
        ?: return emptyList() // Idempotent: already cancelled or never existed
    
    return listOf(
        ScreeningCancelled(
            dayScheduleId = command.dayScheduleId,
            screeningId = screeningToCancel.screeningId,
            occurredAt = command.issuedAt
        )
    )
}

private fun evolve(state: State, event: DayScheduleEvent): State {
    return when (event) {
        is DayScheduleCreated -> state.copy(day = event.dayScheduleId.toLocalDate())

        is ScreeningScheduled -> state.copy(
            screenings = state.screenings + State.Screening(
                screeningId = event.screeningId,
                startTime = event.startTime,
                endTime = event.endTime
            )
        )

        is ScreeningCancelled -> state.copy(
            screenings = state.screenings.filter { it.screeningId != event.screeningId }.toSet()
        )
    }
}

private class CancelScreeningCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: CancelScreening): CommandResult = resultOf {
        val streamId = EventStreamId.of("DaySchedule", command.dayScheduleId)

        val events = eventStore.inSingleStreamTransaction<DayScheduleEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.write.cancelscreening.enabled"])
@RestController
@RequestMapping("/cinema/schedules")
internal class CancelScreeningRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    @DeleteMapping("/{dayScheduleId}/screenings/{screeningId}")
    fun cancelScreening(
        @PathVariable dayScheduleId: DayScheduleId,
        @PathVariable screeningId: ScreeningId
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            CancelScreening(
                dayScheduleId = dayScheduleId,
                screeningId = screeningId,
                issuedAt = clock.instant()
            )
        ).toResponseEntity()

}

private class CancelScreeningMcpTool(
    private val commandGateway: CommandGateway,
    private val clock: Clock,
) {

    @Tool(
        name = "cancel_screening",
        description = "Cancel a scheduled movie screening"
    )
    fun cancelScreening(
        @ToolParam(description = "Date for the schedule in YYYY-MM-DD format", required = true) date: String,
        @ToolParam(description = "Unique identifier for the screening to cancel", required = true) screeningId: String
    ): Map<String, Any> {
        val command = CancelScreening(
            dayScheduleId = DayScheduleId(LocalDate.parse(date)),
            screeningId = ScreeningId(screeningId),
            issuedAt = Instant.now(clock)
        )

        return when (val result = commandGateway.sendAndWait<CommandResult>(command)) {
            is CommandResult.Success -> mapOf(
                "status" to "success",
                "message" to "Screening cancelled successfully",
                "data" to mapOf(
                    "date" to date,
                    "screeningId" to screeningId
                )
            )

            is CommandResult.Failure -> mapOf(
                "status" to "error",
                "message" to result.message
            )
        }
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.write.cancelscreening.enabled"])
@Configuration
private class CancelScreeningConfiguration(
    private val clock: Clock,
    private val eventStore: EventStore,
    private val commandGateway: CommandGateway,
) {

    @Bean
    fun cancelScreeningCommandHandler(): CancelScreeningCommandHandler =
        CancelScreeningCommandHandler(eventStore)

    @Bean
    fun cancelScreeningMcpTool() =
        MethodToolCallbackProvider.builder()
            .toolObjects(CancelScreeningMcpTool(commandGateway, clock))
            .build()
}