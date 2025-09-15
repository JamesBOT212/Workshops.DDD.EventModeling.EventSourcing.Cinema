package com.dddheroes.cinema.modules.reservations.write.collectreservation

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.write.collectreservation.CollectReservation
import com.dddheroes.cinema.modules.reservations.write.collectreservation.CollectReservationRestApi
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(CollectReservationRestApi::class)
@TestPropertySource(properties = ["slices.reservations.write.collectreservation.enabled=true"])
class CollectReservationRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val reservationId = ReservationId.random()
        assumeCommandSuccess(
            CollectReservation(
                reservationId,
                currentTime()
            )
        )

        Given {
            pathParam("reservationId", reservationId.raw)
        } When {
            async().post("/cinema/reservations/{reservationId}/collect")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val reservationId = ReservationId.random()

        assumeCommandFailure(
            CollectReservation(
                reservationId,
                currentTime()
            )
        )

        Given {
            pathParam("reservationId", reservationId.raw)
        } When {
            async().post("/cinema/reservations/{reservationId}/collect")
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