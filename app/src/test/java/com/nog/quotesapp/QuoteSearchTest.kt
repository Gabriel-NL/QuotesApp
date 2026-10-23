package com.nog.quotesapp

import com.nog.quotesapp.model.rankEntries
import com.nog.quotesapp.model.matchesTags
import com.nog.quotesapp.model.EntryOrder
import org.junit.Assert.*
import org.junit.Test

class QuoteSearchTest {
    @Test fun sortingSupportsAlphabeticLengthAndBothDirections() {
        val values=listOf("Zulu","B","apple")
        assertEquals(listOf("apple","B","Zulu"),rankEntries(values,"") { it })
        assertEquals(listOf("Zulu","B","apple"),rankEntries(values,"",descending=true) { it })
        assertEquals(listOf("B","Zulu","apple"),rankEntries(values,"",EntryOrder.LETTER_COUNT) { it })
        assertEquals(listOf("apple","Zulu","B"),rankEntries(values,"",EntryOrder.LETTER_COUNT,true) { it })
    }
    @Test fun lengthAndReverseSortingKeepSearchMatchesAheadOfNonMatches() {
        val values=listOf("match this long sentence","z","match")
        assertEquals(listOf("match","match this long sentence","z"),rankEntries(values,"match",EntryOrder.LETTER_COUNT) { it })
        assertEquals(listOf("match this long sentence","match","z"),rankEntries(values,"match",EntryOrder.LETTER_COUNT,true) { it })
    }
    @Test fun lengthCountsUnicodeCodePointsRatherThanSurrogateHalves() {
        assertEquals(listOf("\uD83D\uDE00","ab"),rankEntries(listOf("ab","\uD83D\uDE00"),"",EntryOrder.LETTER_COUNT) { it })
    }
    @Test fun untaggedFilterAcceptsOnlyQuotesWithoutLinks() {
        assertTrue(matchesTags(emptySet(), emptySet(), emptySet(), onlyUntagged = true))
        assertFalse(matchesTags(setOf(1), emptySet(), emptySet(), onlyUntagged = true))
        assertTrue(matchesTags(setOf(1), emptySet(), emptySet(), onlyUntagged = false))
        assertFalse(matchesTags(emptySet(), setOf(1), emptySet(), onlyUntagged = true))
    }
    @Test fun ranksPhraseThenIndividualWordsThenOthers() {
        val entries = listOf("Zebra", "Sun shines", "Blue sun", "Bright moon", "A blue sun")
        assertEquals(listOf("A blue sun", "Blue sun", "Sun shines", "Bright moon", "Zebra"),
            rankEntries(entries, "blue sun") { it })
    }
    @Test fun emptySearchSortsWithoutRemovingRecords() {
        assertEquals(listOf("apple", "Banana", "zebra"),
            rankEntries(listOf("zebra", "Banana", "apple"), "  ") { it })
    }
    @Test fun exclusionWinsAndAllIncludedTagsAreRequired() {
        assertTrue(matchesTags(setOf(1,2), setOf(1,2), emptySet()))
        assertFalse(matchesTags(setOf(1), setOf(1,2), emptySet()))
        assertFalse(matchesTags(setOf(1,2), setOf(1), setOf(2)))
        assertFalse(matchesTags(setOf(1), setOf(1), setOf(1)))
        assertTrue(matchesTags(emptySet(), emptySet(), emptySet()))
    }
}
