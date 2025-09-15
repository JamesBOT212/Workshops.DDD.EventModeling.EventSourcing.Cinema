package com.dddheroes.cinema.modules.adminnotifications.automation.notificationviaemail

import org.apache.logging.log4j.kotlin.logger
import org.springframework.stereotype.Component

interface EmailSender {
    data class EmailMessage(val recipient: String, val subject: String, val body: String)

    fun send(message: EmailMessage)
}

@Component
class LoggingEmailSender : EmailSender {
    private val logger = logger()

    override fun send(message: EmailSender.EmailMessage) {
        logger.info("Sending email to: ${message.recipient}, subject: ${message.subject}, body: ${message.body}")
    }

}