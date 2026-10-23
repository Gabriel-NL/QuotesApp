package com.nog.quotesapp

import com.nog.quotesapp.backup.*
import com.nog.quotesapp.platform.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupMoverTest {
    @get:Rule val temp=TemporaryFolder()
    private val backup=BackupCsv.encode(BackupBundle(setOf(DataGroup.QUOTES),
        listOf(BackupRecord("quote",mapOf("id" to "1","text" to "Ol\u00e1; \"mundo\"\nOutra linha")))))
    private class Files:DocumentFiles {
        val contents=mutableMapOf<String,String>()
        var entries=listOf<BackupDocument>()
        val deleted=mutableListOf<String>()
        val reads=mutableListOf<String>()
        var corrupt=false;var changeOriginal=false;var refuseDelete=false;var writeFailure=false
        override suspend fun children(folder:String)=entries
        override suspend fun read(uri:String):String { reads+=uri;return contents.getValue(uri) }
        override suspend fun write(uri:String,text:String) { contents[uri]=text }
        override suspend fun writeInFolder(folder:String,name:String,text:String):String {
            check(!writeFailure)
            var target="$folder/$name";var n=1
            while(target in contents) target="$folder/${n++}-$name"
            contents[target]=if(corrupt) "corrupt" else text
            if(changeOriginal) contents["old/backup.csv"]="changed"
            return target
        }
        override suspend fun delete(uri:String):Boolean {
            if(refuseDelete) return false
            deleted+=uri;contents.remove(uri);return true
        }
        override suspend fun persistFolder(uri:String)=Unit
        override fun canUseFolder(uri:String)=true
    }
    private fun files()=Files().apply {
        entries=listOf(BackupDocument("old/backup.csv","backup.csv",false))
        contents["old/backup.csv"]=backup
    }
    @Test fun validBackupMovesAndTemporaryCopyIsRemoved()=runBlocking {
        val files=files();val stage=temp.newFolder()
        assertEquals(BackupMoveResult(moved=1),BackupMover(files,stage).move("old","new"))
        assertEquals(backup,files.contents["new/backup.csv"])
        assertFalse(files.contents.containsKey("old/backup.csv"))
        assertTrue(stage.listFiles()!!.isEmpty())
    }
    @Test fun invalidCsvAndDirectoriesAreUntouchedEvenWithCsvNames()=runBlocking {
        val files=files().apply {
            entries=entries+BackupDocument("old/folder","folder",true)+BackupDocument("old/fake.csv","fake.csv",false)
            contents["old/fake.csv"]="not an app backup"
        }
        val result=BackupMover(files,temp.newFolder()).move("old","new")
        assertEquals(BackupMoveResult(moved=1,skipped=2),result)
        assertFalse("old/folder" in files.reads)
        assertEquals("not an app backup",files.contents["old/fake.csv"])
    }
    @Test fun destinationCollisionPreservesExistingFile()=runBlocking {
        val files=files().apply { contents["new/backup.csv"]="existing" }
        BackupMover(files,temp.newFolder()).move("old","new")
        assertEquals("existing",files.contents["new/backup.csv"])
        assertEquals(backup,files.contents["new/1-backup.csv"])
    }
    @Test fun corruptDestinationNeverDeletesOriginal()=runBlocking {
        val files=files().apply { corrupt=true }
        assertEquals(1,BackupMover(files,temp.newFolder()).move("old","new").retained)
        assertEquals(backup,files.contents["old/backup.csv"]);assertTrue(files.deleted.isEmpty())
    }
    @Test fun originalChangedDuringCopyIsNotDeleted()=runBlocking {
        val files=files().apply { changeOriginal=true }
        assertEquals(1,BackupMover(files,temp.newFolder()).move("old","new").retained)
        assertEquals("changed",files.contents["old/backup.csv"]);assertTrue(files.deleted.isEmpty())
    }
    @Test fun writeFailureKeepsOriginalAndCleansStaging()=runBlocking {
        val files=files().apply { writeFailure=true };val stage=temp.newFolder()
        assertEquals(1,BackupMover(files,stage).move("old","new").retained)
        assertEquals(backup,files.contents["old/backup.csv"]);assertTrue(files.deleted.isEmpty())
        assertTrue(stage.listFiles()!!.isEmpty())
    }
    @Test fun failedDeletionKeepsBothCopiesAndReportsIncompleteMove()=runBlocking {
        val files=files().apply { refuseDelete=true }
        assertEquals(BackupMoveResult(retained=1),BackupMover(files,temp.newFolder()).move("old","new"))
        assertEquals(backup,files.contents["old/backup.csv"])
        assertEquals(backup,files.contents["new/backup.csv"])
    }
    @Test fun selectingSameFolderDoesNotReadOrDelete()=runBlocking {
        val files=files()
        assertEquals(BackupMoveResult(),BackupMover(files,temp.newFolder()).move("old","old"))
        assertTrue(files.reads.isEmpty());assertTrue(files.deleted.isEmpty())
    }
}
