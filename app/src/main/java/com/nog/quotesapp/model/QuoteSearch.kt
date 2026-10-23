package com.nog.quotesapp.model

import java.util.Locale

// All records remain visible; matching tiers only change their ordering.
enum class EntryOrder { ALPHABETIC, LETTER_COUNT }

fun <T> rankEntries(entries: List<T>, query: String, order: EntryOrder = EntryOrder.ALPHABETIC,
    descending: Boolean = false, text: (T) -> String): List<T> {
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
    val alphabetic=compareBy<T,String>(String.CASE_INSENSITIVE_ORDER) { text(it) }
    val ordering=if(order==EntryOrder.LETTER_COUNT) compareBy<T> { text(it).codePointCount(0,text(it).length) }.then(alphabetic) else alphabetic
    return entries.sortedWith(compareBy<T> { tier(text(it)) }.then(if(descending) ordering.reversed() else ordering))
}

fun matchesTags(tagIds: Set<Int>, included: Set<Int>, excluded: Set<Int>, onlyUntagged: Boolean = false): Boolean =
    (!onlyUntagged || tagIds.isEmpty()) && tagIds.containsAll(included) && tagIds.none { it in excluded }
