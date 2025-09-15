package com.dddheroes.cinema.modules.reservations.write.cancelreservation

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.write.cancelreservation.CancelReservation
import com.dddheroes.cinema.modules.reservations.write.cancelreservation.CancelReservationRestApi
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(CancelReservationRestApi::class)
@TestPropertySource(properties = ["slices.reservations.write.cancelreservation.enabled=true"])
class CancelReservationRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val reservationId = ReservationId.random()
        val reason = "Customer requested cancellation"
        assumeCommandSuccess(
            CancelReservation(
                reservationId,
                reason,
                currentTime()
            )
        )

        Given {
            pathParam("reservationId", reservationId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "reason": "$reason"
                }
                """
            )
        } When {
            async().delete("/cinema/reservations/{reservationId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val reservationId = ReservationId.random()
        val reason = "Customer requested cancellation"

        assumeCommandFailure(
            CancelReservation(
                reservationId,
                reason,
                currentTime()
            )
        )

        Given {
            pathParam("reservationId", reservationId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "reason": "$reason"
                }
                """
            )
        } When {
            async().delete("/cinema/reservations/{reservationId}")
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