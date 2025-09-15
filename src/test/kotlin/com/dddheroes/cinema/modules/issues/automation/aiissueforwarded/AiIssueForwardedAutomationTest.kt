package com.dddheroes.cinema.modules.issues.automation.aiissueforwarded

import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.write.sendnotification.SendAdminNotification
import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.modules.dayschedule.MovieId
import com.dddheroes.cinema.modules.dayschedule.randomDate
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.ScreeningReadModel
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreenings
import com.dddheroes.cinema.modules.dayschedule.read.searchscreenings.SearchScreeningsResult
import com.dddheroes.cinema.modules.dayschedule.write.cancelscreening.CancelScreening
import com.dddheroes.cinema.modules.issues.IssueId
import com.dddheroes.cinema.modules.issues.events.IssueOpened
import com.dddheroes.cinema.modules.seatsblocking.write.blockseat.BlockSeat
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@TestPropertySource(properties = ["slices.issues.automation.aiissueforwarder.enabled=true"])
@Disabled("Set Anthropic API key in application.yml and enable this test.")
class AiIssueForwardedAutomationTest : MessagingSpringBootTest() {

    @Test
    fun `when issue opened about spilled drink on seat then block seat for all remaining screenings`() {
        // given
        currentTimeIs(randomDate(9, 0))
        val occurredAt = currentTime()
        val today = LocalDate.ofInstant(occurredAt, clock().zone)

        val screeningId1 = ScreeningId.random()
        val screeningId2 = ScreeningId.random()
        assumeCommandSuccess<BlockSeat>()
        assumeQueryReturns(
            SearchScreenings(day = today),
            SearchScreeningsResult(
                listOf(
                    ScreeningReadModel(
                        screeningId1.raw,
                        DayScheduleId.of(today).toString(),
                        today,
                        MovieId.random().raw,
                        LocalTime.of(10, 0),
                        LocalTime.of(12, 0)
                    ),
                    ScreeningReadModel(
                        screeningId2.raw,
                        DayScheduleId.of(today).toString(),
                        today,
                        MovieId.random().raw,
                        LocalTime.of(12, 30),
                        LocalTime.of(13, 30)
                    ),
                )
            )
        )

        // when
        val issueId = IssueId.random()
        val description = "Na miejscu 1,1 wylała się Coca-Cola"

        // time after the screening1 started, but before screening2 started
        currentTimeIs(currentTime().plus(2, ChronoUnit.HOURS))
        val issueOpenedAt = currentTime()

        eventsOccurred(
            IssueOpened(
                issueId = issueId,
                description = description,
                occurredAt = issueOpenedAt
            )
        )

        // then
        assertCommandExecuted(
            BlockSeat(
                screeningId = ScreeningId(screeningId2.raw),
                seat = SeatNumber(1, 1),
                blockadeOwner = "Issue:$issueId",
                issuedAt = issueOpenedAt
            )
        )
    }

    @Test
    fun `when issue opened about projector failure then cancel all remaining screenings`() {
        // given
        currentTimeIs(randomDate(9, 0))
        val occurredAt = currentTime()
        val today = LocalDate.ofInstant(occurredAt, clock().zone)

        val screeningId1 = ScreeningId.random()
        val screeningId2 = ScreeningId.random()
        assumeCommandSuccess<CancelScreening>()
        assumeQueryReturns(
            SearchScreenings(day = today),
            SearchScreeningsResult(
                listOf(
                    ScreeningReadModel(
                        screeningId1.raw,
                        DayScheduleId.of(today).toString(),
                        today,
                        MovieId.random().raw,
                        LocalTime.of(10, 0),
                        LocalTime.of(12, 0)
                    ),
                    ScreeningReadModel(
                        screeningId2.raw,
                        DayScheduleId.of(today).toString(),
                        today,
                        MovieId.random().raw,
                        LocalTime.of(12, 30),
                        LocalTime.of(13, 30)
                    ),
                )
            )
        )

        // when
        val issueId = IssueId.random()
        val description = "Projektor się zepsuł"

        // time after the screening1 started, but before screening2 started
        currentTimeIs(currentTime().plus(2, ChronoUnit.HOURS))
        val issueOpenedAt = currentTime()
        eventsOccurred(
            IssueOpened(
                issueId = issueId,
                description = description,
                occurredAt = issueOpenedAt
            )
        )

        // then
        assertCommandExecuted(
            CancelScreening(
                dayScheduleId = DayScheduleId.of(today),
                screeningId = ScreeningId(screeningId2.raw),
                issuedAt = issueOpenedAt
            )
        )
    }

    @Test
    fun `when issue opened about general problem then send admin notification`() {
        // given
        currentTimeIs(randomDate(9, 0))
        val occurredAt = currentTime()
        assumeCommandSuccess<SendAdminNotification>()

        // when
        val issueId = IssueId.random()
        val description = "Toaleta jest zepsuta"

        eventsOccurred(
            IssueOpened(
                issueId = issueId,
                description = description,
                occurredAt = occurredAt
            )
        )

        // then
        assertCommandExecuted(
            SendAdminNotification(
                notificationId = NotificationId("IssueOpened$issueId"),
                content = "[ACTION REQUIRED] New issue opened!",
                issuedAt = occurredAt
            )
        )
    }
}