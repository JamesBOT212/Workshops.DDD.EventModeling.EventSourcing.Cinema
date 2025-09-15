package com.dddheroes.cinema.modules.issues.events

import com.dddheroes.cinema.modules.issues.IssueId
import com.dddheroes.cinema.shared.events.CinemaEvent
import com.dddheroes.sdk.domain.EventStreamId

sealed interface IssueEvent : CinemaEvent {
    val issueId: IssueId

    override val streamId: EventStreamId
        get() = EventStreamId.of("Issue", issueId)
}

