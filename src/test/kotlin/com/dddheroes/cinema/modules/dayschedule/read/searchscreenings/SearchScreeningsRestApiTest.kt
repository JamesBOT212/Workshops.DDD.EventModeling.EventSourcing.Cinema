package com.dddheroes.cinema.modules.dayschedule.read.searchscreenings

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.hasSize
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalTime
import java.util.*

@WebMvcTest(SearchScreeningsRestApi::class)
@TestPropertySource(properties = ["slices.dayschedule.read.searchscreenings.enabled=true"])
class SearchScreeningsRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `should return screenings when no filters applied`() {
        val screening1Id = ScreeningId.random()
        val screening2Id = ScreeningId.random()
        val movie1Id = MovieId.random()
        val movie2Id = MovieId.random()
        val day = LocalDate.of(2025, 1, 15)

        val expectedResult = SearchScreeningsResult(
            items = listOf(
                ScreeningReadModel(
                    screeningId = screening1Id.raw,
                    dayScheduleId = day.toString(),
                    day = day,
                    movieId = movie1Id.raw,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(16, 30)
                ),
                ScreeningReadModel(
                    screeningId = screening2Id.raw,
                    dayScheduleId = day.toString(),
                    day = day,
                    movieId = movie2Id.raw,
                    startTime = LocalTime.of(19, 0),
                    endTime = LocalTime.of(21, 30)
                )
            )
        )

        assumeQueryReturns<SearchScreeningsResult, SearchScreenings>(
            SearchScreenings(),
            expectedResult
        )

        Given {
            params(Collections.emptyMap<String, String>())
        } When {
            async().get("/cinema/screenings")
        } Then {
            statusCode(200)
            body("items", hasSize<Any>(2))
            body("items[0].screeningId", equalTo(screening1Id.raw))
            body("items[0].dayScheduleId", equalTo(day.toString()))
            body("items[0].day", equalTo("2025-01-15"))
            body("items[0].movieId", equalTo(movie1Id.raw))
            body("items[0].startTime", equalTo("14:00:00"))
            body("items[0].endTime", equalTo("16:30:00"))
            body("items[1].screeningId", equalTo(screening2Id.raw))
            body("items[1].dayScheduleId", equalTo(day.toString()))
            body("items[1].day", equalTo("2025-01-15"))
            body("items[1].movieId", equalTo(movie2Id.raw))
            body("items[1].startTime", equalTo("19:00:00"))
            body("items[1].endTime", equalTo("21:30:00"))
        }
    }

    @Test
    fun `should return filtered screenings by movieId`() {
        val screeningId = ScreeningId.random()
        val movieId = MovieId.random()
        val day = LocalDate.of(2025, 1, 15)

        val expectedResult = SearchScreeningsResult(
            items = listOf(
                ScreeningReadModel(
                    screeningId = screeningId.raw,
                    dayScheduleId = day.toString(),
                    day = day,
                    movieId = movieId.raw,
                    startTime = LocalTime.of(14, 0),
                    endTime = LocalTime.of(16, 30)
                )
            )
        )

        assumeQueryReturns<SearchScreeningsResult, SearchScreenings>(
            SearchScreenings(movieId = movieId),
            expectedResult
        )

        Given {
            queryParam("movieId", movieId.raw)
        } When {
            async().get("/cinema/screenings")
        } Then {
            statusCode(200)
            body("items", hasSize<Any>(1))
            body("items[0].screeningId", equalTo(screeningId.raw))
            body("items[0].movieId", equalTo(movieId.raw))
            body("items[0].startTime", equalTo("14:00:00"))
            body("items[0].endTime", equalTo("16:30:00"))
        }
    }

    @Test
    fun `should return filtered screenings by day`() {
        val screeningId = ScreeningId.random()
        val movieId = MovieId.random()
        val day = LocalDate.of(2025, 1, 15)

        val expectedResult = SearchScreeningsResult(
            items = listOf(
                ScreeningReadModel(
                    screeningId = screeningId.raw,
                    dayScheduleId = day.toString(),
                    day = day,
                    movieId = movieId.raw,
                    startTime = LocalTime.of(20, 0),
                    endTime = LocalTime.of(22, 30)
                )
            )
        )

        assumeQueryReturns<SearchScreeningsResult, SearchScreenings>(
            SearchScreenings(day = day),
            expectedResult
        )

        Given {
            queryParam("day", "2025-01-15")
        } When {
            async().get("/cinema/screenings")
        } Then {
            statusCode(200)
            body("items", hasSize<Any>(1))
            body("items[0].screeningId", equalTo(screeningId.raw))
            body("items[0].day", equalTo("2025-01-15"))
            body("items[0].startTime", equalTo("20:00:00"))
            body("items[0].endTime", equalTo("22:30:00"))
        }
    }

    @Test
    fun `should return empty result when no screenings match filters`() {
        val movieId = MovieId.random()

        val expectedResult = SearchScreeningsResult(items = emptyList())

        assumeQueryReturns<SearchScreeningsResult, SearchScreenings>(
            SearchScreenings(movieId = movieId),
            expectedResult
        )

        Given {
            queryParam("movieId", movieId.raw)
        } When {
            async().get("/cinema/screenings")
        } Then {
            statusCode(200)
            body("items", hasSize<Any>(0))
        }
    }
}