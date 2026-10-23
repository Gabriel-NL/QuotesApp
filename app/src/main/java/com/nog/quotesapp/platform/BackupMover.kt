package com.nog.quotesapp.platform

import com.nog.quotesapp.backup.BackupCsv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class BackupDocument(val uri:String,val name:String,val directory:Boolean)
data class BackupMoveResult(val moved:Int=0,val skipped:Int=0,val retained:Int=0) {
    fun summary()="Backup folder saved. $moved backup(s) moved; $skipped non-backup file(s) or folder(s) skipped; $retained file(s) kept in the old folder because they could not be safely moved."
}

/** Only direct children are considered. Deletion always follows read-back verification. */
class BackupMover(private val files:DocumentFiles,private val stagingRoot:File) {
    suspend fun move(oldFolder:String,newFolder:String):BackupMoveResult = withContext(Dispatchers.IO) {
        if(oldFolder==newFolder) return@withContext BackupMoveResult()
        check(files.canUseFolder(oldFolder) && files.canUseFolder(newFolder)) { "Access to both backup folders is required. No files moved." }
        val children=files.children(oldFolder)
        var moved=0;var skipped=0;var retained=0
        for(document in children) {
            if(document.directory) { skipped++;continue }
            val text=try { files.read(document.uri) }
                catch(cancelled:CancellationException) { throw cancelled }
                catch(error:Exception) { retained++;continue }
            try { BackupCsv.decode(text) }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { skipped++;continue }
            var staging:File?=null
            try {
                check(stagingRoot.isDirectory || stagingRoot.mkdirs()) { "Cannot stage backup." }
                staging=File.createTempFile("backup-", ".csv", stagingRoot)
                staging.writeText(text,Charsets.UTF_8)
                val staged=staging.readText(Charsets.UTF_8)
                check(staged==text) { "Temporary copy verification failed." }
                val target=files.writeInFolder(newFolder,document.name,staged)
                // A provider may expose the same underlying folder through a different URI.
                check(target!=document.uri) { "Source and destination are the same document." }
                check(files.read(target)==staged) { "Destination verification failed." }
                check(files.read(document.uri)==staged) { "Original changed during transfer." }
                check(files.delete(document.uri)) { "Original could not be removed." }
                moved++
            } catch(cancelled:CancellationException) { throw cancelled }
            catch(error:Exception) { retained++ }
            finally { staging?.delete() }
        }
        BackupMoveResult(moved,skipped,retained)
    }
}
