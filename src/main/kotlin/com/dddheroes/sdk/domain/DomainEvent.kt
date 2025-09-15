package com.dddheroes.sdk.domain

import java.time.Instant

interface DomainEvent {
    val streamId: EventStreamId
    val occurredAt: Instant
}