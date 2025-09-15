package com.dddheroes.cinema.modules.issues.events

import com.dddheroes.cinema.modules.issues.IssueId
import java.time.Instant

data class IssueOpened(
    override val issueId: IssueId,
    val description: String,
    override val occurredAt: Instant
) : IssueEvent