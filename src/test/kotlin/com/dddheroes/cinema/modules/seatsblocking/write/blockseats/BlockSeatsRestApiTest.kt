package com.dddheroes.cinema.modules.seatsblocking.write.blockseats

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.seatsblocking.write.blockseats.BlockSeats
import com.dddheroes.cinema.modules.seatsblocking.write.blockseats.BlockSeatsRestApi
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(BlockSeatsRestApi::class)
@TestPropertySource(properties = ["slices.seatsblocking.write.blockseats.enabled=true"])
class BlockSeatsRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val screeningId = ScreeningId.random()
        val blockadeOwner = "Issue:123"
        assumeCommandSuccess<BlockSeats>()

        Given {
            pathParam("screeningId", screeningId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "seats": ["1:2", "1:3", "2:1"],
                  "blockadeOwner": "$blockadeOwner"
                }
                """
            )
        } When {
            async().put("/cinema/screenings/{screeningId}/seats-blockades")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val screeningId = ScreeningId.random()
        val blockadeOwner = "Issue:123"

        assumeCommandFailure<BlockSeats>()

        Given {
            pathParam("screeningId", screeningId.raw)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "seats": ["1:2", "1:3", "2:1"],
                  "blockadeOwner": "$blockadeOwner"
                }
                """
            )
        } When {
            async().put("/cinema/screenings/{screeningId}/seats-blockades")
        } Then {
            statusCode(400)
            contentType(ContentType.JSON)
            body(
                equalTo(
                    """{"message":"Simulated failure"}"""
                )
            )
        }
    }
}