package com.dddheroes.cinema.modules.seatsblocking.read.getscreeningseats

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.TestPropertySource

@WebMvcTest(GetScreeningSeatsRestApi::class)
@TestPropertySource(properties = ["slices.seatsblocking.read.getscreeningseats.enabled=true"])
class GetScreeningSeatsRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `should return screening seats when screening exists`() {
        val screeningId = ScreeningId.random()
        val expectedResult = GetScreeningSeatsResult(
            items = listOf(
                ScreeningSeatsReadModel.Seat(row = 1, column = 1, blockedBy = null),
                ScreeningSeatsReadModel.Seat(row = 1, column = 2, blockedBy = "Reservation:123"),
                ScreeningSeatsReadModel.Seat(row = 2, column = 1, blockedBy = null),
                ScreeningSeatsReadModel.Seat(row = 2, column = 2, blockedBy = "Reservation:456")
            )
        )

        assumeQueryReturns<GetScreeningSeatsResult, GetScreeningSeats>(
            GetScreeningSeats(screeningId),
            expectedResult
        )

        Given {
            pathParam("screeningId", screeningId.raw)
        } When {
            async().get("/cinema/screenings/{screeningId}/seats")
        } Then {
            statusCode(200)
            body("items", hasSize<Any>(4))
            body("items[0].row", equalTo(1))
            body("items[0].column", equalTo(1))
            body("items[0].blockedBy", equalTo(null))
            body("items[1].row", equalTo(1))
            body("items[1].column", equalTo(2))
            body("items[1].blockedBy", equalTo("Reservation:123"))
            body("items[2].row", equalTo(2))
            body("items[2].column", equalTo(1))
            body("items[2].blockedBy", equalTo(null))
            body("items[3].row", equalTo(2))
            body("items[3].column", equalTo(2))
            body("items[3].blockedBy", equalTo("Reservation:456"))
        }
    }

    @Test
    fun `should return empty seats when screening has no seats`() {
        val screeningId = ScreeningId.random()
        val expectedResult = GetScreeningSeatsResult(items = emptyList())

        assumeQueryReturns<GetScreeningSeatsResult, GetScreeningSeats>(
            GetScreeningSeats(screeningId),
            expectedResult
        )

        Given {
            pathParam("screeningId", screeningId.raw)
        } When {
            async().get("/cinema/screenings/{screeningId}/seats")
        } Then {
            statusCode(200)
            body("items", hasSize<Any>(0))
        }
    }
}