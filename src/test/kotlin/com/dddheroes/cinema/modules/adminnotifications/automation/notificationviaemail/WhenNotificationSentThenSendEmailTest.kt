package com.dddheroes.cinema.modules.adminnotifications.automation.notificationviaemail

import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.automation.notificationviaemail.EmailSender
import com.dddheroes.cinema.modules.adminnotifications.events.NotificationSent
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean

@TestPropertySource(properties = ["slices.adminnotifications.automation.notificationviaemail.enabled"])
class WhenNotificationSentThenSendEmailTest : MessagingSpringBootTest() {

    @MockitoSpyBean
    lateinit var emailSender: EmailSender

    @Test
    fun `given sent notification, then send email message`() {
        // given
        val notificationId = NotificationId.random()
        val occurredAt = currentTime()
        assumeCommandSuccess<EmailSender>()
        eventOccurred(
            NotificationSent(
                notificationId = notificationId,
                content = "Notification content",
                occurredAt
            )
        )

        // when
        // NotificationSent processed by the automation

        // then
        awaitUntilAsserted {
            verify(emailSender).send(
                EmailSender.EmailMessage(
                    "admin@verticalcinema.pl",
                    "New Notification: $notificationId",
                    "Notification content"
                )
            )
        }
    }

}