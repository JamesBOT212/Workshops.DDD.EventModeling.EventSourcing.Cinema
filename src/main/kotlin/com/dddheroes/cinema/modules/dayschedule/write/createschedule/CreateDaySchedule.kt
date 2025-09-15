package com.dddheroes.cinema.modules.dayschedule.write.createschedule

import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleEvent
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

data class CreateDaySchedule(
    val dayScheduleId: DayScheduleId,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val issuedAt: Instant
)

private data class State(val created: Boolean = false)

private fun decide(command: CreateDaySchedule, state: State): List<DayScheduleEvent> {
    return when (state.created) {
        true -> emptyList() // Idempotent: schedule already created
        false -> listOf(
            DayScheduleCreated(
                dayScheduleId = command.dayScheduleId,
                openingTime = command.openingTime,
                closingTime = command.closingTime,
                occurredAt = command.issuedAt
            )
        )
    }
}

private fun evolve(state: State, event: DayScheduleEvent): State = when (event) {
    is DayScheduleCreated -> state.copy(created = true)
    else -> state
}

private class CreateDayScheduleCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: CreateDaySchedule): CommandResult = resultOf {
        val streamId = EventStreamId.of("DaySchedule", command.dayScheduleId)

        val events = eventStore.inSingleStreamTransaction<DayScheduleEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.write.createschedule.enabled"])
@RestController
@RequestMapping("/cinema/schedules")
internal class CreateDayScheduleRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    data class Body(
        val openingTime: LocalTime,
        val closingTime: LocalTime,
    )

    @PutMapping("/{dayScheduleId}")
    fun putDaySchedule(
        @PathVariable dayScheduleId: DayScheduleId,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            CreateDaySchedule(
                dayScheduleId = dayScheduleId,
                openingTime = requestBody.openingTime,
                closingTime = requestBody.closingTime,
                issuedAt = clock.instant()
            )
        ).toResponseEntity()

}

private class CreateDayScheduleMcpTool(
    private val commandGateway: CommandGateway,
    private val clock: Clock,
) {

    @Tool(
        name = "create_day_schedule",
        description = "Create a new day schedule for the cinema with opening and closing times"
    )
    fun createDaySchedule(
        @ToolParam(description = "Date for the schedule in YYYY-MM-DD format", required = true) date: String,
        @ToolParam(description = "Opening time in HH:MM format (24-hour)", required = true) openingTime: String,
        @ToolParam(description = "Closing time in HH:MM format (24-hour)", required = true) closingTime: String
    ): Map<String, Any> {
        val command = CreateDaySchedule(
            dayScheduleId = DayScheduleId(LocalDate.parse(date)),
            openingTime = LocalTime.parse(openingTime),
            closingTime = LocalTime.parse(closingTime),
            issuedAt = Instant.now(clock)
        )

        return when (val result = commandGateway.sendAndWait<CommandResult>(command)) {
            is CommandResult.Success -> mapOf(
                "status" to "success",
                "message" to "Day schedule created successfully",
                "data" to mapOf(
                    "date" to date,
                    "openingTime" to openingTime,
                    "closingTime" to closingTime
                )
            )

            is CommandResult.Failure -> mapOf(
                "status" to "error",
                "message" to result.message
            )
        }
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.write.createschedule.enabled"])
@Configuration
private class CreateDayScheduleConfiguration(
    private val clock: Clock,
    private val eventStore: EventStore,
    private val commandGateway: CommandGateway,
) {

    @Bean
    fun createDayScheduleCommandHandler(): CreateDayScheduleCommandHandler =
        CreateDayScheduleCommandHandler(eventStore)

    @Bean
    fun createDayScheduleMcpTool() =
        MethodToolCallbackProvider.builder()
            .toolObjects(CreateDayScheduleMcpTool(commandGateway, clock))
            .build()
}

