package com.dddheroes.sdk.application

import com.dddheroes.sdk.domain.DomainEvent
import com.dddheroes.sdk.domain.EventStreamId
import org.axonframework.eventhandling.GenericDomainEventMessage
import org.axonframework.eventsourcing.eventstore.EventStore

inline fun <reified T : DomainEvent> EventStore.inMultiStreamTransaction(
    streams: List<EventStreamId>,
    block: (List<T>) -> List<T>
): Map<EventStreamId, List<T>> {
    val storedEvents = sourceMulti<T>(streams)
    val lastSequenceByStream = storedEvents.groupBy { it.streamId }.mapValues { it.value.size.toLong() }
    val eventsToStore = block(storedEvents)

    // Validate that all events belong to one of the specified streams
    val eventsWithInvalidStreamId = eventsToStore.filter { it.streamId !in streams }
    if (eventsWithInvalidStreamId.isNotEmpty()) {
        throw IllegalArgumentException(
            "Attempt to store events which do not belong to any of the specified streams. Expected streams: $streams, but found events with streams: ${
                eventsWithInvalidStreamId.map { it.streamId }.distinct()
            }"
        )
    }

    val eventsToStoreByStream = eventsToStore.groupBy { it.streamId }
    eventsToStoreByStream.forEach { (streamId, events) ->
        val expectedSequence = lastSequenceByStream[streamId] ?: 0L
        append(streamId, events, expectedSequence)
    }

    return eventsToStoreByStream
}

inline fun <reified T : DomainEvent> EventStore.sourceMulti(streams: List<EventStreamId>) =
    streams.map { sourceSingle<T>(it) }
        .reduce { acc, list -> acc + list }

/**
 * Executes a transaction on a single event stream identified by the combination of [streamCategory] and [streamId].
 *
 * This function reads all events from the specified stream, applies the provided [block] function to the list of
 * events to determine new events to be appended, and then appends those new events to the stream.
 *
 * Optimistic Locking: The expected sequence number for appending is calculated based on the number of events read from the stream.
 * If the stream has been modified by another transaction in the meantime, the append operation will fail, ensuring data consistency.
 *
 * @param T The type of events in the stream.
 * @param streamCategory The category of the event stream.
 * @param streamId The identifier of the event stream.
 * @param block A function that takes the current list of events and returns a list of new events to append.
 */
inline fun <reified T : DomainEvent> EventStore.inSingleStreamTransaction(
    id: EventStreamId,
    block: (List<T>) -> List<T>
): List<T> {
    val storedEvents = sourceSingle<T>(id)
    val currentSequence = storedEvents.size.toLong()
    val eventsToStore = block(storedEvents)

    // Validate that all events belong to the same stream
    val eventsWithDifferentStreamId = eventsToStore.filter { it.streamId != id }
    if (eventsWithDifferentStreamId.isNotEmpty()) {
        throw IllegalArgumentException(
            "Attempt to store events which do not belong to the same stream. Expected stream: $id, but found events with streams: ${
                eventsWithDifferentStreamId.map { it.streamId }.distinct()
            }"
        )
    }

    append(id, eventsToStore, currentSequence)
    return eventsToStore;
}

/**
 * Reads all events from a single event stream and casts them to the specified type.
 *
 * @param T The expected type of events in the stream.
 * @param id The identifier of the event stream to read from.
 * @return List of events cast to type T.
 * @throws IllegalStateException if any event in the stream cannot be cast to the expected type T.
 */
inline fun <reified T : DomainEvent> EventStore.sourceSingle(id: EventStreamId, startSequence: Int = 0): List<T> =
    readEvents(id.toString()).asStream()
        .filter { it.sequenceNumber >= startSequence }
        .map { it.payload }
        .map { event ->
            event as? T
                ?: throw IllegalStateException("Unexpected event type in stream $id. Expected: ${T::class.simpleName}, but found: ${event::class.simpleName}")
        }.toList()

inline fun <reified T : DomainEvent> EventStore.append(
    streamId: EventStreamId,
    events: List<T>,
    expectedSequence: Long,
    metadataProvider: (T) -> Map<String, Any> = { emptyMap() }
) {
    val eventsToStore = events.mapIndexed { index, event ->
        GenericDomainEventMessage(
            streamId.type,
            streamId.toString(),
            expectedSequence + index,
            event,
            metadataProvider.invoke(event)
        )
    }
    publish(eventsToStore)
}