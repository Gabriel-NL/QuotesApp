package com.nog.quotesapp

import androidx.lifecycle.viewModelScope
import com.nog.quotesapp.backup.*
import com.nog.quotesapp.model.*
import com.nog.quotesapp.platform.DocumentFiles
import com.nog.quotesapp.platform.BackupMoveResult
import com.nog.quotesapp.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private lateinit var model:SettingsViewModel
    private val store=FakeSettings();private val files=FakeFiles();private val backups=FakeBackups()
    @Before fun setup() { Dispatchers.setMain(dispatcher);model=SettingsViewModel(store,backups,files,{},dispatcher) }
    @After fun cleanup() { model.viewModelScope.cancel();Dispatchers.resetMain() }
    @Test fun invalidDraftDoesNotSaveAndValidDraftPersists()=runTest(dispatcher) {
        runCurrent();model.changeDraft(SettingsDraft(time="25:00"));model.save();runCurrent()
        assertEquals(0,store.saves);assertNotNull(model.state.value.message)
        model.changeDraft(SettingsDraft(time="08:30",history="0"));model.save();runCurrent()
        assertEquals(510,store.current().dailyMinutes);assertEquals(0,store.current().historyLimit)
        assertFalse(model.state.value.dirty)
    }
    @Test fun folderDenialAndPickerCancellationLeaveSettingsAlone()=runTest(dispatcher) {
        runCurrent();model.selectFolder(null);runCurrent();assertEquals(0,store.saves)
        files.deny=true;model.selectFolder("content://folder");runCurrent()
        assertEquals("",store.current().backupFolder);assertEquals(0,store.saves)
    }
    @Test fun loadingAndPreviewingCsvNeverImportsUntilConfirmed()=runTest(dispatcher) {
        runCurrent();model.loadImport("content://file");advanceUntilIdle()
        assertEquals(0,backups.applied)
        model.prepareImport();advanceUntilIdle()
        assertNotNull(model.state.value.importPreview);assertEquals(0,backups.applied)
        model.applyImport();advanceUntilIdle()
        assertEquals(1,backups.applied);assertNull(model.state.value.importCounts)
    }
    @Test fun selectingNewFolderMovesBackupsBeforeSavingDestination()=runTest(dispatcher) {
        store.value.value=AppSettings(backupFolder="old")
        runCurrent();model.selectFolder("new");advanceUntilIdle()
        assertEquals("old" to "new",files.transfer)
        assertEquals("new",store.current().backupFolder)
        assertTrue(model.state.value.message!!.contains("1 backup(s) moved"))
    }
    @Test fun transferListingFailureLeavesPreviousFolderConfigured()=runTest(dispatcher) {
        store.value.value=AppSettings(backupFolder="old");files.failTransfer=true
        runCurrent();model.selectFolder("new");advanceUntilIdle()
        assertEquals("old",store.current().backupFolder)
        assertEquals(0,store.saves)
    }
    @Test fun malformedNewFileCannotApplyAnOldPreview()=runTest(dispatcher) {
        runCurrent();model.loadImport("content://first");advanceUntilIdle();model.prepareImport();advanceUntilIdle()
        files.content="bad file";model.loadImport("content://second");advanceUntilIdle()
        assertNull(model.state.value.importPreview);assertNull(model.state.value.importCounts)
        model.applyImport();advanceUntilIdle();assertEquals(0,backups.applied)
    }
    @Test fun canceledExportDoesNotWriteOrReportSuccess()=runTest(dispatcher) {
        runCurrent();model.prepareExport();advanceUntilIdle();model.hideExportPreview();model.exportToUri(null)
        assertEquals(0,files.writes);assertEquals("Export canceled.",model.state.value.message)
    }
    private class FakeSettings:SettingsStore {
        val value=MutableStateFlow(AppSettings());var saves=0
        override fun observe()=value
        override suspend fun current()=value.value
        override suspend fun save(settings:AppSettings) {saves++;value.value=settings}
        override suspend fun clearHistory()=Unit
    }
    private class FakeFiles:DocumentFiles {
        var transfer:Pair<String,String>?=null;var failTransfer=false
        override suspend fun moveBackups(oldFolder:String,newFolder:String):BackupMoveResult {
            check(!failTransfer) { "Cannot list previous folder." }
            transfer=oldFolder to newFolder
            return BackupMoveResult(moved=1)
        }
        var deny=false;var writes=0
        var content=BackupCsv.encode(BackupBundle(setOf(DataGroup.QUOTES),listOf(BackupRecord("quote",mapOf("id" to "1","text" to "Test")))))
        override suspend fun read(uri:String)=content
        override suspend fun write(uri:String,text:String) {writes++}
        override suspend fun writeInFolder(folder:String,name:String,text:String):String {writes++;return "content://export"}
        override suspend fun persistFolder(uri:String) {check(!deny) {"Access denied"}}
        override fun canUseFolder(uri:String)=!deny
    }
    private class FakeBackups:BackupStore {
        var applied=0
        override suspend fun export(groups:Set<DataGroup>,includeDependencies:Boolean,includeQuoteTags:Boolean)=ExportPreview("csv","summary")
        override suspend fun prepare(input:BackupBundle,groups:Set<DataGroup>,mode:ImportMode,settingKeys:Set<String>,canUseFolder:(String)->Boolean):ImportPlan {
            val defaults=AppSettings().values().map {BackupRecord("setting",mapOf("key" to it.key,"value_type" to it.type,"value" to it.value))}
            return ImportPlanner.plan(BackupSnapshot(BackupBundle(DataGroup.entries.toSet(),defaults),1,1),input,groups,mode,settingKeys,canUseFolder)
        }
        override suspend fun apply(plan:ImportPlan) {applied++}
    }
}
