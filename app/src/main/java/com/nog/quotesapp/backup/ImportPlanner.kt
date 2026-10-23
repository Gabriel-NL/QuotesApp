package com.nog.quotesapp.backup

import com.nog.quotesapp.model.*
import java.util.Locale

fun normalizedEntry(value:String)=value.trim().lowercase(Locale.ROOT)
data class BackupSnapshot(val bundle:BackupBundle,val nextQuoteId:Int,val nextTagId:Int) {
    val settings:AppSettings get()=AppSettings.from(bundle.rows(DataGroup.SETTINGS).map { SettingValue(it["key"],it["value_type"],it["value"]) })
}
enum class ImportMode { MERGE, REPLACE }
data class ImportPlan(val before:BackupSnapshot,val after:BackupBundle,val selected:Set<DataGroup>,val mode:ImportMode,val summary:String)

object ImportPlanner {
    fun plan(before:BackupSnapshot,input:BackupBundle,selected:Set<DataGroup>,mode:ImportMode,
        settingKeys:Set<String>,canUseFolder:(String)->Boolean,now:Long=System.currentTimeMillis()):ImportPlan {
        require(selected.isNotEmpty() && input.groups.containsAll(selected)) { "Choose datasets present in the file." }
        BackupCsv.validate(input)
        val messages=mutableListOf<String>()
        val fullRestore=mode==ImportMode.REPLACE && selected.containsAll(DataGroup.entries) && settingKeys.containsAll(setOf("quote_limit","tag_limit"))
        val old=before.bundle
        val incomingSettings=if(DataGroup.SETTINGS in selected) input.rows(DataGroup.SETTINGS).filter { it["key"] in settingKeys } else emptyList()
        val known=incomingSettings.filter { it["key"] in AppSettings.KEYS }.map { SettingValue(it["key"],it["value_type"],it["value"]) }
        input.rows(DataGroup.SETTINGS).filter { it["key"] !in AppSettings.KEYS }.forEach { messages+="Unknown setting ignored: ${it["key"]}" }
        var settings=AppSettings.from(known,before.settings)
        if(known.any { it.key=="backup_folder" } && settings.backupFolder.isNotEmpty() && !canUseFolder(settings.backupFolder)) {
            messages+="Imported backup folder is unavailable. Current folder kept; choose a folder on this device before activating a new location."
            settings=settings.copy(backupFolder=before.settings.backupFolder)
        }
        fun rows(g:DataGroup)=old.rows(g)
        if(mode==ImportMode.REPLACE) {
            if(DataGroup.QUOTES in selected) {
                for(g in listOf(DataGroup.LINKS,DataGroup.HISTORY,DataGroup.DAILY)) {
                    val affected=if(g==DataGroup.DAILY) rows(g).any { it["quote_id"].isNotEmpty() } else rows(g).isNotEmpty()
                    require(!affected || g in selected) { "Replacing quotes affects ${g.key}. Include that dataset in the file and import selection." }
                }
            }
            if(DataGroup.TAGS in selected) require(rows(DataGroup.LINKS).isEmpty() || DataGroup.LINKS in selected) { "Replacing tags also requires the relationships dataset." }
        }
        val quoteRows=(if(mode==ImportMode.REPLACE && DataGroup.QUOTES in selected) emptyList() else rows(DataGroup.QUOTES)).toMutableList()
        val tagRows=(if(mode==ImportMode.REPLACE && DataGroup.TAGS in selected) emptyList() else rows(DataGroup.TAGS)).toMutableList()
        val quoteMap=mutableMapOf<Int,Int>();val tagMap=mutableMapOf<Int,Int>()
        var nextQuote=before.nextQuoteId.toLong();var nextTag=before.nextTagId.toLong()
        var addedQuotes=0;var mergedQuotes=0;var addedTags=0;var mergedTags=0
        fun allocate(value:Long):Int { require(value in 1..Int.MAX_VALUE.toLong()) { "Database ID capacity exceeded." };return value.toInt() }
        if(DataGroup.TAGS in selected) input.rows(DataGroup.TAGS).forEach { record ->
            val name=record["name"].trim()
            val matches=if(mode==ImportMode.MERGE) tagRows.filter { normalizedEntry(it["name"])==normalizedEntry(name) } else emptyList()
            require(matches.size<=1) { "Ambiguous existing tag: $name. Resolve duplicate names before importing." }
            val id=if(matches.isNotEmpty()) { mergedTags++;matches.single().number("id") } else {
                require(record["name"].length<=settings.tagLimit || (fullRestore && record["name"].length<=1000)) { "Imported tag exceeds limit ${settings.tagLimit}: $name" }
                val id=allocate(nextTag++);tagRows+=BackupRecord("tag",mapOf("id" to id.toString(),"name" to record["name"]));addedTags++;id
            }
            tagMap[record.number("id")]=id
        }
        if(DataGroup.QUOTES in selected) input.rows(DataGroup.QUOTES).forEach { record ->
            val text=record["text"]
            val matches=if(mode==ImportMode.MERGE) quoteRows.filter { normalizedEntry(it["text"])==normalizedEntry(text) } else emptyList()
            require(matches.size<=1) { "Ambiguous existing quote. Resolve duplicates before importing." }
            val id=if(matches.isNotEmpty()) { mergedQuotes++;matches.single().number("id") } else {
                require(text.length<=settings.quoteLimit || (fullRestore && text.length<=100000)) { "Imported quote exceeds limit ${settings.quoteLimit}. Increase the limit or import compatible settings." }
                val id=allocate(nextQuote++);quoteRows+=BackupRecord("quote",mapOf("id" to id.toString(),"text" to text));addedQuotes++;id
            }
            quoteMap[record.number("id")]=id
        }
        fun resolve(sourceId:Int,group:DataGroup):Int {
            val mapping=if(group==DataGroup.QUOTES) quoteMap else tagMap
            mapping[sourceId]?.let { return it }
            val field=if(group==DataGroup.QUOTES) "text" else "name"
            val source=input.rows(group).singleOrNull { it.number("id")==sourceId } ?: error("Missing ${group.key} dependency in CSV.")
            val destination=if(group==DataGroup.QUOTES) quoteRows else tagRows
            val matches=destination.filter { normalizedEntry(it[field])==normalizedEntry(source[field]) }
            require(matches.size==1) { "Cannot resolve ${group.key} dependency by content. Include that dataset in the import." }
            return matches.single().number("id").also { mapping[sourceId]=it }
        }
        var links=(if(DataGroup.LINKS in selected && mode==ImportMode.REPLACE) emptyList() else rows(DataGroup.LINKS))
        if(DataGroup.LINKS in selected) {
            links=(links+input.rows(DataGroup.LINKS).map { record -> BackupRecord("quote_tag",mapOf(
                "quote_id" to resolve(record.number("quote_id"),DataGroup.QUOTES).toString(),
                "tag_id" to resolve(record.number("tag_id"),DataGroup.TAGS).toString())) }).distinctBy { it["quote_id"] to it["tag_id"] }
        }
        var history=if(DataGroup.HISTORY in selected) input.rows(DataGroup.HISTORY).map { record ->
            record.copy(fields=record.fields+("quote_id" to resolve(record.number("quote_id"),DataGroup.QUOTES).toString()))
        } else rows(DataGroup.HISTORY)
        val validIds=quoteRows.map { it.number("id") }.toSet()
        val validTagIds=tagRows.map { it.number("id") }.toSet()
        require(links.all { it.number("quote_id") in validIds && it.number("tag_id") in validTagIds }) { "Import would leave broken tag links." }
        require(history.all { it.number("quote_id") in validIds }) { "Import would leave broken history." }
        val limit=effectiveHistoryLimit(settings.historyLimit,quoteRows.size)
        history=history.sortedByDescending { it.number("id") }.distinctBy { it.number("quote_id") }.take(limit).reversed()
        var daily=if(DataGroup.DAILY in selected) input.rows(DataGroup.DAILY).map { record ->
            record.copy(fields=record.fields+("quote_id" to if(record["quote_id"].isBlank()) "" else resolve(record.number("quote_id"),DataGroup.QUOTES).toString()))
        } else rows(DataGroup.DAILY)
        val period=dailyPeriod(now,minutesOfDay=settings.dailyMinutes)
        if(DataGroup.DAILY !in selected && settings.dailyMinutes!=before.settings.dailyMinutes) {
            daily=daily.map { it.copy(fields=it.fields+("local_date" to period.key)) }
        }
        if(DataGroup.DAILY in selected && (daily.isEmpty() || daily.single()["local_date"]!=period.key || (input.sourceZone!=null && input.sourceZone!=java.util.TimeZone.getDefault().id))) {
            require(quoteRows.isEmpty() || DataGroup.HISTORY in selected) { "Restoring an expired daily quote also requires history to be selected." }
            val candidates=eligibleQuoteIds(validIds.toList(),history.map { it.number("quote_id") })
            val chosen=candidates.randomOrNull()
            daily=listOf(BackupRecord("daily_quote",mapOf("id" to "1","quote_id" to (chosen?.toString() ?: ""),"local_date" to period.key)))
            if(chosen!=null && limit>0) {
                val maxId=(history.maxOfOrNull { it.number("id") } ?: 0).toLong()+1
                require(maxId<=Int.MAX_VALUE)
                history=(history.filterNot { it.number("quote_id")==chosen }+BackupRecord("quote_history",mapOf("id" to maxId.toString(),"quote_id" to chosen.toString(),"inserted_at" to now.toString()))).takeLast(limit)
            }
            messages+="Expired daily state recalculated for the current local period."
        }
        require(daily.all { it["quote_id"].isEmpty() || it.number("quote_id") in validIds }) { "Import would leave an invalid daily quote." }
        val after=BackupBundle(DataGroup.entries.toSet(),quoteRows+tagRows+links+history+daily+settings.values().map { BackupRecord("setting",mapOf("key" to it.key,"value_type" to it.type,"value" to it.value)) })
        if(fullRestore && (quoteRows.any { it["text"].length>settings.quoteLimit } || tagRows.any { it["name"].length>settings.tagLimit }))
            messages+="Full restore preserves saved entries above the chosen limits; new or changed text must obey those limits."
        messages.add(0,"Quotes: $addedQuotes added, $mergedQuotes merged. Tags: $addedTags added, $mergedTags merged.")
        selected.forEach { group -> messages+="${group.key}: ${input.rows(group).size} input record(s); ${after.rows(group).size} after import." }
        if(mode==ImportMode.REPLACE) messages+="REPLACE removes existing records only in the selected business datasets."
        if(DataGroup.SETTINGS in selected) messages+="Settings: replace ${known.size} selected key(s); absent keys unchanged."
        if(history.size<rows(DataGroup.HISTORY).size && DataGroup.HISTORY !in selected) messages+="History trimmed to the effective configured limit: $limit."
        return ImportPlan(before,after,selected,mode,messages.joinToString("\n"))
    }
}
