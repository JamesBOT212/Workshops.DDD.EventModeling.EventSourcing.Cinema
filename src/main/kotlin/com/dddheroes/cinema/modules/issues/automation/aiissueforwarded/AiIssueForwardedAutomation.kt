package com.dddheroes.cinema.modules.issues.automation.aiissueforwarded

import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.write.sendnotification.SendAdminNotification
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.ScreeningReadModel
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreenings
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreeningsResult
import com.dddheroes.cinema.modules.dayschedule.write.cancelscreening.CancelScreening
import com.dddheroes.cinema.modules.issues.events.IssueOpened
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.BlockSeat
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.sdk.application.CommandResult
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.logging.log4j.kotlin.logger
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventhandling.async.SequentialPolicy
import org.axonframework.extensions.kotlin.query
import org.axonframework.queryhandling.QueryGateway
import org.springframework.ai.chat.client.ChatClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

data class AiDecision(
    val command: String,
    val properties: Map<String, String> = emptyMap()
)

@ConditionalOnProperty(name = ["slices.issues.automation.aiissueforwarder.enabled"])
@ProcessingGroup("AI_Issue_Forwarder")
@Component
private class AiIssueForwardedAutomation(
    private val chatClientBuilder: ChatClient.Builder,
    private val commandGateway: CommandGateway,
    private val queryGateway: QueryGateway,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    val logger = logger()

    @EventHandler
    fun handle(event: IssueOpened) {
        val chat = chatClientBuilder.build()
        val today = LocalDate.ofInstant(event.occurredAt, clock.zone)

        val analysisPrompt = """
            Analyze the following cinema issue and determine the appropriate action. The cinema has a single screen with seats arranged in a 10x10 grid from (0,0) to (9,9).
            
            Issue: ${event.description}
            
            Respond with JSON in one of these formats:
            1. For admin notification: {"command": "SendAdminNotification", "properties": {"content": "issue description"}}
            2. For screening cancellation (cancels ALL remaining screenings today): {"command": "CancelScreening", "properties": {}}
            3. For seat blocking (blocks seat for ALL remaining screenings today): {"command": "BlockSeat", "properties": {"row": "X", "column": "Y"}}
            
            Examples:
            - "Projector is broken" -> {"command": "CancelScreening", "properties": {}}
            - "Fire alarm activated" -> {"command": "CancelScreening", "properties": {}}
            - "Seat 3,5 has broken armrest" -> {"command": "BlockSeat", "properties": {"row": "3", "column": "5"}}
            - "Spilled drink on seat 1,1" -> {"command": "BlockSeat", "properties": {"row": "1", "column": "1"}}
            - "Bathroom is out of order" -> {"command": "SendAdminNotification", "properties": {"content": "Bathroom is out of order"}}
            - "Technical problem with air conditioning" -> {"command": "SendAdminNotification", "properties": {"content": "Technical problem with air conditioning"}}
            
            Respond with ONLY valid JSON, no additional text.
        """.trimIndent()

        val response = chat.prompt(analysisPrompt).call()
        val jsonResponse = response.content()?.trim() ?: ""

        logger.info("AI response for issue ${event.issueId}: $jsonResponse")

        try {
            val decision = objectMapper.readValue(jsonResponse, AiDecision::class.java)

            when (decision.command) {
                "CancelScreening" -> {
                    val todayScreenings: SearchScreeningsResult = queryGateway.query<SearchScreeningsResult, SearchScreenings>(SearchScreenings(day = today)).join()
                    val commands = cancelAllRemainingScreenings(event, todayScreenings)
                    commands.forEach { command ->
                        val result = commandGateway.sendAndWait<CommandResult>(command)
                        logger.info("Command $command in response to IssueOpened: ${event.issueId}. Result $result")
                    }
                }

                "BlockSeat" -> {
                    val row = decision.properties["row"]!!.toInt()
                    val column = decision.properties["column"]!!.toInt()
                    val todayScreenings: SearchScreeningsResult = queryGateway.query<SearchScreeningsResult, SearchScreenings>(SearchScreenings(day = today)).join()
                    val commands = blockSeatForAllRemainingScreenings(event, todayScreenings, row, column)
                    commands.forEach { command ->
                        val result = commandGateway.sendAndWait<CommandResult>(command)
                        logger.info("Command $command in response to IssueOpened: ${event.issueId}. Result $result")
                    }
                }

                else -> {
                    val command = sendAdminNotification(event)
                    val result = commandGateway.sendAndWait<CommandResult>(command)
                    logger.info("Command $command in response to IssueOpened: ${event.issueId}. Result $result")
                }
            }
        } catch (e: Exception) {
            logger.error("AI action execution failed: ${e.message}")
        }
    }

    private fun sendAdminNotification(event: IssueOpened) = SendAdminNotification(
        notificationId = NotificationId("IssueOpened${event.issueId}"),
        content = "[ACTION REQUIRED] New issue opened!",
        issuedAt = event.occurredAt
    )

    private fun cancelAllRemainingScreenings(event: IssueOpened, todayScreenings: SearchScreeningsResult): List<CancelScreening> {
        val now = event.occurredAt.atZone(clock.zone)
        val currentDay = now.toLocalDate()
        val currentTime = now.toLocalTime()
        
        return remainingScreeningsToday(todayScreenings, currentTime)
            .map { screening ->
                CancelScreening(
                    dayScheduleId = DayScheduleId(currentDay),
                    screeningId = ScreeningId(screening.screeningId),
                    issuedAt = event.occurredAt
                )
            }
    }

    private fun blockSeatForAllRemainingScreenings(event: IssueOpened, todayScreenings: SearchScreeningsResult, seatRow: Int, seatColumn: Int): List<BlockSeat> {
        val currentTime = event.occurredAt.atZone(clock.zone).toLocalTime()
        
        return remainingScreeningsToday(todayScreenings, currentTime)
            .map { screening ->
                BlockSeat(
                    screeningId = ScreeningId(screening.screeningId),
                    seat = SeatNumber(seatRow, seatColumn),
                    blockadeOwner = "Issue:${event.issueId}",
                    issuedAt = event.occurredAt
                )
            }
    }

    private fun remainingScreeningsToday(
        todayScreenings: SearchScreeningsResult,
        currentTime: LocalTime?
    ): List<ScreeningReadModel> = todayScreenings.items
        .filter { it.startTime >= currentTime }

    @Bean
    fun aiIssueForwardedSequencingPolicy() = SequentialPolicy()

}