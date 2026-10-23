package com.nog.quotesapp

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.nog.quotesapp.backup.*
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.*
import com.nog.quotesapp.data.repository.*
import com.nog.quotesapp.model.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SettingsBackupTest {
    private lateinit var db:AppDatabase
    private lateinit var backups:BackupRepository
    private lateinit var settings:SettingsRepository
    @Before fun setup() {
        db=Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,AppDatabase::class.java).build()
        backups=BackupRepository(db);settings=SettingsRepository(db)
    }
    @After fun cleanup()=db.close()
    private suspend fun prepare(csv:String,groups:Set<DataGroup>,mode:ImportMode=ImportMode.REPLACE)=backups.prepare(BackupCsv.decode(csv),groups,mode,AppSettings.KEYS,{false})
    @Test fun fullCsvRoundTripKeepsUnusedTagsAssociationsAndSettings()=runBlocking {
        val tag=db.tagDao().insert(TagEntity(name="Linked")).toInt()
        db.tagDao().insert(TagEntity(name="Unused"))
        QuoteRepository(db).createQuote("Text; with \"quotes\"",listOf(tag))
        settings.save(AppSettings(historyLimit=2,quoteLimit=800))
        DailyQuoteRepository(db).refresh()
        val exported=backups.export(DataGroup.entries.toSet(),true,false)
        val plan=prepare(exported.csv,DataGroup.entries.toSet())
        backups.apply(plan)
        assertEquals("Text; with \"quotes\"",db.quoteDao().getAll().single().text)
        assertEquals(setOf("Linked","Unused"),db.tagDao().getAll().map { it.name }.toSet())
        assertEquals(db.quoteDao().getAll().single().id,db.quoteTagCrossRefDao().getAll().single().quoteId)
        assertEquals(800,settings.current().quoteLimit)
    }
    @Test fun businessAndSettingsRollBackTogetherOnWriteFailure()=runBlocking {
        QuoteRepository(db).createQuote("Original",emptyList())
        val input=BackupBundle(setOf(DataGroup.QUOTES,DataGroup.SETTINGS),listOf(
            BackupRecord("quote",mapOf("id" to "1","text" to "Replacement")),
            BackupRecord("setting",mapOf("key" to "quote_limit","value_type" to "integer","value" to "123"))
        ))
        val plan=backups.prepare(input,input.groups,ImportMode.REPLACE,AppSettings.KEYS,{false})
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_settings BEFORE INSERT ON app_settings BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        var failed=false
        try { backups.apply(plan) } catch(error:Exception) { failed=true }
        assertTrue(failed)
        assertEquals("Original",db.quoteDao().getAll().single().text)
        assertEquals(AppSettings(),settings.current())
    }
    @Test fun stalePreviewCannotOverwriteNewerRecords()=runBlocking {
        QuoteRepository(db).createQuote("Original",emptyList())
        val export=backups.export(setOf(DataGroup.QUOTES),false,false)
        val plan=prepare(export.csv,setOf(DataGroup.QUOTES))
        QuoteRepository(db).createQuote("Added after preview",emptyList())
        var failed=false;try {backups.apply(plan)} catch(error:Exception) {failed=true}
        assertTrue(failed);assertEquals(2,db.quoteDao().getAll().size)
    }
    @Test fun mergeDeduplicatesAndRemapsCollidingSourceIds()=runBlocking {
        val localTag=db.tagDao().insert(TagEntity(name="Business")).toInt()
        val localQuote=QuoteRepository(db).createQuote("Alpha",emptyList()).toInt()
        val input=BackupBundle(setOf(DataGroup.QUOTES,DataGroup.TAGS,DataGroup.LINKS),listOf(
            BackupRecord("quote",mapOf("id" to "99","text" to " alpha ")),
            BackupRecord("quote",mapOf("id" to localQuote.toString(),"text" to "Beta")),
            BackupRecord("tag",mapOf("id" to "77","name" to "business")),
            BackupRecord("quote_tag",mapOf("quote_id" to "99","tag_id" to "77"))
        ))
        backups.apply(backups.prepare(input,input.groups,ImportMode.MERGE,emptySet(),{false}))
        assertEquals(2,db.quoteDao().getAll().size)
        assertEquals(1,db.tagDao().getAll().size)
        assertEquals(QuoteTagCrossRefEntity(localQuote,localTag),db.quoteTagCrossRefDao().getAll().single())
    }
    @Test fun reducedLimitsKeepExistingRecordsAndRejectNewLongText()=runBlocking {
        val text="Existing long text"
        val id=QuoteRepository(db).createQuote(text,emptyList()).toInt()
        val tag=db.tagDao().insert(TagEntity(name="Existing long tag")).toInt()
        settings.save(AppSettings(quoteLimit=1,tagLimit=1))
        QuoteRepository(db).updateQuote(id,text,emptyList())
        TagRepository(db).saveTag(tag,"Existing long tag")
        var failed=false;try {QuoteRepository(db).createQuote("Too long",emptyList())} catch(error:Exception) {failed=true}
        assertTrue(failed);assertEquals(text,db.quoteDao().getById(id)?.text)
    }
    @Test fun timeChangeAndHistoryClearKeepCurrentDailyQuote()=runBlocking {
        repeat(3) {QuoteRepository(db).createQuote("Quote $it",emptyList())}
        val daily=DailyQuoteRepository(db,configured={settings.current()})
        daily.refresh();daily.refresh(true)
        val original=db.dailyQuoteDao().get()!!.quoteId
        settings.save(settings.current().copy(dailyMinutes=600,historyLimit=1))
        assertEquals(original,daily.refresh()?.id)
        assertTrue(db.fifoDao().getAll().size<=1)
        settings.clearHistory()
        assertTrue(db.fifoDao().getAll().isEmpty())
        assertEquals(original,db.dailyQuoteDao().get()?.quoteId)
    }
    @Test fun settingsOnlyImportKeepsUnselectedKeysAndAllBusinessData()=runBlocking {
        val quote=QuoteRepository(db).createQuote("Keep",emptyList()).toInt()
        val input=BackupBundle(setOf(DataGroup.SETTINGS),listOf(
            BackupRecord("setting",mapOf("key" to "history_limit","value_type" to "integer","value" to "0")),
            BackupRecord("setting",mapOf("key" to "quote_limit","value_type" to "integer","value" to "50"))
        ))
        backups.apply(backups.prepare(input,input.groups,ImportMode.MERGE,setOf("history_limit"),{false}))
        assertEquals(0,settings.current().historyLimit)
        assertEquals(5000,settings.current().quoteLimit)
        assertEquals("Keep",db.quoteDao().getById(quote)?.text)
    }
}
