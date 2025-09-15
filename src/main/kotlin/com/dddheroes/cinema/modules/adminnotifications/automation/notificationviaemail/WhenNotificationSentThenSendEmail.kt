package com.dddheroes.cinema.modules.adminnotifications.automation.notificationviaemail

import com.dddheroes.cinema.modules.adminnotifications.events.NotificationSent
import org.axonframework.eventhandling.DisallowReplay
import org.axonframework.eventhandling.EventHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@ConditionalOnProperty(name = ["slices.adminnotifications.automation.notificationviaemail.enabled"])
@Component
private class WhenNotificationSentThenSendEmail(private val emailSender: EmailSender) {

    @DisallowReplay
    @EventHandler
    fun handle(event: NotificationSent) {
        emailSender.send(
            EmailSender.EmailMessage(
                recipient = "admin@verticalcinema.pl",
                subject = "New Notification: ${event.notificationId}",
                body = event.content
            )
        )
    }

}