package com.nog.quotesapp.model

// Plain app data: no Room annotations or Android Context required.
data class Quote(val id: Int, val text: String)
data class Tag(val id: Int, val name: String)
data class QuoteTagLink(val quoteId: Int, val tagId: Int)
data class QuoteDraft(val quote: Quote, val tagIds: List<Int>)

object EntryLimits {
    const val QUOTE_LENGTH = 5000
    const val TAG_NAME_LENGTH = 100
}
