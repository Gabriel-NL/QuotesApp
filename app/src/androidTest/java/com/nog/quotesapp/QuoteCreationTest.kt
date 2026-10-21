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
    @Test
    fun editReplacesLinksWithoutChangingId() = runBlocking {
        val oldTag = db.tagDao().insert(TagEntity(name = "Old")).toInt()
        val newTag = db.tagDao().insert(TagEntity(name = "New")).toInt()
        val repo = QuoteRepository(db)
        val id = repo.createQuote("Before", listOf(oldTag)).toInt()
        repo.updateQuote(id, "After", listOf(newTag))
        assertEquals("After", db.quoteDao().getById(id)?.text)
        assertEquals(listOf(newTag), db.quoteTagCrossRefDao().getAll().map { it.tagId })
        assertEquals(1, db.quoteDao().getAll().size)
    }

    @Test
    fun failedEditPreservesOriginalTextAndLinks() = runBlocking {
        val tag = db.tagDao().insert(TagEntity(name = "Kept")).toInt()
        val repo = QuoteRepository(db)
        val id = repo.createQuote("Before", listOf(tag)).toInt()
        var failed = false
        try { repo.updateQuote(id, "After", listOf(Int.MAX_VALUE)) }
        catch (exception: Exception) { failed = true }
        assertTrue(failed)
        assertEquals("Before", db.quoteDao().getById(id)?.text)
        assertEquals(listOf(tag), db.quoteTagCrossRefDao().getAll().map { it.tagId })
    }

    @Test
    fun bulkDeleteCleansLinksAndHistoryButKeepsTags() = runBlocking {
        val tag = db.tagDao().insert(TagEntity(name = "Kept")).toInt()
        val repo = QuoteRepository(db)
        val first = repo.createQuote("First", listOf(tag)).toInt()
        val second = repo.createQuote("Second", listOf(tag)).toInt()
        val kept = repo.createQuote("Kept", emptyList()).toInt()
        db.fifoDao().insert(com.nog.quotesapp.data.entity.FifoEntity(quoteId = first, insertedAt = 1))
        repo.deleteQuotes(listOf(first, second))
        assertEquals(listOf(kept), db.quoteDao().getAll().map { it.id })
        assertTrue(db.quoteTagCrossRefDao().getAll().isEmpty())
        assertTrue(db.fifoDao().getAll().isEmpty())
        assertTrue(db.tagDao().exists(tag))
    }

    @Test
    fun renamingTagKeepsQuoteLinksAndDeletingTagKeepsQuote() = runBlocking {
        val tag = db.tagDao().insert(TagEntity(name = "Before")).toInt()
        val quote = QuoteRepository(db).createQuote("Keep me", listOf(tag)).toInt()
        db.tagDao().updateTagNameUsingId(tag, "After")
        assertEquals("After", db.tagDao().getById(tag)?.name)
        assertEquals(tag, db.quoteTagCrossRefDao().getAll().single().tagId)
        db.tagDao().deleteById(tag)
        assertTrue(db.quoteTagCrossRefDao().getAll().isEmpty())
        assertNotNull(db.quoteDao().getById(quote))
    }
}
