package com.dddheroes.cinema.modules.adminnotifications.write.readnotification

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.write.readnotification.ReadAdminNotification
import com.dddheroes.cinema.modules.adminnotifications.write.readnotification.ReadAdminNotificationRestApi
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(ReadAdminNotificationRestApi::class)
@TestPropertySource(properties = ["slices.adminnotifications.write.readnotification.enabled=true"])
class ReadAdminNotificationRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val notificationId = NotificationId.random()

        assumeCommandSuccess(
            ReadAdminNotification(
                notificationId = notificationId,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("notificationId", notificationId.raw)
        } When {
            async().put("/cinema/admin/notifications/{notificationId}/read")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val notificationId = NotificationId.random()

        assumeCommandFailure(
            ReadAdminNotification(
                notificationId = notificationId,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("notificationId", notificationId.raw)
        } When {
            async().put("/cinema/admin/notifications/{notificationId}/read")
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