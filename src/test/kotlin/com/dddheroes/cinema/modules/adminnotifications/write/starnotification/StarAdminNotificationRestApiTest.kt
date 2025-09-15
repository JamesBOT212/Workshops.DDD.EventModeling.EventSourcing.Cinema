package com.dddheroes.cinema.modules.adminnotifications.write.starnotification

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.write.starnotification.StarAdminNotification
import com.dddheroes.cinema.modules.adminnotifications.write.starnotification.StarAdminNotificationRestApi
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(StarAdminNotificationRestApi::class)
@TestPropertySource(properties = ["slices.adminnotifications.write.starnotification.enabled=true"])
class StarAdminNotificationRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val notificationId = NotificationId.random()
        assumeCommandSuccess(
            StarAdminNotification(
                notificationId = notificationId,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("notificationId", notificationId.raw)
        } When {
            async().put("/cinema/admin/notifications/{notificationId}/star")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val notificationId = NotificationId.random()

        assumeCommandFailure(
            StarAdminNotification(
                notificationId = notificationId,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("notificationId", notificationId.raw)
        } When {
            async().put("/cinema/admin/notifications/{notificationId}/star")
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