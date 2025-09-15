package com.dddheroes.cinema.modules.issues

import java.util.UUID

@JvmInline
value class IssueId(val raw: String) {
    override fun toString(): String = raw

    companion object {
        fun of(raw: String): IssueId = IssueId(raw)
        fun random(): IssueId = IssueId(UUID.randomUUID().toString())
    }
}
