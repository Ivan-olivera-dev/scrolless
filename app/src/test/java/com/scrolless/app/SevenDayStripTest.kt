package com.scrolless.app

import com.scrolless.app.core.DayCell
import com.scrolless.app.core.DayRecord
import com.scrolless.app.core.DayStatus
import com.scrolless.app.core.ProtectionConfig
import com.scrolless.app.core.ProtectionMode
import com.scrolless.app.core.RuntimeState
import com.scrolless.app.core.SessionController
import com.scrolless.app.core.ShortFormTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SevenDayStripTest {

    private val config = ProtectionConfig(ProtectionMode.MINDFUL_BUDGET, 900, ShortFormTarget.entries.toSet(), emptySet())

    // 2026-11-15 is a Sunday.
    private val today = "2026-11-15"

    @Test
    fun lastSevenDays_coversRealDatesEndingTodayWithIsoWeekdays() {
        val cells = SessionController.lastSevenDays(RuntimeState(dayIso = today), config, today)
        assertEquals(
            listOf("2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12", "2026-11-13", "2026-11-14", "2026-11-15"),
            cells.map { it.dateIso }
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), cells.map { it.dayOfWeek })
    }

    @Test
    fun lastSevenDays_classifiesHistoryAgainstLimitAndMarksTodayInProgress() {
        val state = RuntimeState(
            dayIso = today,
            activeSecondsToday = 5_000,
            history = listOf(
                DayRecord("2026-11-08", 0, 0), // outside the window
                DayRecord("2026-11-10", 900, 1), // exactly the limit -> within
                DayRecord("2026-11-12", 901, 3), // over
                DayRecord("2026-11-14", 0, 0)
            )
        )
        val cells = SessionController.lastSevenDays(state, config, today)
        assertEquals(
            listOf(
                DayCell("2026-11-09", 1, DayStatus.NO_RECORD),
                DayCell("2026-11-10", 2, DayStatus.WITHIN_LIMIT),
                DayCell("2026-11-11", 3, DayStatus.NO_RECORD),
                DayCell("2026-11-12", 4, DayStatus.OVER_LIMIT),
                DayCell("2026-11-13", 5, DayStatus.NO_RECORD),
                DayCell("2026-11-14", 6, DayStatus.WITHIN_LIMIT),
                DayCell("2026-11-15", 7, DayStatus.TODAY)
            ),
            cells
        )
    }

    @Test
    fun lastSevenDays_weekdaysFollowTheActualDate() {
        // 2026-11-18 is a Wednesday: window starts on Thursday 2026-11-12.
        val cells = SessionController.lastSevenDays(RuntimeState(dayIso = "2026-11-18"), config, "2026-11-18")
        assertEquals(listOf(4, 5, 6, 7, 1, 2, 3), cells.map { it.dayOfWeek })
        assertEquals("2026-11-12", cells.first().dateIso)
        assertEquals(DayStatus.TODAY, cells.last().status)
    }

    @Test
    fun lastSevenDays_invalidTodayYieldsEmpty() {
        assertEquals(emptyList<DayCell>(), SessionController.lastSevenDays(RuntimeState(), config, "not-a-date"))
    }

    @Test
    fun weekdayInitial_esAndEn() {
        assertEquals("LMXJVSD", (1..7).joinToString("") { SessionController.weekdayInitial(it, isEs = true) })
        assertEquals("MTWTFSS", (1..7).joinToString("") { SessionController.weekdayInitial(it, isEs = false) })
    }
}
