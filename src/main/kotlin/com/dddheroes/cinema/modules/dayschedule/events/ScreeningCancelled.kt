package com.dddheroes.cinema.modules.dayschedule.events

import com.dddheroes.cinema.modules.dayschedule.DayScheduleId
import com.dddheroes.cinema.shared.valueobjects.ScreeningId
import java.time.Instant

data class ScreeningCancelled(
    override val dayScheduleId: DayScheduleId,
    val screeningId: ScreeningId,
    override val occurredAt: Instant
) : DayScheduleEvent