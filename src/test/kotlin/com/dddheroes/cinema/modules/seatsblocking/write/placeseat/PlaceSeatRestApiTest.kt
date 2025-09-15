package com.dddheroes.cinema.modules.seatsblocking.write.placeseat

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.seatsblocking.write.placeseat.PlaceSeat
import com.dddheroes.cinema.modules.seatsblocking.write.placeseat.PlaceSeatRestApi
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(PlaceSeatRestApi::class)
@TestPropertySource(properties = ["slices.seatsblocking.write.placeseat.enabled=true"])
class PlaceSeatRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val screeningId = ScreeningId.random()
        val seat = "1:2"
        assumeCommandSuccess<PlaceSeat>()

        Given {
            pathParam("screeningId", screeningId.raw)
            pathParam("seat", seat)
        } When {
            put("/cinema/screenings/{screeningId}/seats/{seat}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val screeningId = ScreeningId.random()
        val seat = "1:2"

        assumeCommandFailure<PlaceSeat>()

        Given {
            pathParam("screeningId", screeningId.raw)
            pathParam("seat", seat)
        } When {
            put("/cinema/screenings/{screeningId}/seats/{seat}")
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