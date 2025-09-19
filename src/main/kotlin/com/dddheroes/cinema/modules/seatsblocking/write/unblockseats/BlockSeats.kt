package com.dddheroes.cinema.modules.seatsblocking.write.unblockseats

import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatEvent
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotBlocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatNotUnblocked
import com.dddheroes.cinema.modules.seatsblocking.events.SeatPlaced
import com.dddheroes.cinema.modules.seatsblocking.events.SeatUnblocked
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.restapi.ErrorResponse
import com.dddheroes.sdk.application.CommandResult
import com.dddheroes.sdk.application.inMultiStreamTransaction
import com.dddheroes.sdk.application.resultOf
import com.dddheroes.sdk.application.toCommandResult
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

data class UnblockSeats(
    val screeningId: ScreeningId,
    val seats: Set<SeatNumber>,
    val blockadeOwner: String,
    val issuedAt: Instant
)
