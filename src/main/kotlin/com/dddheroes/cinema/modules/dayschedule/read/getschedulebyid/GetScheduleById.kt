package com.dddheroes.cinema.modules.dayschedule.read.getschedulebyid

import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleCreated
import com.dddheroes.cinema.modules.dayschedule.events.DayScheduleEvent
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningCancelled
import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.sourceSingle
import com.dddheroes.sdk.domain.EventStreamId
import org.axonframework.eventsourcing.eventstore.EventStore
import org.axonframework.extensions.kotlin.query
import org.axonframework.queryhandling.QueryGateway
import org.axonframework.queryhandling.QueryHandler
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.CompletableFuture

/**
 * Read Model implementations are more technical - adjusted to the underlying storage implementation.
 */
data class GetScheduleById(val id: DayScheduleId)
data class GetScheduleByIdResult(
    val id: DayScheduleId,
    val day: LocalDate,
    val openingTime: LocalTime,
    val closingTime: LocalTime,
    val screeningTimes: List<Screening>
) {
    data class Screening(
        val screeningId: ScreeningId,
        val movieId: MovieId,
        val startTime: LocalTime,
        val endTime: LocalTime
    )
}

private fun evolve(state: GetScheduleByIdResult?, event: DayScheduleEvent) = when (event) {
    is DayScheduleCreated -> GetScheduleByIdResult(
        event.dayScheduleId,
        event.dayScheduleId.toLocalDate(),
        event.openingTime,
        event.closingTime,
        screeningTimes = emptyList()
    )

    is ScreeningScheduled -> state!!.copy(
        screeningTimes = state.screeningTimes + GetScheduleByIdResult.Screening(
            event.screeningId,
            event.movieId,
            event.startTime,
            event.endTime
        )
    )

    is ScreeningCancelled -> state!!.copy(screeningTimes = state.screeningTimes.filter { it.screeningId != event.screeningId })
}

/**
 * On-demand projection implementation. We source events from a single stream and build it upon every request.
 */
private class GetScheduleByIdQueryHandler(private val eventStore: EventStore) {

    @QueryHandler
    fun handle(query: GetScheduleById): GetScheduleByIdResult? {
        val events = eventStore.sourceSingle<DayScheduleEvent>(
            EventStreamId.of("DaySchedule", query.id)
        )
        if (events.isEmpty()) {
            return null
        }
        return events.fold(null, ::evolve)
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.read.getschedulebyid.enabled"])
@RestController
@RequestMapping("/cinema")
internal class GetScheduleByIdRestApi(private val queryGateway: QueryGateway) {

    @GetMapping("/schedules/{scheduleId}")
    fun getSchedule(
        @PathVariable scheduleId: String
    ): CompletableFuture<ResponseEntity<GetScheduleByIdResult>> = queryGateway
        .query<GetScheduleByIdResult?, GetScheduleById>(GetScheduleById(DayScheduleId.of(scheduleId)))
        .thenApply { it?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build() }

}

private class GetScheduleByIdMcpTool(
    private val queryGateway: QueryGateway,
) {

    @Tool(
        name = "get_schedule_by_date",
        description = "Get the cinema schedule for a specific date including all screenings"
    )
    fun getScheduleByDate(
        @ToolParam(description = "Date for the schedule in YYYY-MM-DD format", required = true) date: String
    ): Map<String, Any> {
        return try {
            val dayScheduleId = DayScheduleId(LocalDate.parse(date))
            val result = queryGateway.query<GetScheduleByIdResult?, GetScheduleById>(
                GetScheduleById(dayScheduleId)
            ).join()

            if (result != null) {
                mapOf(
                    "status" to "success",
                    "data" to mapOf(
                        "date" to result.day.toString(),
                        "openingTime" to result.openingTime.toString(),
                        "closingTime" to result.closingTime.toString(),
                        "screenings" to result.screeningTimes.map { screening ->
                            mapOf(
                                "screeningId" to screening.screeningId.raw,
                                "movieId" to screening.movieId.raw,
                                "startTime" to screening.startTime.toString(),
                                "endTime" to screening.endTime.toString()
                            )
                        }
                    )
                )
            } else {
                mapOf(
                    "status" to "not_found",
                    "message" to "No schedule found for date $date"
                )
            }
        } catch (e: Exception) {
            mapOf(
                "status" to "error",
                "message" to "Failed to retrieve schedule: ${e.message}"
            )
        }
    }

}

@ConditionalOnProperty(name = ["slices.dayschedule.read.getschedulebyid.enabled"])
@Configuration
private class GetScheduleByIdConfiguration(
    private val eventStore: EventStore,
    private val queryGateway: QueryGateway,
) {

    @Bean
    fun getScheduleByIdQueryHandler(): GetScheduleByIdQueryHandler =
        GetScheduleByIdQueryHandler(eventStore)

    @Bean
    fun getScheduleByIdMcpTool() =
        MethodToolCallbackProvider.builder()
            .toolObjects(GetScheduleByIdMcpTool(queryGateway))
            .build()
}





