package com.dddheroes.sdk.domain

data class EventStreamId private constructor(
    val type: String,
    val value: String
) {

    init {
        require(type.isNotBlank()) { "Type cannot be blank" }
        require(value.isNotBlank()) { "Value cannot be blank" }
    }

    companion object {

        fun of(type: String, vararg idParts: Any): EventStreamId {
            return EventStreamId(type, idParts.map { it.toString() }.joinToString(":") { it.trim() })
        }

        fun of(type: String, value: Any): EventStreamId {
            return EventStreamId(type, value.toString())
        }

        fun of(type: String, value: String): EventStreamId {
            return EventStreamId(type, value)
        }

        fun fromRaw(raw: String): EventStreamId {
            require(raw.isNotBlank()) { "Raw string cannot be blank" }
            val parts = raw.split(":", limit = 2)
            require(parts.size == 2) { "Invalid format. Expected 'type_value'" }
            return of(parts[0], parts[1])
        }
    }

    override fun toString(): String = "${type}:${value}"
}