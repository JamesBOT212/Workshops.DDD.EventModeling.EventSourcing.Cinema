package com.dddheroes.cinema.modules.adminnotifications.write.sendnotification

import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.events.AdminNotificationEvent
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationRead
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationSent
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationStarred
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inSingleStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
import com.dddheroes.sdk.application.toResponseEntity
import com.dddheroes.sdk.domain.EventStreamId
import org.axonframework.commandhandling.CommandHandler
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventsourcing.eventstore.EventStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CompletableFuture

data class SendAdminNotification(
    val notificationId: NotificationId,
    val content: String,
    val issuedAt: Instant
)

private data class State(val sent: Boolean = false)

private fun decide(command: SendAdminNotification, state: State): List<AdminNotificationEvent> {
    return when (state.sent) {
        true -> emptyList()
        false -> listOf(NotificationSent(command.notificationId, command.content, command.issuedAt))
    }
}

private fun evolve(state: State, event: AdminNotificationEvent): State = when (event) {
    is NotificationSent -> state.copy(sent = true)
    is NotificationStarred -> state
    is NotificationRead -> state
}

@ConditionalOnProperty(name = ["slices.adminnotifications.write.sendnotification.enabled"])
@Component
private class SendAdminNotificationCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: SendAdminNotification): CommandResult = resultOf {
        val streamId = EventStreamId.of("AdminNotification", command.notificationId)

        val events = eventStore.inSingleStreamTransaction<AdminNotificationEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.adminnotifications.write.sendnotification.enabled"])
@RestController
@RequestMapping("/cinema/admin/notifications")
internal class SendAdminNotificationRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    data class Body(
        val content: String,
    )

    @PutMapping("/{notificationId}")
    fun putNotification(
        @PathVariable notificationId: NotificationId,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            SendAdminNotification(
                notificationId = notificationId,
                content = requestBody.content,
                issuedAt = clock.instant()
            )
        ).toResponseEntity()

}