package com.dddheroes.cinema.modules.issues.write.openissue

import com.dddheroes.cinema.RestApiSpringBootTest
import com.dddheroes.cinema.modules.issues.IssueId
import com.dddheroes.cinema.modules.issues.write.openissue.OpenIssue
import com.dddheroes.cinema.modules.issues.write.openissue.OpenIssueRestApi
import io.restassured.http.ContentType
import io.restassured.module.mockmvc.kotlin.extensions.Given
import io.restassured.module.mockmvc.kotlin.extensions.Then
import io.restassured.module.mockmvc.kotlin.extensions.When
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource

@WebMvcTest(OpenIssueRestApi::class)
@TestPropertySource(properties = ["slices.issues.write.openissue.enabled=true"])
class OpenIssueRestApiTest : RestApiSpringBootTest() {

    @Test
    fun `command success - returns 204 No Content`() {
        val issueId = IssueId.random()
        val description = "System experiencing intermittent connection issues"

        assumeCommandSuccess(
            OpenIssue(
                issueId = issueId,
                description = description,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("issueId", issueId.raw)
            contentType(ContentType.JSON)
            body("""{"description":"$description"}""")
        } When {
            async().put("/cinema/issues/{issueId}")
        } Then {
            statusCode(HttpStatus.NO_CONTENT.value())
        }
    }

    @Test
    fun `command failure - returns 400 Bad Request`() {
        val issueId = IssueId.random()
        val description = "System experiencing intermittent connection issues"

        assumeCommandFailure(
            OpenIssue(
                issueId = issueId,
                description = description,
                issuedAt = currentTime()
            )
        )

        Given {
            pathParam("issueId", issueId.raw)
            contentType(ContentType.JSON)
            body("""{"description":"$description"}""")
        } When {
            async().put("/cinema/issues/{issueId}")
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