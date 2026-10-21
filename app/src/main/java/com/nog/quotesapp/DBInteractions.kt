package com.nog.quotesapp

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.db.DatabaseProvider
import com.nog.quotesapp.data.entity.QuoteEntity
import com.nog.quotesapp.data.entity.TagEntity
import com.nog.quotesapp.debugText
import kotlinx.coroutines.launch


suspend fun addQuote(db: AppDatabase, quote: String): Long {
    Log.d("L.o.g"," Adding quote: "+quote)
    return db.quoteDao().insert(
        QuoteEntity(text = quote)
    )
}
suspend fun addTag(db: AppDatabase, tagName: String): Long {
    Log.d("L.o.g"," Adding tag: "+tagName)
    return db.tagDao().insert(
        TagEntity(name = tagName)
    )
}

suspend fun addQuoteTagCrossRef(
    db: AppDatabase,
    newTagId: Int,
    newQuoteId: Int
) {
    val quoteExists = db.quoteDao().exists(newQuoteId)
    val tagExists = db.tagDao().exists(newTagId)

    if (!quoteExists || !tagExists) {
        Log.e(
            "L.o.g",
            "CrossRef blocked: quoteExists=$quoteExists tagExists=$tagExists " +
                    "(quoteId=$newQuoteId, tagId=$newTagId)"
        )
        return
    }

    Log.d(
        "L.o.g",
        "Adding cross ref between quote=$newQuoteId tag=$newTagId"
    )

    db.quoteTagCrossRefDao().insert(
        quoteId = newQuoteId,
        tagId = newTagId
    )
}

suspend fun updateQuoteText(
    db: AppDatabase,
    id: Int,
    newText: String
) {
    db.quoteDao().updateQuoteTextUsingId(id, newText)
}

suspend fun updateTagName(
    db: AppDatabase,
    id: Int,
    newName: String
) {
    db.tagDao().updateTagNameUsingId(id, newName)
}

suspend fun displayQuotes(db: AppDatabase): String
{
    val quotes = db.quoteDao().getAll()

    return buildString {

        appendLine("QUOTES:")

        if (quotes.isEmpty())
        {
            appendLine("empty")
            return@buildString
        }

        quotes.forEach {
            appendLine("id=${it.id} | text=${it.text}")
        }
    }
}

suspend fun displayTags(db: AppDatabase): String
{
    val tags = db.tagDao().getAll()

    return buildString {

        appendLine("TAGS:")

        if (tags.isEmpty())
        {
            appendLine("empty")
            return@buildString
        }

        tags.forEach {
            appendLine("id=${it.id} | name=${it.name}")
        }
    }
}

suspend fun displayRelations(db: AppDatabase): String
{
    val relations = db.quoteTagCrossRefDao().getAll()

    return buildString {

        appendLine("RELATIONS:")

        if (relations.isEmpty())
        {
            appendLine("empty")
            return@buildString
        }

        relations.forEach {
            appendLine("quoteId=${it.quoteId} | tagId=${it.tagId}")
        }
    }
}

suspend fun displayAll(db: AppDatabase): String
{
    return buildString {

        appendLine("===== DATABASE STATE =====")
        appendLine()

        appendLine(displayQuotes(db))
        appendLine()
        appendLine(displayTags(db))
        appendLine()
        appendLine(displayRelations(db))
    }
}

fun ComponentActivity.testFullDatabase()
{
    val db: AppDatabase = DatabaseProvider.getDatabase(this)

    lifecycleScope.launch {

        debugText = "Running database test..."

        // ========= LIMPA TUDO =========

        db.quoteTagCrossRefDao().deleteAll()
        db.tagDao().deleteAll()
        db.quoteDao().deleteAll()

        // ========= INSERE FRASES =========

        val quote1Id = addQuote(db,"The cake is a lie").toInt()
        val quote2Id = addQuote(db,"Praise the sun").toInt()
        val quote3Id = addQuote(db,"Would you kindly?").toInt()


        // ========= INSERE TAGS =========

        val tag1Id = addTag(db,"Gaming").toInt()
        val tag2Id = addTag(db,"Motivation").toInt()

        // ========= RELAÇÕES =========

        addQuoteTagCrossRef(db, quote1Id, tag2Id)
        addQuoteTagCrossRef(db, quote2Id, tag2Id)

        // ========= ESTADO INICIAL =========

        val beforeDelete =displayAll(db)

        // ========= APAGA FRASE 1 =========

        db.quoteDao().deleteById(quote1Id)

        // ========= ESTADO FINAL =========


        debugText =
            beforeDelete
    }
}
