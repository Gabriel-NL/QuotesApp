package com.nog.quotesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.backup.*
import com.nog.quotesapp.model.*
import com.nog.quotesapp.platform.DocumentFiles
import com.nog.quotesapp.data.repository.TagStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SettingsDraft(val time:String="00:00",val history:String="5",val quoteLimit:String="5000",val tagLimit:String="100") {
    fun applyTo(base:AppSettings):AppSettings {
        require(time.matches(Regex("[0-2][0-9]:[0-5][0-9]"))) { "Use HH:mm, for example 08:30." }
        return base.copy(dailyMinutes=time.take(2).toInt()*60+time.takeLast(2).toInt(),
            historyLimit=history.toIntOrNull() ?: error("Invalid history size."),
            quoteLimit=quoteLimit.toIntOrNull() ?: error("Invalid quote limit."),
            tagLimit=tagLimit.toIntOrNull() ?: error("Invalid tag limit.")).validated()
    }
    companion object { fun from(s:AppSettings)=SettingsDraft(String.format(Locale.ROOT,"%02d:%02d",s.dailyMinutes/60,s.dailyMinutes%60),s.historyLimit.toString(),s.quoteLimit.toString(),s.tagLimit.toString()) }
}
data class SettingsUiState(
    val availableTags:List<Tag> = emptyList(),
    val settings:AppSettings=AppSettings(),val draft:SettingsDraft=SettingsDraft(),val dirty:Boolean=false,
    val folderName:String="",val loading:Boolean=true,val busy:Boolean=false,val message:String?=null,val preciseAlarms:Boolean=true,
    val exportGroups:Set<DataGroup> = DataGroup.entries.toSet(),val includeDependencies:Boolean=true,val includeQuoteTags:Boolean=false,
    val exportPreview:ExportPreview?=null,
    val importCounts:Map<DataGroup,Int>?=null,val importGroups:Set<DataGroup> = emptySet(),
    val importedKeys:Set<String> = emptySet(),val selectedKeys:Set<String> = emptySet(),
    val importMode:ImportMode=ImportMode.MERGE,val importPreview:String?=null
)
class SettingsViewModel(
    private val settings:SettingsStore,private val backups:BackupStore,
    private val files:DocumentFiles,private val onChanged:()->Unit,
    private val parsingDispatcher:CoroutineDispatcher=Dispatchers.Default,
    tags:TagStore?=null
):ViewModel() {
    private val mutableState=MutableStateFlow(SettingsUiState())
    val state=mutableState.asStateFlow()
    private var imported:BackupBundle?=null
    private var plan:ImportPlan?=null
    private var export:ExportPreview?=null
    init {
        if(tags!=null) viewModelScope.launch {
            tags.observeTags().catch { error ->
                if(error is CancellationException) throw error
                mutableState.update { it.copy(message="Could not load filter tags.") }
            }.collect { rows -> mutableState.update { it.copy(availableTags=rows) } }
        }
        viewModelScope.launch {
            settings.observe().catch { error ->
                if(error is CancellationException) throw error
                mutableState.update { it.copy(loading=false,message="Could not load settings.") }
            }.collect { value ->
                val name=if(value.backupFolder.isEmpty()) "" else withContext(Dispatchers.IO) { files.folderName(value.backupFolder) }
                mutableState.update { it.copy(settings=value,folderName=name,loading=false,draft=if(it.dirty) it.draft else SettingsDraft.from(value)) }
            }
        }
    }
    private fun task(block:suspend ()->Unit) {
        if(state.value.busy) return
        mutableState.update { it.copy(busy=true,message=null) }
        viewModelScope.launch {
            try { block() }
            catch(error:CancellationException) { throw error }
            catch(error:Exception) { mutableState.update { it.copy(message=error.message ?: "Operation failed. Nothing was imported.") } }
            finally { mutableState.update { it.copy(busy=false) } }
        }
    }
    fun changeDraft(draft:SettingsDraft) { if(!state.value.busy) mutableState.update { it.copy(draft=draft,dirty=true) } }
    fun setPreciseAlarms(allowed:Boolean) { mutableState.update { it.copy(preciseAlarms=allowed) } }
    fun save()=task {
        val value=state.value.draft.applyTo(settings.current())
        settings.save(value)
        mutableState.update { it.copy(settings=value,draft=SettingsDraft.from(value),dirty=false,message="Settings saved.") }
        runCatching(onChanged)
    }
    fun reset()=task {
        settings.save(AppSettings())
        mutableState.update { it.copy(settings=AppSettings(),draft=SettingsDraft(),dirty=false,message="Defaults restored. Quotes and tags were kept.") }
        runCatching(onChanged)
    }
    fun clearHistory()=task { settings.clearHistory();mutableState.update { it.copy(message="Recent history cleared. Current daily quote kept.") } }
    fun toggleDailyTag(name:String)=task {
        val current=settings.current()
        val existing=current.dailyTagNames.firstOrNull { tagFilterKey(it)==tagFilterKey(name) }
        settings.save(current.copy(dailyTagNames=if(existing==null) current.dailyTagNames+name else current.dailyTagNames-existing))
        runCatching(onChanged)
    }
    fun dailyWhitelist(value:Boolean)=task {
        settings.save(settings.current().copy(dailyWhitelist=value));runCatching(onChanged)
    }
    fun clearDailyTags()=task {
        settings.save(settings.current().copy(dailyTagNames=emptySet()));runCatching(onChanged)
    }
    fun selectFolder(uri:String?) {
        if(uri==null) return
        task {
            val previous=settings.current().backupFolder
            files.persistFolder(uri)
            val result=if(previous.isNotEmpty() && previous!=uri) files.moveBackups(previous,uri) else null
            settings.save(settings.current().copy(backupFolder=uri))
            mutableState.update { it.copy(message=result?.summary() ?: "Backup folder saved.") }
        }
    }
    fun removeFolder()=task { settings.save(settings.current().copy(backupFolder=""));mutableState.update { it.copy(message="Exports will use Save as.") } }
    fun toggleExport(group:DataGroup) { if(!state.value.busy) { export=null;mutableState.update { it.copy(exportGroups=if(group in it.exportGroups) it.exportGroups-group else it.exportGroups+group,exportPreview=null) } } }
    fun toggleAllExport() {
        if(state.value.busy) return
        val select=!(state.value.exportGroups.containsAll(DataGroup.entries) && state.value.includeDependencies && state.value.includeQuoteTags)
        export=null
        mutableState.update { it.copy(exportGroups=if(select) DataGroup.entries.toSet() else emptySet(),
            includeDependencies=select,includeQuoteTags=select,exportPreview=null) }
    }
    fun exportDependencies(value:Boolean) { mutableState.update { it.copy(includeDependencies=value) } }
    fun exportQuoteTags(value:Boolean) { mutableState.update { it.copy(includeQuoteTags=value) } }
    fun prepareExport()=task {
        val s=state.value
        export=backups.export(s.exportGroups,s.includeDependencies,s.includeQuoteTags)
        mutableState.update { it.copy(exportPreview=export) }
    }
    fun dismissExport() { if(!state.value.busy) { export=null;mutableState.update { it.copy(exportPreview=null) } } }
    fun hideExportPreview() { mutableState.update { it.copy(exportPreview=null) } }
    fun fileName()="Random Quotes App "+SimpleDateFormat("yyyy-MM-dd HH-mm-ss",Locale.ROOT).format(Date())+".csv"
    fun exportToFolder()=task {
        val payload=checkNotNull(export) { "Prepare the export again." }
        files.writeInFolder(settings.current().backupFolder,fileName(),payload.csv)
        export=null;mutableState.update { it.copy(exportPreview=null,message="CSV exported successfully.\n${payload.summary}") }
    }
    fun exportToUri(uri:String?) {
        if(uri==null) { export=null;mutableState.update { it.copy(message="Export canceled.") };return }
        task {
            val payload=checkNotNull(export) { "Prepare the export again." }
            files.write(uri,payload.csv)
            export=null;mutableState.update { it.copy(exportPreview=null,message="CSV exported successfully.\n${payload.summary}") }
        }
    }
    fun loadImport(uri:String?) {
        if(uri==null) return
        task {
            imported=null;plan=null
            mutableState.update { it.copy(importCounts=null,importGroups=emptySet(),importPreview=null,importedKeys=emptySet(),selectedKeys=emptySet()) }
            val content=files.read(uri)
            val bundle=withContext(parsingDispatcher) { BackupCsv.decode(content) }
            imported=bundle;plan=null
            val keys=bundle.rows(DataGroup.SETTINGS).map { it["key"] }.toSet()
            val unknown=keys-AppSettings.KEYS
            mutableState.update { it.copy(importCounts=bundle.counts(),importGroups=bundle.groups,
                importedKeys=keys,selectedKeys=keys.intersect(AppSettings.KEYS),importPreview=null,
                message=if(unknown.isEmpty()) "CSV validated. Choose what to import, then preview." else "CSV validated. Unknown settings will not be applied: ${unknown.joinToString()}") }
        }
    }
    fun toggleImport(group:DataGroup) { if(!state.value.busy) { plan=null;mutableState.update { it.copy(importGroups=if(group in it.importGroups) it.importGroups-group else it.importGroups+group,importPreview=null) } } }
    fun toggleSetting(key:String) { if(key !in AppSettings.KEYS || state.value.busy) return;plan=null;mutableState.update { it.copy(selectedKeys=if(key in it.selectedKeys) it.selectedKeys-key else it.selectedKeys+key,importPreview=null) } }
    fun setMode(mode:ImportMode) { plan=null;mutableState.update { it.copy(importMode=mode,importPreview=null) } }
    fun prepareImport()=task {
        val s=state.value
        plan=backups.prepare(checkNotNull(imported),s.importGroups,s.importMode,s.selectedKeys,files::canUseFolder)
        mutableState.update { it.copy(importPreview=plan!!.summary) }
    }
    fun dismissImport() { if(!state.value.busy) { plan=null;mutableState.update { it.copy(importPreview=null) } } }
    fun applyImport()=task {
        val approved=checkNotNull(plan) { "Preview the import again." }
        try { backups.apply(approved) }
        catch(error:Exception) { plan=null;mutableState.update { it.copy(importPreview=null) };throw error }
        plan=null;imported=null
        val current=settings.current()
        val importedSettings=DataGroup.SETTINGS in approved.selected
        mutableState.update { it.copy(importPreview=null,importCounts=null,settings=current,
            draft=if(importedSettings) SettingsDraft.from(current) else it.draft,
            dirty=if(importedSettings) false else it.dirty,message="Import completed.\n${approved.summary}") }
        runCatching(onChanged)
    }
}
