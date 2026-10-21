package com.nog.quotesapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.TagEntity
import com.nog.quotesapp.data.repository.QuoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QuoteCreationTest {
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun createsQuoteAndDistinctTagLinks() = runBlocking {
        val firstTag = db.tagDao().insert(TagEntity(name = "First")).toInt()
        val secondTag = db.tagDao().insert(TagEntity(name = "Second")).toInt()
        val id = QuoteRepository(db).createQuote("  My quote  ", listOf(firstTag, secondTag, firstTag))
        val quotes = db.quoteDao().observeAll().first()
        assertEquals(1, quotes.size)
        assertEquals(id.toInt(), quotes.single().id)
        assertEquals("My quote", quotes.single().text)
        val links = db.quoteTagCrossRefDao().getAll()
        assertEquals(setOf(firstTag, secondTag), links.map { it.tagId }.toSet())
        assertTrue(links.all { it.quoteId == id.toInt() })
    }

    @Test
    fun createsQuoteWithoutTags() = runBlocking {
        QuoteRepository(db).createQuote("No tags", emptyList())
        assertEquals("No tags", db.quoteDao().getAll().single().text)
        assertTrue(db.quoteTagCrossRefDao().getAll().isEmpty())
    }

    @Test
    fun missingTagRollsBackQuoteAndEarlierLinks() = runBlocking {
        val tagId = db.tagDao().insert(TagEntity(name = "Existing")).toInt()
        var failed = false
        try {
            QuoteRepository(db).createQuote("Must roll back", listOf(tagId, Int.MAX_VALUE))
        } catch (exception: Exception) { failed = true }
        assertTrue("Missing tag must fail creation", failed)
        assertTrue(db.quoteDao().getAll().isEmpty())
        assertTrue(db.quoteTagCrossRefDao().getAll().isEmpty())
        assertTrue(db.tagDao().exists(tagId))
    }
}
