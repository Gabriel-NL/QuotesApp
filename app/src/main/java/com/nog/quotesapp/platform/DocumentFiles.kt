package com.nog.quotesapp.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.nog.quotesapp.backup.MAX_BACKUP_BYTES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

interface DocumentFiles {
    suspend fun children(folder:String):List<BackupDocument> = error("Folder listing is unavailable.")
    suspend fun delete(uri:String):Boolean = error("Document deletion is unavailable.")
    suspend fun moveBackups(oldFolder:String,newFolder:String):BackupMoveResult = error("Backup transfer is unavailable.")
    suspend fun read(uri:String):String
    suspend fun write(uri:String,text:String)
    suspend fun writeInFolder(folder:String,name:String,text:String):String
    suspend fun persistFolder(uri:String)
    fun folderName(uri:String):String = "Selected folder"
    fun canUseFolder(uri:String):Boolean
}
class AndroidDocumentFiles(context:Context):DocumentFiles {
    private val resolver=context.applicationContext.contentResolver
    private val stagingRoot=java.io.File(context.applicationContext.cacheDir,"backup-transfer")
    override suspend fun moveBackups(oldFolder:String,newFolder:String):BackupMoveResult =
        BackupMover(this,stagingRoot).move(oldFolder,newFolder)
    override suspend fun children(folder:String):List<BackupDocument> = withContext(Dispatchers.IO) {
        check(canUseFolder(folder)) { "Cannot access the previous backup folder. Choose it again before moving backups." }
        val tree=Uri.parse(folder)
        val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE)
        val cursor=checkNotNull(resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),columns,null,null,null)) { "Cannot list backup folder." }
        cursor.use {
            buildList {
                while(it.moveToNext()) add(BackupDocument(
                    DocumentsContract.buildDocumentUriUsingTree(tree,it.getString(0)).toString(),
                    it.getString(1) ?: "backup.csv",
                    it.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR))
            }
        }
    }
    override suspend fun delete(uri:String):Boolean = withContext(Dispatchers.IO) {
        DocumentsContract.deleteDocument(resolver,Uri.parse(uri))
    }
    override fun folderName(uri:String):String=runCatching {
        val tree=Uri.parse(uri)
        resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: "Selected folder"
    }.getOrDefault("Unavailable folder; choose again")
    override fun canUseFolder(uri:String):Boolean=runCatching {
        val target=Uri.parse(uri)
        resolver.persistedUriPermissions.any { it.uri==target && it.isReadPermission && it.isWritePermission } &&
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(target,DocumentsContract.getTreeDocumentId(target)),arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use { it.moveToFirst() && it.getString(0)==DocumentsContract.Document.MIME_TYPE_DIR }==true
    }.getOrDefault(false)
    override suspend fun persistFolder(uri:String)=withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(Uri.parse(uri),Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        check(canUseFolder(uri)) { "Folder access unavailable. Choose another folder." }
    }
    override suspend fun read(uri:String):String=withContext(Dispatchers.IO) {
        val bytes=checkNotNull(resolver.openInputStream(Uri.parse(uri))) { "Cannot open CSV." }.use { input ->
            val buffer=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192);var count=input.read(chunk)
            while(count!=-1) {
                require(buffer.size()+count<=MAX_BACKUP_BYTES) { "CSV exceeds 32 MB." }
                buffer.write(chunk,0,count);count=input.read(chunk)
            };buffer.toByteArray()
        }
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    override suspend fun write(uri:String,text:String)=withContext(Dispatchers.IO) {
        val target=Uri.parse(uri)
        try {
            checkNotNull(resolver.openOutputStream(target,"w")) { "Cannot create export." }.use { output -> output.write(text.toByteArray(Charsets.UTF_8));output.flush() }
        } catch(error:Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver,target) }
            throw error
        }
    }
    override suspend fun writeInFolder(folder:String,name:String,text:String):String=withContext(Dispatchers.IO) {
        check(canUseFolder(folder)) { "Backup folder unavailable. Choose it again or use Save as." }
        val tree=Uri.parse(folder)
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val names=mutableSetOf<String>()
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { cursor -> while(cursor.moveToNext()) names+=cursor.getString(0) }
        var candidate=name;var suffix=1
        while(candidate in names) candidate=name.removeSuffix(".csv")+" (${suffix++}).csv"
        val created=checkNotNull(DocumentsContract.createDocument(resolver,parent,"text/csv",candidate)) { "Cannot create file in folder." }
        write(created.toString(),text)
        created.toString()
    }
}
