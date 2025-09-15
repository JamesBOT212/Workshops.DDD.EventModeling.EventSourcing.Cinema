package com.dddheroes.cinema.modules.issues.write.openissue

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.issues.IssueId
import com.dddheroes.cinema.modules.issues.events.IssueOpened
import com.dddheroes.cinema.modules.issues.write.openissue.OpenIssue
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.domain.EventStreamId
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.util.*

@TestPropertySource(properties = ["slices.issues.write.openissue.enabled=true"])
class OpenIssueTest : MessagingSpringBootTest() {

    @Test
    fun `opening issue (1st time) - success`() {
        // given - no events, issue not opened yet

        // when
        val issueId = IssueId.random()
        val description = anIssueDescription()
        val issuedAt = Instant.now()
        val result = executeCommand(OpenIssue(issueId, description, issuedAt))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Issue", issueId)
        val expectedEvent = IssueOpened(issueId, description, issuedAt)
        assertLastStreamEvent(streamId, expectedEvent)
    }

    @Test
    fun `opening same issue again - nothing`() {
        // given
        val issueId = IssueId.random()
        val description = anIssueDescription()
        val issuedAt = Instant.now()
        val events = listOf(
            IssueOpened(issueId, description, issuedAt)
        )
        eventsOccurred(events)

        // when
        val result = executeCommand(OpenIssue(issueId, description, issuedAt))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Issue", issueId)
        assertStreamEvents(streamId, events)
    }

    @Test
    fun `opening same issue with different description - nothing`() {
        // given
        val issueId = IssueId.random()
        val originalDescription = anIssueDescription()
        val issuedAt = Instant.now()
        val events = listOf(
            IssueOpened(issueId, originalDescription, issuedAt)
        )
        eventsOccurred(events)

        // when
        val differentDescription = anIssueDescription()
        val differentIssuedAt = Instant.now()
        val result = executeCommand(OpenIssue(issueId, differentDescription, differentIssuedAt))

        // then
        val expectedResult = CommandResult.Success
        assertThat(result).isEqualTo(expectedResult)

        // then
        val streamId = EventStreamId.of("Issue", issueId)
        assertStreamEvents(streamId, events)
    }

    fun anIssueDescription() = "Issue: ${UUID.randomUUID()}"
}