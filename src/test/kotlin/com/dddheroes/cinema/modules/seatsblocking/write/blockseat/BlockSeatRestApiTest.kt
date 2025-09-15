package com.dddheroes.cinema.modules.seatsblocking.write.blockseat

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.BlockSeat
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.BlockSeatRestApi
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

@WebMvcTest(BlockSeatRestApi::class)
@TestPropertySource(properties = ["slices.seatsblocking.write.blockseat.enabled=true"])
class BlockSeatRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val screeningId = ScreeningId.random()
        val seat = "1:2"
        val blockadeOwner = "Issue:123"
        assumeCommandSuccess(
            BlockSeat(
                screeningId,
                SeatNumber(1, 2),
                blockadeOwner,
                currentTime()
            )
        )

        Given {
            pathParam("screeningId", screeningId.raw)
            pathParam("seat", seat)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "blockadeOwner": "$blockadeOwner"
                }
                """
            )
        } When {
            put("/cinema/screenings/{screeningId}/seats-blockades/{seat}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        // given
        val screeningId = ScreeningId.random()
        val seat = "1:2"
        val blockadeOwner = "Issue:123"

        assumeCommandFailure<BlockSeat>()

        Given {
            pathParam("screeningId", screeningId.raw)
            pathParam("seat", seat)
            contentType(ContentType.JSON)
            body(
                """
                {
                  "blockadeOwner": "$blockadeOwner"
                }
                """
            )
        } When {
            put("/cinema/screenings/{screeningId}/seats-blockades/{seat}")
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