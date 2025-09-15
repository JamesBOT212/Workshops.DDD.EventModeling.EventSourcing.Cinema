package com.dddheroes.cinema.modules.dayschedule.automation.whenscreeningscheduledthenplaceseats

import com.dddheroes.cinema.modules.dayschedule.events.ScreeningScheduled
import com.dddheroes.cinema.shared.valueobjects.SeatNumber
import com.dddheroes.cinema.modules.seatsblocking.write.placeseat.PlaceSeat
import com.dddheroes.sdk.application.CommandResult
import org.axonframework.commandhandling.gateway.CommandGateway
import org.axonframework.eventhandling.DisallowReplay
import org.axonframework.eventhandling.EventHandler
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * This automation is placed in the Seats Bounded Context because Day Schedule Bounded Context knows nothing about the seats.
 * But seats are aware of they belong to the screening.
 */
@ConditionalOnProperty(name = ["slices.dayschedule.automation.whenscreeningscheduledthenplaceseats.enabled"])
@Component
private class WhenScreeningScheduledThenPlaceSeats(
    private val commandGateway: CommandGateway
) {

    @DisallowReplay
    @EventHandler
    fun handle(event: ScreeningScheduled) {
        val screeningId = event.screeningId;

        // Domain assumption: we have just one cinema room with a fixed number of seats
        val seats = (SeatNumber.MIN_SEAT_ROW..SeatNumber.MAX_SEAT_ROW).flatMap { row ->
            (SeatNumber.MIN_SEAT_COLUMN..SeatNumber.MAX_SEAT_COLUMN).map { column ->
                SeatNumber(row, column)
            }
        }.toSet()

        val commands = seats.map { PlaceSeat(screeningId, it, event.occurredAt) }

        commands.forEach { commandGateway.sendAndWait<CommandResult>(it).throwIfFailure() }
    }

}