package com.dddheroes.cinema.modules.reservations.write.startreservation

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.reservations.ReservationId
import com.dddheroes.cinema.modules.reservations.write.startreservation.StartReservation
import com.dddheroes.cinema.modules.reservations.write.startreservation.StartReservationRestApi
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(StartReservationRestApi::class)
@TestPropertySource(properties = ["slices.reservations.write.startreservation.enabled=true"])
class StartReservationRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 200 OK`() {
        val screeningId = ScreeningId.random()
        val reservationId = ReservationId.random()
        assumeCommandSuccess(
            StartReservation(
                reservationId,
                screeningId,
                setOf(SeatNumber(1, 1), SeatNumber(1, 2)),
                currentTime()
            )
        )

        Given {
            pathParam("screeningId", screeningId.raw)
            pathParam("reservationId", reservationId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "seats": [
                    {"row": 1, "column": 1},
                    {"row": 1, "column": 2}
                  ]
                }
                """
            )
        } When {
            async().put("/cinema/screenings/{screeningId}/reservations/{reservationId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val screeningId = ScreeningId.random()
        val reservationId = ReservationId.random()

        assumeCommandFailure(
            StartReservation(
                reservationId,
                screeningId,
                setOf(SeatNumber(1, 1), SeatNumber(1, 2)),
                currentTime()
            )
        )

        Given {
            pathParam("screeningId", screeningId.raw)
            pathParam("reservationId", reservationId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "seats": [
                    {"row": 1, "column": 1},
                    {"row": 1, "column": 2}
                  ]
                }
                """
            )
        } When {
            async().put("/cinema/screenings/{screeningId}/reservations/{reservationId}")
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