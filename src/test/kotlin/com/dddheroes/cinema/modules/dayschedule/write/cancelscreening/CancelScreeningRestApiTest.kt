package com.dddheroes.cinema.modules.dayschedule.write.cancelscreening

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(CancelScreeningRestApi::class)
@TestPropertySource(properties = ["slices.dayschedule.write.cancelscreening.enabled=true"])
class CancelScreeningRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val dayScheduleId = DayScheduleId.random()
        val screeningId = ScreeningId.random()

        assumeCommandSuccess(
            CancelScreening(
                dayScheduleId = dayScheduleId,
                screeningId = screeningId,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("dayScheduleId", dayScheduleId.toString())
            pathParam("screeningId", screeningId.raw)
        } When {
            async().delete("/cinema/schedules/{dayScheduleId}/screenings/{screeningId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val dayScheduleId = DayScheduleId.random()
        val screeningId = ScreeningId.random()

        assumeCommandFailure(
            CancelScreening(
                dayScheduleId = dayScheduleId,
                screeningId = screeningId,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("dayScheduleId", dayScheduleId.toString())
            pathParam("screeningId", screeningId.raw)
        } When {
            async().delete("/cinema/schedules/{dayScheduleId}/screenings/{screeningId}")
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