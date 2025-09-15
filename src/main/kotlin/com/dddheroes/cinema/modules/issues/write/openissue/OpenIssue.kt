package com.dddheroes.cinema.modules.issues.write.openissue

import com.dddheroes.cinema.modules.issues.IssueId
import com.dddheroes.cinema.modules.issues.events.IssueEvent
import com.dddheroes.cinema.modules.issues.events.IssueOpened
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

data class OpenIssue(
    val issueId: IssueId,
    val description: String,
    val issuedAt: Instant
)

private data class State(val opened: Boolean = false)

private fun decide(command: OpenIssue, state: State): List<IssueEvent> {
    return when (state.opened) {
        true -> emptyList()
        false -> listOf(IssueOpened(command.issueId, command.description, command.issuedAt))
    }
}

private fun evolve(state: State, event: IssueEvent): State = when (event) {
    is IssueOpened -> state.copy(opened = true)
}

@ConditionalOnProperty(name = ["slices.issues.write.openissue.enabled"])
@Component
private class OpenIssueCommandHandler(val eventStore: EventStore) {

    @CommandHandler
    fun handle(command: OpenIssue): CommandResult = resultOf {
        val streamId = EventStreamId.of("Issue", command.issueId)

        val events = eventStore.inSingleStreamTransaction<IssueEvent>(streamId) { events ->
            val currentState = events
                .fold(State()) { state, event -> evolve(state, event) }
            decide(command, currentState)
        }

        return events.toCommandResult()
    }

}

@ConditionalOnProperty(name = ["slices.issues.write.openissue.enabled"])
@RestController
@RequestMapping("/cinema/issues")
internal class OpenIssueRestApi(
    private val commandGateway: CommandGateway,
    private val clock: Clock
) {

    data class Body(
        val description: String,
    )

    @PutMapping("/{issueId}")
    fun openIssue(
        @PathVariable issueId: IssueId,
        @RequestBody requestBody: Body
    ): CompletableFuture<ResponseEntity<Any>> =
        commandGateway.send<CommandResult>(
            OpenIssue(
                issueId = issueId,
                description = requestBody.description,
                issuedAt = clock.instant()
            )
        ).toResponseEntity()

}