package com.dddheroes.cinema.modules.seatsblocking.automation.trendsreporter

import com.dddheroes.cinema.MessagingSpringBootTest
import com.dddheroes.cinema.modules.adminnotifications.NotificationId
import com.dddheroes.cinema.modules.adminnotifications.write.sendnotification.SendAdminNotification
import com.dddheroes.cinema.modules.seatsblocking.events.SeatBlocked
import com.dddheroes.cinema.modules.seatsblocking.write.placeseat.PlaceSeat
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import java.time.Duration
import java.time.Instant

@TestPropertySource(properties = ["slices.dayschedule.automation.trendsreporter.enabled=true"])
class TrendingScreeningReservationsInHourAutomationTest : MessagingSpringBootTest() {

    @Test
    fun `if seats blocked (by reservation) in screening more than 2 times in an hour, we notify the admin about trening screening 2`() {
        // given
        assumeCommandSuccess<SendAdminNotification>()
        val screeningId = ScreeningId.random()

        val booking1Timestamp = Instant.now()
        val booking2Timestamp: Instant = Instant.now().plus(Duration.ofMinutes(30))
        eventsOccurred(
            SeatBlocked(screeningId, SeatNumber(1, 1), "Reservation:1", booking1Timestamp),
            SeatBlocked(screeningId, SeatNumber(1, 2), "Reservation:2", booking2Timestamp),
        )

        // when - events processed by the automation

        // then
        val notificationId = NotificationId("trending-screening-$screeningId");
        assertCommandExecuted(
            SendAdminNotification(notificationId, "Screening $screeningId is trending", booking2Timestamp)
        )
    }

}