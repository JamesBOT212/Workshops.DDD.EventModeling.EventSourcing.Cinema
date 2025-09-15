package com.dddheroes.cinema.modules.adminnotifications

import java.util.UUID

@JvmInline
value class NotificationId(val raw: String) {
    override fun toString(): String = raw

    companion object {
        fun random(): NotificationId = NotificationId(UUID.randomUUID().toString())
    }
}
