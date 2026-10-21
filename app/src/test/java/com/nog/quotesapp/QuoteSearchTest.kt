package com.nog.quotesapp

import com.nog.quotesapp.ui.screens.rankEntries
import com.nog.quotesapp.ui.screens.matchesTags
import org.junit.Assert.*
import org.junit.Test

class QuoteSearchTest {
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
