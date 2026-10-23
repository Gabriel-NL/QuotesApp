package com.nog.quotesapp.backup

import com.nog.quotesapp.model.*
import java.text.SimpleDateFormat
import java.util.Locale

const val CSV_DELIMITER=';'
const val CSV_VERSION=1
const val DATABASE_VERSION=3
const val MAX_BACKUP_BYTES=32*1024*1024

enum class DataGroup(val key:String,val recordType:String) {
    QUOTES("quotes","quote"), TAGS("tags","tag"), LINKS("relationships","quote_tag"),
    HISTORY("history","quote_history"), DAILY("daily","daily_quote"), SETTINGS("settings","setting")
}
data class BackupRecord(val type:String,val fields:Map<String,String>) {
    operator fun get(key:String)=fields[key].orEmpty()
    fun number(key:String)=get(key).toIntOrNull() ?: error("Invalid $key in $type")
}
data class BackupBundle(val groups:Set<DataGroup>,val records:List<BackupRecord>,val sourceZone:String?=null) {
    fun rows(group:DataGroup)=records.filter { it.type==group.recordType }
    fun counts()=groups.associateWith { rows(it).size }
}

object BackupCsv {
    val header=listOf("record_type","id","quote_id","tag_id","text","name","inserted_at","local_date","key","value_type","value")
    private val allowed=mapOf(
        "metadata" to setOf("key","value_type","value"), "dataset" to setOf("key","value_type","value"),
        "quote" to setOf("id","text"),"tag" to setOf("id","name"),"quote_tag" to setOf("quote_id","tag_id"),
        "quote_history" to setOf("id","quote_id","inserted_at"),"daily_quote" to setOf("id","quote_id","local_date"),
        "setting" to setOf("key","value_type","value")
    )
    fun encode(bundle:BackupBundle):String {
        val rows=mutableListOf(header)
        fun row(record:BackupRecord)=listOf(record.type)+header.drop(1).map { record[it] }
        rows+=row(BackupRecord("metadata",mapOf("key" to "format_version","value_type" to "integer","value" to CSV_VERSION.toString())))
        rows+=row(BackupRecord("metadata",mapOf("key" to "database_schema_version","value_type" to "integer","value" to DATABASE_VERSION.toString())))
        rows+=row(BackupRecord("metadata",mapOf("key" to "source_timezone","value_type" to "string","value" to (bundle.sourceZone ?: java.util.TimeZone.getDefault().id))))
        bundle.groups.sortedBy { it.ordinal }.forEach { group ->
            rows+=row(BackupRecord("dataset",mapOf("key" to group.key,"value_type" to "integer","value" to bundle.rows(group).size.toString())))
        }
        bundle.records.forEach { rows+=row(it) }
        return rows.joinToString("\r\n",postfix="\r\n") { fields -> fields.joinToString(CSV_DELIMITER.toString()) { value ->
            if(value.any { it==CSV_DELIMITER || it=='"' || it=='\r' || it=='\n' }) "\""+value.replace("\"","\"\"")+"\"" else value
        } }
    }
    fun decode(text:String):BackupBundle {
        require(text.toByteArray(Charsets.UTF_8).size<=MAX_BACKUP_BYTES) { "CSV exceeds 32 MB." }
        val rows=parse(text.removePrefix("\uFEFF"))
        require(rows.firstOrNull()==header) { "Invalid CSV header. Use an app CSV export with semicolon delimiters." }
        require(rows.size<=100003) { "Too many CSV records." }
        val all=rows.drop(1).filterNot { it==listOf("") }.mapIndexed { index,columns ->
            require(columns.size==header.size) { "Wrong column count at record ${index+2}." }
            val type=columns[0]; val permitted=allowed[type] ?: error("Unknown record type: $type")
            val fields=header.drop(1).zip(columns.drop(1)).toMap()
            require(fields.all { (key,value) -> value.isEmpty() || key in permitted }) { "Unexpected field in $type." }
            BackupRecord(type,fields)
        }
        val metadata=all.filter { it.type=="metadata" }
        require(metadata.map { it["key"] }.toSet().containsAll(setOf("format_version","database_schema_version")) && metadata.map { it["key"] }.distinct().size==metadata.size && metadata.all { it["key"] in setOf("format_version","database_schema_version","source_timezone") }) { "Missing or duplicated CSV version metadata." }
        metadata.forEach { require(it["value_type"]==if(it["key"]=="source_timezone") "string" else "integer") { "Invalid metadata type." } }
        require(metadata.single { it["key"]=="format_version" }.number("value")==CSV_VERSION) { "Unsupported CSV format version." }
        require(metadata.single { it["key"]=="database_schema_version" }.number("value") in 1..DATABASE_VERSION) { "Unsupported database version." }
        val manifests=all.filter { it.type=="dataset" }
        require(manifests.isNotEmpty() && manifests.map { it["key"] }.distinct().size==manifests.size) { "Missing or repeated dataset manifest." }
        val groups=manifests.map { manifest ->
            require(manifest["value_type"]=="integer")
            DataGroup.entries.singleOrNull { it.key==manifest["key"] } ?: error("Unknown dataset: ${manifest["key"]}")
        }.toSet()
        val bundle=BackupBundle(groups,all.filter { it.type !in setOf("metadata","dataset") },metadata.singleOrNull { it["key"]=="source_timezone" }?.get("value"))
        require(bundle.records.all { record -> groups.any { it.recordType==record.type } }) { "Record outside declared datasets." }
        manifests.forEach { require(it.number("value")==bundle.rows(groups.single { group -> group.key==it["key"] }).size) { "Dataset count mismatch: ${it["key"]}" } }
        validate(bundle)
        return bundle
    }
    fun validate(bundle:BackupBundle) {
        fun unique(group:DataGroup,key:(BackupRecord)->String) {
            val rows=bundle.rows(group)
            require(rows.map(key).distinct().size==rows.size) { "Repeated identifier in ${group.key}." }
        }
        fun positive(row:BackupRecord,key:String) { require(row.number(key)>0) { "IDs must be positive." } }
        for(group in listOf(DataGroup.QUOTES,DataGroup.TAGS,DataGroup.HISTORY,DataGroup.DAILY)) {
            unique(group) { it.number("id").toString() };bundle.rows(group).forEach { positive(it,"id") }
        }
        bundle.rows(DataGroup.QUOTES).forEach { require(it["text"].isNotBlank()) { "Empty quote." } }
        bundle.rows(DataGroup.TAGS).forEach { require(it["name"].isNotBlank()) { "Empty tag." } }
        unique(DataGroup.LINKS) { "${it.number("quote_id")}/${it.number("tag_id")}" }
        unique(DataGroup.SETTINGS) { it["key"] }
        bundle.rows(DataGroup.SETTINGS).forEach {
            require(it["key"].isNotBlank() && it["value_type"] in setOf("string","integer","boolean")) { "Invalid setting record." }
            if(it["value_type"]=="integer") require(it["value"].toLongOrNull()!=null) { "Invalid setting integer." }
            if(it["value_type"]=="boolean") require(it["value"] in setOf("true","false")) { "Invalid setting boolean." }
        }
        val quotes=bundle.rows(DataGroup.QUOTES).map { it.number("id") }.toSet()
        val tags=bundle.rows(DataGroup.TAGS).map { it.number("id") }.toSet()
        bundle.rows(DataGroup.LINKS).forEach {
            require(it.number("quote_id") in quotes && it.number("tag_id") in tags) { "Link dependency missing. Export quotes and tags with relationships." }
        }
        bundle.rows(DataGroup.HISTORY).forEach {
            require(it.number("quote_id") in quotes && (it["inserted_at"].toLongOrNull() ?: -1)>=0) { "Invalid history record or missing quote." }
        }
        val daily=bundle.rows(DataGroup.DAILY)
        require(daily.size<=1)
        daily.forEach {
            require(it.number("id")==1)
            require(it["quote_id"].isEmpty() || it.number("quote_id") in quotes) { "Daily quote dependency missing." }
            val format=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply { isLenient=false }
            val parsed=runCatching { format.parse(it["local_date"]) }.getOrNull()
            require(parsed!=null && format.format(parsed)==it["local_date"]) { "Invalid daily date." }
        }
    }
    // Strict CSV state machine, including quoted line breaks and doubled quotes.
    private fun parse(text:String):List<List<String>> {
        val rows=mutableListOf<List<String>>();var row=mutableListOf<String>();val field=StringBuilder()
        var quoted=false;var closed=false;var started=false;var i=0
        fun finishField() { row+=field.toString();field.setLength(0);closed=false;started=false }
        fun finishRow() { finishField();rows+=row;row=mutableListOf() }
        while(i<text.length) {
            val c=text[i]
            if(quoted) {
                if(c=='"') {
                    if(i+1<text.length && text[i+1]=='"') { field.append('"');i++ }
                    else { quoted=false;closed=true }
                } else field.append(c)
            } else when(c) {
                '"' -> { require(!started && !closed) { "Unexpected CSV quote." };quoted=true;started=true }
                CSV_DELIMITER -> finishField()
                '\r','\n' -> { finishRow();if(c=='\r' && i+1<text.length && text[i+1]=='\n') i++ }
                else -> { require(!closed) { "Characters after closing CSV quote." };field.append(c);started=true }
            }
            i++
        }
        require(!quoted) { "Unclosed CSV field." }
        if(started || closed || field.isNotEmpty() || row.isNotEmpty()) finishRow()
        return rows
    }
}
