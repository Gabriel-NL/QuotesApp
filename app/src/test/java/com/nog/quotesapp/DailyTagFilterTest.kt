package com.nog.quotesapp

import com.nog.quotesapp.model.*
import org.junit.Assert.*
import org.junit.Test

class DailyTagFilterTest {
    private val quotes=listOf(Quote(1,"First"),Quote(2,"Second"),Quote(3,"Untagged"))
    private val tags=listOf(Tag(7,"Work"),Tag(8,"Love"))
    private val links=listOf(QuoteTagLink(1,7),QuoteTagLink(1,8),QuoteTagLink(2,8))
    private fun ids(s:AppSettings)=filterDailyQuotes(quotes,tags,links,s).map { it.id }
    @Test fun blacklistRejectsAnyMatchButKeepsUntagged() {
        assertEquals(listOf(2,3),ids(AppSettings(dailyTagNames=setOf("work"))))
    }
    @Test fun whitelistAllowsAnyMatchAndExcludesUntagged() {
        assertEquals(listOf(1,2),ids(AppSettings(dailyTagNames=setOf("WORK","Love"),dailyWhitelist=true)))
    }
    @Test fun emptyListAllowsAllInBothModes() {
        assertEquals(listOf(1,2,3),ids(AppSettings()))
        assertEquals(listOf(1,2,3),ids(AppSettings(dailyWhitelist=true)))
    }
    @Test fun missingTagInWhitelistDoesNotAccidentallyAllowEverything() {
        assertTrue(ids(AppSettings(dailyTagNames=setOf("Deleted"),dailyWhitelist=true)).isEmpty())
    }
    @Test fun settingsRoundTripPreservesNamesWithDelimitersAndNewlines() {
        val configuration=AppSettings(dailyTagNames=setOf("A;B", "1:two\nthree", "Ol\u00e1"),dailyWhitelist=true)
        assertEquals(configuration,AppSettings.from(configuration.values()))
        assertEquals(AppSettings(),AppSettings.from(emptyList()))
    }
    @Test fun malformedFilterSettingsAreRejected() {
        for(value in listOf("-1:bad","100:short","x:bad")) {
            assertTrue(runCatching { AppSettings.from(listOf(SettingValue("daily_tag_names","string",value))) }.isFailure)
        }
    }
}
