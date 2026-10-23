package com.nog.quotesapp

import com.nog.quotesapp.model.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DailyQuotePolicyTest {
    @Test fun cacheAlwaysLeavesACandidate() {
        assertEquals(0,effectiveHistoryLimit(5,0))
        assertEquals(0,effectiveHistoryLimit(5,1))
        assertEquals(1,effectiveHistoryLimit(5,2))
        assertEquals(5,effectiveHistoryLimit(5,50))
        assertEquals(0,effectiveHistoryLimit(-1,4))
        assertEquals(listOf(3),eligibleQuoteIds(listOf(1,2,3),listOf(2,1)))
    }
    @Test fun nextMidnightUsesLocalDateAndDaylightSaving() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val start = Calendar.getInstance(zone).apply {
            clear(); set(2026,Calendar.MARCH,8,0,0,0)
        }.timeInMillis
        val period = dailyPeriod(start,zone)
        assertEquals("2026-03-08",period.key)
        assertEquals(23*60*60*1000L,period.nextMidnightMillis-start)
        assertEquals("2026-03-09",dailyPeriod(period.nextMidnightMillis,zone).key)
    }
    @Test fun timezoneChangesCalendarDateWithoutChangingInstant() {
        val instant = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(2026,Calendar.OCTOBER,22,1,0,0)
        }.timeInMillis
        assertEquals("2026-10-22",dailyPeriod(instant,TimeZone.getTimeZone("UTC")).key)
        assertEquals("2026-10-21",dailyPeriod(instant,TimeZone.getTimeZone("America/Fortaleza")).key)
    }
}
