package com.dddheroes.cinema.modules.dayschedule.write.schedulescreening

import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleEvent
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningCancelled
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.modules.dayschedule.write.schedulescreening.State.Screening
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inSingleStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

data class ScheduleScreening(
    val dayScheduleId: DayScheduleId,
    val screeningId: ScreeningId,
    val movieId: MovieId,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val occurredAt: Instant
)

private data class State(val day: LocalDate? = null, val screenings: Set<Screening> = setOf()) {
    data class Screening(val screeningId: ScreeningId, val startTime: LocalTime, val endTime: LocalTime)
}

private fun decide(command: ScheduleScreening, state: State): List<DayScheduleEvent> {
    if (state.day == null) {
        throw IllegalStateException("Day schedule with id ${command.dayScheduleId} does not exist")
    }
    if (command.startTime >= command.endTime) {
        throw IllegalArgumentException("Screening start time must be before end time")
    }
    if (state.screenings.any { it.screeningId == command.screeningId }) {
        throw IllegalArgumentException("Screening with id ${command.screeningId} already scheduled")
    }
    if (state.screenings.any { it.startTime < command.endTime && command.startTime < it.endTime }) {
        throw IllegalArgumentException("Screening with time ${command.startTime} - ${command.endTime} overlaps with existing screening")
    }
    return listOf(
        ScreeningScheduled(
            dayScheduleId = command.dayScheduleId,
            screeningId = command.screeningId,
            movieId = command.movieId,
            startTime = command.startTime,
            endTime = command.endTime,
            occurredAt = command.occurredAt
        )
    )
}

private fun evolve(state: State, event: DayScheduleEvent): State {
    return when (event) {
        is DayScheduleCreated -> state.copy(day = event.dayScheduleId.toLocalDate())

        is ScreeningScheduled -> state.copy(
            screenings = state.screenings + Screening(
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

private class ScheduleScreeningCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: ScheduleScreening): CommandResult = resultOf {
        val streamId = EventStreamId.of("DaySchedule", command.dayScheduleId)

        val events = eventStore.inSingleStreamTransaction<DayScheduleEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

private class ScheduleScreeningMcpTool(
    private val commandGateway: CommandGateway,
    private val clock: Clock,
) {

    @Tool(
        name = "schedule_screening",
        description = "Schedule a new movie screening for a specific day and time slot in the cinema"
    )
    fun scheduleScreening(
        @ToolParam(description = "Date for the screening in YYYY-MM-DD format", required = true) date: String,
        @ToolParam(description = "Unique identifier for the screening", required = true) screeningId: String,
        @ToolParam(description = "Unique identifier for the movie", required = true) movieId: String,
        @ToolParam(description = "Start time in HH:MM format (24-hour)", required = true) startTime: String,
        @ToolParam(description = "End time in HH:MM format (24-hour)", required = true) endTime: String
    ): Map<String, Any> {
        val command = ScheduleScreening(
            dayScheduleId = DayScheduleId(LocalDate.parse(date)),
            screeningId = ScreeningId(screeningId),
            movieId = MovieId(movieId),
            startTime = LocalTime.parse(startTime),
            endTime = LocalTime.parse(endTime),
            occurredAt = Instant.now(clock)
        )

        return when (val result = commandGateway.sendAndWait<CommandResult>(command)) {
            is CommandResult.Success -> mapOf(
                "status" to "success",
                "message" to "Screening scheduled successfully",
                "data" to mapOf(
                    "screeningId" to screeningId,
                    "movieId" to movieId,
                    "date" to date,
                    "startTime" to startTime,
                    "endTime" to endTime
                )
            )

            is CommandResult.Failure -> mapOf(
                "status" to "error",
                "message" to result.message
            )
        }
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.write.schedulescreening.enabled"])
@Configuration
private class ScheduleScreeningConfiguration(
    private val clock: Clock,
    private val eventStore: EventStore,
    private val commandGateway: CommandGateway,
) {

    @Bean
    fun scheduleScreeningCommandHandler(): ScheduleScreeningCommandHandler =
        ScheduleScreeningCommandHandler(eventStore)

    @Bean
    fun scheduleScreeningMcpTool() =
        MethodToolCallbackProvider.builder()
            .toolObjects(ScheduleScreeningMcpTool(commandGateway, clock))
            .build();
}
