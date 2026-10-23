package com.nog.quotesapp.backup

import androidx.room.withTransaction
import com.nog.quotesapp.data.db.AppDatabase
import com.nog.quotesapp.data.entity.*
import com.nog.quotesapp.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportPreview(val csv:String,val summary:String)
interface BackupStore {
    suspend fun export(groups:Set<DataGroup>,includeDependencies:Boolean,includeQuoteTags:Boolean):ExportPreview
    suspend fun prepare(input:BackupBundle,groups:Set<DataGroup>,mode:ImportMode,settingKeys:Set<String>,canUseFolder:(String)->Boolean):ImportPlan
    suspend fun apply(plan:ImportPlan)
}
class BackupRepository(private val db:AppDatabase):BackupStore {
    private suspend fun snapshot():BackupSnapshot {
        val records=mutableListOf<BackupRecord>()
        db.quoteDao().getAll().sortedBy { it.id }.forEach { records+=BackupRecord("quote",mapOf("id" to it.id.toString(),"text" to it.text)) }
        db.tagDao().getAll().sortedBy { it.id }.forEach { records+=BackupRecord("tag",mapOf("id" to it.id.toString(),"name" to it.name)) }
        db.quoteTagCrossRefDao().getAll().sortedWith(compareBy<QuoteTagCrossRefEntity> { it.quoteId }.thenBy { it.tagId }).forEach { records+=BackupRecord("quote_tag",mapOf("quote_id" to it.quoteId.toString(),"tag_id" to it.tagId.toString())) }
        db.fifoDao().getAll().sortedBy { it.id }.forEach { records+=BackupRecord("quote_history",mapOf("id" to it.id.toString(),"quote_id" to it.quoteId.toString(),"inserted_at" to it.insertedAt.toString())) }
        db.dailyQuoteDao().get()?.let { records+=BackupRecord("daily_quote",mapOf("id" to it.id.toString(),"quote_id" to (it.quoteId?.toString() ?: ""),"local_date" to it.localDate)) }
        SettingsRepository.read(db).values().forEach { records+=BackupRecord("setting",mapOf("key" to it.key,"value_type" to it.type,"value" to it.value)) }
        fun next(table:String):Int {
            db.openHelper.writableDatabase.query("SELECT seq FROM sqlite_sequence WHERE name = ?",arrayOf(table)).use { cursor ->
                val sequence=if(cursor.moveToFirst()) cursor.getLong(0) else 0L
                require(sequence<Int.MAX_VALUE) { "Database ID capacity exceeded." }
                return (sequence+1).toInt()
            }
        }
        return BackupSnapshot(BackupBundle(DataGroup.entries.toSet(),records),next("quotes"),next("tags"))
    }
    override suspend fun export(groups:Set<DataGroup>,includeDependencies:Boolean,includeQuoteTags:Boolean):ExportPreview=withContext(Dispatchers.IO) {
        require(groups.isNotEmpty()) { "Select at least one dataset." }
        val selected=groups.toMutableSet()
        if(includeQuoteTags && DataGroup.QUOTES in selected) selected+=setOf(DataGroup.TAGS,DataGroup.LINKS)
        if(includeDependencies) {
            if(DataGroup.DAILY in selected) selected+=DataGroup.HISTORY
            if(selected.any { it in setOf(DataGroup.HISTORY,DataGroup.DAILY,DataGroup.LINKS) }) selected+=DataGroup.QUOTES
            if(DataGroup.LINKS in selected) selected+=DataGroup.TAGS
        }
        val captured=db.withTransaction { snapshot() }
        val bundle=BackupBundle(selected,captured.bundle.records.filter { row -> selected.any { it.recordType==row.type } })
        BackupCsv.validate(bundle)
        val csv=BackupCsv.encode(bundle)
        require(csv.toByteArray(Charsets.UTF_8).size<=MAX_BACKUP_BYTES) { "Export exceeds 32 MB. Select fewer datasets." }
        ExportPreview(csv,selected.sortedBy { it.ordinal }.joinToString("\n") { "${it.key}: ${bundle.rows(it).size} record(s)" })
    }
    override suspend fun prepare(input:BackupBundle,groups:Set<DataGroup>,mode:ImportMode,settingKeys:Set<String>,canUseFolder:(String)->Boolean):ImportPlan =
        withContext(Dispatchers.IO) { db.withTransaction { ImportPlanner.plan(snapshot(),input,groups,mode,settingKeys,canUseFolder) } }

    override suspend fun apply(plan:ImportPlan)=db.withTransaction {
        check(snapshot()==plan.before) { "Data changed since the preview. Preview the import again before applying." }
        val after=plan.after;val selected=plan.selected;val before=plan.before.bundle
        val historyChanged=after.rows(DataGroup.HISTORY)!=before.rows(DataGroup.HISTORY)
        val dailyChanged=after.rows(DataGroup.DAILY)!=before.rows(DataGroup.DAILY)
        if(DataGroup.LINKS in selected) db.quoteTagCrossRefDao().deleteAll()
        if(DataGroup.HISTORY in selected || historyChanged) db.fifoDao().deleteAll()
        if(DataGroup.DAILY in selected || dailyChanged) db.dailyQuoteDao().deleteAll()
        if(plan.mode==ImportMode.REPLACE) {
            if(DataGroup.QUOTES in selected) db.quoteDao().deleteAll()
            if(DataGroup.TAGS in selected) db.tagDao().deleteAll()
        }
        val oldQuotes=before.rows(DataGroup.QUOTES).map { it.number("id") }.toSet()
        val oldTags=before.rows(DataGroup.TAGS).map { it.number("id") }.toSet()
        if(DataGroup.QUOTES in selected) after.rows(DataGroup.QUOTES).filter { plan.mode==ImportMode.REPLACE || it.number("id") !in oldQuotes }.forEach {
            db.quoteDao().insert(QuoteEntity(id=it.number("id"),text=it["text"]))
        }
        if(DataGroup.TAGS in selected) after.rows(DataGroup.TAGS).filter { plan.mode==ImportMode.REPLACE || it.number("id") !in oldTags }.forEach {
            db.tagDao().insert(TagEntity(id=it.number("id"),name=it["name"]))
        }
        if(DataGroup.LINKS in selected) after.rows(DataGroup.LINKS).forEach { db.quoteTagCrossRefDao().insert(it.number("quote_id"),it.number("tag_id")) }
        if(DataGroup.HISTORY in selected || historyChanged) after.rows(DataGroup.HISTORY).forEach {
            db.fifoDao().insert(FifoEntity(id=it.number("id"),quoteId=it.number("quote_id"),insertedAt=it["inserted_at"].toLong()))
        }
        if(DataGroup.DAILY in selected || dailyChanged) after.rows(DataGroup.DAILY).singleOrNull()?.let {
            db.dailyQuoteDao().save(DailyQuoteEntity(quoteId=it["quote_id"].toIntOrNull(),localDate=it["local_date"]))
        }
        if(DataGroup.SETTINGS in selected) SettingsRepository.write(db,com.nog.quotesapp.model.AppSettings.from(after.rows(DataGroup.SETTINGS).map {
            com.nog.quotesapp.model.SettingValue(it["key"],it["value_type"],it["value"])
        }))
    }
}
