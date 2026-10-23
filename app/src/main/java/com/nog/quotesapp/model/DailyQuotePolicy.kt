package com.nog.quotesapp.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

const val RECENT_QUOTE_LIMIT = 5

data class DailyPeriod(val key: String, val nextMidnightMillis: Long)

// Calendar respects local daylight-saving transitions; adding 24 hours would not.
fun dailyPeriod(now: Long, zone: TimeZone = TimeZone.getDefault(), minutesOfDay:Int = 0): DailyPeriod {
    require(minutesOfDay in 0..1439)
    val boundary = Calendar.getInstance(zone).apply {
        timeInMillis=now
        set(Calendar.HOUR_OF_DAY,minutesOfDay/60); set(Calendar.MINUTE,minutesOfDay%60)
        set(Calendar.SECOND,0); set(Calendar.MILLISECOND,0)
    }
    if(boundary.timeInMillis>now) boundary.add(Calendar.DAY_OF_MONTH,-1)
    val key=String.format(Locale.ROOT,"%04d-%02d-%02d",boundary.get(Calendar.YEAR),boundary.get(Calendar.MONTH)+1,boundary.get(Calendar.DAY_OF_MONTH))
    boundary.add(Calendar.DAY_OF_MONTH,1)
    return DailyPeriod(key,boundary.timeInMillis)
}

fun effectiveHistoryLimit(configured: Int, entryCount: Int): Int =
    configured.coerceAtLeast(0).coerceAtMost((entryCount - 1).coerceAtLeast(0))

fun eligibleQuoteIds(ids: List<Int>, recent: List<Int>): List<Int> {
    val blocked = recent.toSet()
    return ids.filter { it !in blocked }
}
