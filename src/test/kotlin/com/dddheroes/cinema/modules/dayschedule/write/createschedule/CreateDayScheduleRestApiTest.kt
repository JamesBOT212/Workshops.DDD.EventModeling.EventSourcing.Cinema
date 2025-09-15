package com.dddheroes.cinema.modules.dayschedule.write.createschedule

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.random
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import java.time.LocalTime

@WebMvcTest(CreateDayScheduleRestApi::class)
@TestPropertySource(properties = ["slices.dayschedule.write.createschedule.enabled=true"])
class CreateDayScheduleRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val dayScheduleId = DayScheduleId.random()
        val openingTime = LocalTime.of(9, 0)
        val closingTime = LocalTime.of(21, 0)

        assumeCommandSuccess(
            CreateDaySchedule(
                dayScheduleId = dayScheduleId,
                openingTime = openingTime,
                closingTime = closingTime,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("dayScheduleId", dayScheduleId.toString())
            contentType(ContentType.JSON)
            body("""{"openingTime":"09:00","closingTime":"21:00"}""")
        } When {
            async().put("/cinema/schedules/{dayScheduleId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val dayScheduleId = DayScheduleId.random()
        val openingTime = LocalTime.of(9, 0)
        val closingTime = LocalTime.of(21, 0)

        assumeCommandFailure(
            CreateDaySchedule(
                dayScheduleId = dayScheduleId,
                openingTime = openingTime,
                closingTime = closingTime,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("dayScheduleId", dayScheduleId.toString())
            contentType(ContentType.JSON)
            body("""{"openingTime":"09:00","closingTime":"21:00"}""")
        } When {
            async().put("/cinema/schedules/{dayScheduleId}")
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