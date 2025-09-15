package com.dddheroes.cinema.modules.dayschedule.events

import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.shared.events.CinemaEvent
import com.dddheroes.sdk.domain.EventStreamId

sealed interface DayScheduleEvent : CinemaEvent {
    val dayScheduleId: DayScheduleId

    override val streamId: EventStreamId
        get() = EventStreamId.of("DaySchedule", dayScheduleId)
}