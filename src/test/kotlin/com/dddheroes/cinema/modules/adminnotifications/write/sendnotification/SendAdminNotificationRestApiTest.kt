package com.dddheroes.cinema.modules.adminnotifications.write.sendnotification

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.write.sendnotification.SendAdminNotification
import com.dddheroes.cinema.modules.adminnotifications.write.sendnotification.SendAdminNotificationRestApi
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(SendAdminNotificationRestApi::class)
@TestPropertySource(properties = ["slices.adminnotifications.write.sendnotification.enabled=true"])
class SendAdminNotificationRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val notificationId = NotificationId.random()
        val content = "Test notification content"

        assumeCommandSuccess(
            SendAdminNotification(
                notificationId = notificationId,
                content = content,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("notificationId", notificationId.raw)
            contentType(ContentType.JSON)
            body("""{"content":"$content"}""")
        } When {
            async().put("/cinema/admin/notifications/{notificationId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val notificationId = NotificationId.random()
        val content = "Test notification content"

        assumeCommandFailure(
            SendAdminNotification(
                notificationId = notificationId,
                content = content,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("notificationId", notificationId.raw)
            contentType(ContentType.JSON)
            body("""{"content":"$content"}""")
        } When {
            async().put("/cinema/admin/notifications/{notificationId}")
        } Then {
            statusCode(400)
            contentType("application/json")
            body(
                equalTo(
                    """{"message":"Simulated failure"}"""
                )
            )
        }
    }
}