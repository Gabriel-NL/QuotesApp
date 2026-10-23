package com.nog.quotesapp

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nog.quotesapp.data.db.*
import com.nog.quotesapp.data.entity.*
import com.nog.quotesapp.data.repository.DailyQuoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.TimeZone

class DailyQuoteTest {
    private lateinit var db:AppDatabase
    private var time=1_761_091_200_000L
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
    }
    @After fun cleanup()=db.close()
    private fun repository(limit:Int=5)=DailyQuoteRepository(db,limit,{time},{TimeZone.getTimeZone("UTC")},{0})
    @Test fun emptyAndSingleQuoteAreSafe()=runBlocking {
        val repo=repository()
        assertNull(repo.refresh())
        val id=db.quoteDao().insert(QuoteEntity(text="Only")).toInt()
        assertEquals(id,repo.refresh()?.id)
        assertEquals(id,repo.refresh(true)?.id)
        assertTrue(db.fifoDao().getAll().isEmpty())
    }
    @Test fun filterChangesReplaceBlockedDailyAndEmptyFilterRestoresCandidates()=runBlocking {
        val first=db.quoteDao().insert(QuoteEntity(text="Work quote")).toInt()
        val second=db.quoteDao().insert(QuoteEntity(text="Untagged quote")).toInt()
        val tag=db.tagDao().insert(TagEntity(name="Work")).toInt()
        db.quoteTagCrossRefDao().insert(first,tag)
        var configuration=com.nog.quotesapp.model.AppSettings()
        val repo=DailyQuoteRepository(db,now={time},randomIndex={0},configured={configuration})
        assertEquals(first,repo.refresh()?.id)
        configuration=configuration.copy(dailyTagNames=setOf("Work"))
        assertEquals(second,repo.refresh()?.id)
        configuration=configuration.copy(dailyWhitelist=true)
        assertEquals(first,repo.refresh()?.id)
        configuration=configuration.copy(dailyTagNames=setOf("Missing"))
        assertNull(repo.refresh());assertNull(repo.observeDailyQuote().first())
        configuration=configuration.copy(dailyTagNames=emptySet())
        assertNotNull(repo.refresh())
    }
    @Test fun persistsOncePerDayAndRerollsWithoutRecentRepeats()=runBlocking {
        repeat(4) { db.quoteDao().insert(QuoteEntity(text="Quote $it")) }
        val first=repository().refresh()!!
        assertEquals(first,repository().refresh())
        val selected=mutableListOf(first.id)
        repeat(3) { selected += repository().refresh(true)!!.id }
        assertEquals(4,selected.toSet().size)
        assertEquals(3,db.fifoDao().getAll().size)
        time+=86_400_000
        val next=repository().refresh()!!
        assertFalse(next.id in selected.takeLast(3))
    }
    @Test fun oversizedHistoryClampsAndDeletedDailyIsReplaced()=runBlocking {
        val first=db.quoteDao().insert(QuoteEntity(text="First")).toInt()
        val second=db.quoteDao().insert(QuoteEntity(text="Second")).toInt()
        val repo=repository(100)
        assertEquals(first,repo.refresh()?.id)
        assertEquals(second,repo.refresh(true)?.id)
        assertEquals(1,db.fifoDao().getAll().size)
        db.quoteDao().deleteById(second)
        assertEquals(first,repo.refresh()?.id)
        assertTrue(db.fifoDao().getAll().isEmpty())
        db.quoteDao().deleteById(first)
        assertNull(repo.refresh())
    }
    @Test fun currentTextEditsAreVisibleToWidgetObservation()=runBlocking {
        val id=db.quoteDao().insert(QuoteEntity(text="Before")).toInt()
        val repo=repository(); repo.refresh()
        db.quoteDao().updateQuoteTextUsingId(id,"After")
        assertEquals("After",repo.observeDailyQuote().first()?.text)
    }
    @Test fun migrationPreservesOldQuotesTagsAndLinks()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="daily-migration-test.db"
        context.deleteDatabase(name)
        try {
            val file=context.getDatabasePath(name); file.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
                old.execSQL("CREATE TABLE quotes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, text TEXT NOT NULL)")
                old.execSQL("CREATE TABLE tags (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                old.execSQL("CREATE TABLE quote_fifo (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, quoteId INTEGER NOT NULL, insertedAt INTEGER NOT NULL)")
                old.execSQL("CREATE TABLE quote_tags_cross_ref (quoteId INTEGER NOT NULL, tagId INTEGER NOT NULL, PRIMARY KEY(quoteId,tagId), FOREIGN KEY(quoteId) REFERENCES quotes(id) ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE CASCADE)")
                old.execSQL("INSERT INTO quotes VALUES(7,'Keep this quote')")
                old.execSQL("INSERT INTO tags VALUES(3,'Keep this tag')")
                old.execSQL("INSERT INTO quote_tags_cross_ref VALUES(7,3)")
                old.version=1
            }
            val migrated=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(MIGRATION_1_2,MIGRATION_2_3).build()
            try {
                assertEquals("Keep this quote",migrated.quoteDao().getById(7)?.text)
                assertEquals("Keep this tag",migrated.tagDao().getById(3)?.name)
                assertEquals(3,migrated.quoteTagCrossRefDao().getAll().single().tagId)
                assertNull(migrated.dailyQuoteDao().get())
                assertEquals(7,DailyQuoteRepository(migrated).refresh()?.id)
            } finally { migrated.close() }
        } finally { context.deleteDatabase(name) }
    }
}
