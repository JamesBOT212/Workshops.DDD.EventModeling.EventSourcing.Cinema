package com.dddheroes.cinema.modules.dayschedule.read.getschedulebyid

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.random
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.LocalTime

@WebMvcTest(GetScheduleByIdRestApi::class)
class GetScheduleByIdRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `should return 200 with schedule when schedule exists`() {
        // given
        val scheduleId = DayScheduleId.random()
        val screeningId = ScreeningId.random()
        val movieId = MovieId.random()
        val expectedResult = GetScheduleByIdResult(
            id = scheduleId,
            day = LocalDate.of(2025, 1, 15),
            openingTime = LocalTime.of(9, 0),
            closingTime = LocalTime.of(22, 0),
            screeningTimes = listOf(
                GetScheduleByIdResult.Screening(
                    screeningId = screeningId,
                    movieId = movieId,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(16, 30)
                )
            )
        )

        assumeQueryReturns<GetScheduleByIdResult?, GetScheduleById>(GetScheduleById(scheduleId), expectedResult)

        Given {
            pathParam("scheduleId", scheduleId.toString())
        } When {
            async().get("/cinema/schedules/{scheduleId}")
        } Then {
            statusCode(200)
            body("id", equalTo(scheduleId.toString()))
            body("day", equalTo("2025-01-15"))
            body("openingTime", equalTo("09:00:00"))
            body("closingTime", equalTo("22:00:00"))
            body("screeningTimes.size()", equalTo(1))
            body("screeningTimes[0].screeningId", equalTo(screeningId.raw))
            body("screeningTimes[0].movieId", equalTo(movieId.raw))
            body("screeningTimes[0].startTime", equalTo("14:00:00"))
            body("screeningTimes[0].endTime", equalTo("16:30:00"))
        }
    }

    @Test
    fun `null query result - returns 404 Not Found`() {
        val scheduleId = DayScheduleId.random()
        assumeQueryReturns<GetScheduleByIdResult?, GetScheduleById>(GetScheduleById(scheduleId), null)

        Given {
            pathParam("scheduleId", scheduleId.raw.toString())
        } When {
            async().get("/cinema/schedules/{scheduleId}")
        } Then {
            statusCode(HttpStatus.NOT_FOUND.value())
        }
    }

}