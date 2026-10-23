package com.nog.quotesapp

import com.nog.quotesapp.backup.*
import com.nog.quotesapp.model.*
import com.nog.quotesapp.viewmodel.SettingsDraft
import org.junit.Test
import org.junit.Assert.*

class BackupCsvTest {
    private fun q(id:Int,text:String)=BackupRecord("quote",mapOf("id" to "$id","text" to text))
    private fun t(id:Int,name:String)=BackupRecord("tag",mapOf("id" to "$id","name" to name))
    private fun link(q:Int,t:Int)=BackupRecord("quote_tag",mapOf("quote_id" to "$q","tag_id" to "$t"))
    private fun settings(s:AppSettings)=s.values().map { BackupRecord("setting",mapOf("key" to it.key,"value_type" to it.type,"value" to it.value)) }
    private fun snap(records:List<BackupRecord> = emptyList(),s:AppSettings=AppSettings())=BackupSnapshot(BackupBundle(DataGroup.entries.toSet(),records+settings(s)),100,200)
    private fun plan(before:BackupSnapshot,input:BackupBundle,groups:Set<DataGroup> = input.groups,mode:ImportMode=ImportMode.MERGE,keys:Set<String> = AppSettings.KEYS)=ImportPlanner.plan(before,input,groups,mode,keys,{false})
    private fun fails(block:()->Unit) { var failed=false;try {block()} catch(error:Exception) {failed=true};assertTrue("Operation should fail",failed) }
    @Test fun roundTripPreservesDelimiterQuotesNewlinesAndAccents() {
        val original=BackupBundle(setOf(DataGroup.QUOTES,DataGroup.TAGS,DataGroup.LINKS),listOf(q(7,"Ol\u00e1; \"mundo\"\r\nLinha\nSeguinte"),t(4,"a;b"),link(7,4)))
        val decoded=BackupCsv.decode(BackupCsv.encode(original))
        assertEquals(original.groups,decoded.groups)
        assertEquals(original.records.map { it.type to it.fields.filterValues { v -> v.isNotEmpty() } },decoded.records.map { it.type to it.fields.filterValues { v -> v.isNotEmpty() } })
    }
    @Test fun emptyPresentDatasetIsDifferentFromAbsent() {
        val decoded=BackupCsv.decode(BackupCsv.encode(BackupBundle(setOf(DataGroup.TAGS),emptyList())))
        assertTrue(DataGroup.TAGS in decoded.groups);assertFalse(DataGroup.QUOTES in decoded.groups)
        assertEquals(0,decoded.rows(DataGroup.TAGS).size)
    }
    @Test fun rejectsMissingReferencesAndDuplicateIds() {
        fails { BackupCsv.decode(BackupCsv.encode(BackupBundle(setOf(DataGroup.LINKS),listOf(link(1,2))))) }
        fails { BackupCsv.decode(BackupCsv.encode(BackupBundle(setOf(DataGroup.QUOTES),listOf(q(1,"a"),q(1,"b"))))) }
    }
    @Test fun rejectsMalformedQuoteHeaderAndManifestCount() {
        fails { BackupCsv.decode("wrong;header") }
        val csv=BackupCsv.encode(BackupBundle(setOf(DataGroup.QUOTES),listOf(q(1,"A"))))
        fails { BackupCsv.decode(csv+"quote;2;;;\"unclosed") }
        fails { BackupCsv.decode(csv.replace("quotes;integer;1","quotes;integer;9")) }
    }
    @Test fun rejectsUnsupportedVersionAndAcceptsBom() {
        val csv=BackupCsv.encode(BackupBundle(setOf(DataGroup.QUOTES),listOf(q(1,"A"))))
        fails { BackupCsv.decode(csv.replace("format_version;integer;1","format_version;integer;9")) }
        assertEquals(1,BackupCsv.decode("\uFEFF"+csv).records.size)
    }
    @Test fun mergeRemapsIdsAndUnitesLinks() {
        val before=snap(listOf(q(1," Alpha "),t(2,"Business")))
        val input=BackupBundle(setOf(DataGroup.QUOTES,DataGroup.TAGS,DataGroup.LINKS),listOf(q(99,"alpha"),q(1,"Beta"),t(1,"business"),link(99,1),link(1,1)))
        val after=plan(before,input).after
        assertEquals(2,after.rows(DataGroup.QUOTES).size)
        assertEquals(1,after.rows(DataGroup.TAGS).size)
        assertEquals(setOf("1","100"),after.rows(DataGroup.LINKS).map { it["quote_id"] }.toSet())
        assertTrue(after.rows(DataGroup.LINKS).all { it["tag_id"]=="2" })
    }
    @Test fun ambiguousTagsAndQuoteReplacementDependenciesAreRejected() {
        val before=snap(listOf(q(1,"A"),t(2,"Tag"),t(3,"TAG"),link(1,2)))
        fails { plan(before,BackupBundle(setOf(DataGroup.TAGS),listOf(t(4,"tag")))) }
        fails { plan(before,BackupBundle(setOf(DataGroup.QUOTES),listOf(q(1,"B"))),mode=ImportMode.REPLACE) }
    }
    @Test fun dependencyResolutionUsesContentNotCoincidentIds() {
        val before=snap(listOf(q(1,"Unrelated"),t(2,"Existing")))
        val input=BackupBundle(setOf(DataGroup.QUOTES,DataGroup.TAGS,DataGroup.LINKS),listOf(q(1,"Different"),t(2,"Existing"),link(1,2)))
        fails { plan(before,input,setOf(DataGroup.LINKS)) }
    }
    @Test fun settingsOnlyReplacesSelectedKeysAndIgnoresUnavailableFolder() {
        val input=BackupBundle(setOf(DataGroup.SETTINGS),settings(AppSettings(historyLimit=2,quoteLimit=80,backupFolder="content://unavailable")))
        val after=plan(snap(),input,keys=setOf("history_limit","backup_folder")).after
        val result=AppSettings.from(after.rows(DataGroup.SETTINGS).map { SettingValue(it["key"],it["value_type"],it["value"]) })
        assertEquals(2,result.historyLimit);assertEquals(5000,result.quoteLimit);assertEquals("",result.backupFolder)
    }
    @Test fun incomingLimitsApplyBeforeDataValidation() {
        val input=BackupBundle(setOf(DataGroup.QUOTES,DataGroup.SETTINGS),listOf(q(1,"Long text"))+settings(AppSettings(quoteLimit=3)))
        fails { plan(snap(),input) }
    }
    @Test fun unknownSettingsReportedAndExistingLongEntriesRemain() {
        val unknown=BackupRecord("setting",mapOf("key" to "future_option","value_type" to "boolean","value" to "true"))
        val input=BackupBundle(setOf(DataGroup.SETTINGS),settings(AppSettings(quoteLimit=1))+unknown)
        val result=plan(snap(listOf(q(1,"Existing long quote"))),input)
        assertEquals("Existing long quote",result.after.rows(DataGroup.QUOTES).single()["text"])
        assertTrue(result.summary.contains("Unknown setting"))
    }
    @Test fun numericDuplicateIdsAreRejectedEvenWithLeadingZeroes() {
        val duplicate=q(1,"A").copy(fields=mapOf("id" to "01","text" to "B"))
        fails {BackupCsv.decode(BackupCsv.encode(BackupBundle(setOf(DataGroup.QUOTES),listOf(q(1,"A"),duplicate))))}
    }
    @Test fun completeRestorePreservesGrandfatheredLongRecords() {
        val input=BackupBundle(DataGroup.entries.toSet(),listOf(q(1,"Saved long quote"))+settings(AppSettings(quoteLimit=1)))
        val restored=plan(snap(),input,mode=ImportMode.REPLACE)
        assertEquals("Saved long quote",restored.after.rows(DataGroup.QUOTES).single()["text"])
        assertTrue(restored.summary.contains("above the chosen limits"))
    }
    @Test fun settingsTimeValidationAndZeroHistory() {
        fails { SettingsDraft(time="29:00").applyTo(AppSettings()) }
        fails { AppSettings(quoteLimit=0).validated() }
        assertEquals(0,SettingsDraft(history="0").applyTo(AppSettings()).historyLimit)
    }
}
