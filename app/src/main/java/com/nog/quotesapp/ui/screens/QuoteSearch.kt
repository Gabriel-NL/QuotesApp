package com.nog.quotesapp.ui.screens

import java.util.Locale

// All records remain visible; matching tiers only change their ordering.
fun <T> rankEntries(entries: List<T>, query: String, text: (T) -> String): List<T> {
    val phrase = query.trim().lowercase(Locale.ROOT)
    val words = phrase.split(Regex("\\s+")).filter { it.isNotBlank() }
    fun tier(value: String): Int {
        val normalized = value.lowercase(Locale.ROOT)
        return when {
            phrase.isBlank() || normalized.contains(phrase) -> 0
            words.any { normalized.contains(it) } -> 1
            else -> 2
        }
    }
    return entries.sortedWith(compareBy<T> { tier(text(it)) }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { text(it) })
}

fun matchesTags(tagIds: Set<Int>, included: Set<Int>, excluded: Set<Int>): Boolean =
    tagIds.containsAll(included) && tagIds.none { it in excluded }
