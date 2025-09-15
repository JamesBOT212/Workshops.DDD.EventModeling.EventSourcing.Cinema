package com.dddheroes.sdk.application

import com.dddheroes.cinema.shared.restapi.ErrorResponse
import com.dddheroes.sdk.domain.DomainEvent
import com.dddheroes.sdk.domain.DomainRuleViolatedException
import com.dddheroes.sdk.domain.EventStreamId
import com.dddheroes.sdk.domain.FailureEvent
import org.springframework.http.ResponseEntity
import java.util.concurrent.CompletableFuture

sealed class CommandResult {
    data object Success : CommandResult()
    data class Failure(val message: String) : CommandResult()

    fun throwIfFailure(): CommandResult {
        if (this is Failure) {
            throw DomainRuleViolatedException(message)
        }
        return this;
    }
}

inline fun <T, R> T.resultOf(block: T.() -> R): CommandResult {
    return try {
        block()
        CommandResult.Success
    } catch (e: Throwable) {
        CommandResult.Failure(e.message ?: "Unknown error")
    }
}

fun <T : DomainEvent> Collection<T>.toCommandResult(): CommandResult {
    val failureEvents = this.filterIsInstance<FailureEvent>()
    return if (failureEvents.isEmpty()) {
        CommandResult.Success
    } else {
        val messages = failureEvents.joinToString(", ") { it.reason }
        CommandResult.Failure(messages)
    }
}

fun <T : DomainEvent> Map<EventStreamId, List<T>>.toCommandResult(): CommandResult =
    this.values.flatten().toCommandResult()

fun <T : CommandResult> CompletableFuture<T>.toResponseEntity(): CompletableFuture<ResponseEntity<Any>> = thenApply {
    when (it) {
        is CommandResult.Success -> ResponseEntity.noContent().build()
        is CommandResult.Failure -> ResponseEntity.badRequest().body(ErrorResponse(it.message))
    }
}