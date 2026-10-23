package com.nog.quotesapp.model

import java.util.Locale

fun tagFilterKey(name:String)=name.trim().lowercase(Locale.ROOT)

fun matchesDailyTags(names:Collection<String>,settings:AppSettings):Boolean {
    if(settings.dailyTagNames.isEmpty()) return true
    val selected=settings.dailyTagNames.map(::tagFilterKey).toSet()
    val matches=names.any { tagFilterKey(it) in selected }
    return if(settings.dailyWhitelist) matches else !matches
}

fun filterDailyQuotes(quotes:List<Quote>,tags:List<Tag>,links:List<QuoteTagLink>,settings:AppSettings):List<Quote> {
    val names=tags.associate { it.id to it.name }
    val byQuote=links.groupBy { it.quoteId }
    return quotes.filter { quote -> matchesDailyTags(byQuote[quote.id].orEmpty().mapNotNull { names[it.tagId] },settings) }
}
