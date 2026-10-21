package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.nog.quotesapp.data.db.DatabaseProvider
import com.nog.quotesapp.data.entity.QuoteEntity
import com.nog.quotesapp.data.repository.QuoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun QuoteEditorScreen(quoteId: Int? = null, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val db = remember(context) { DatabaseProvider.getDatabase(context) }
    val repository = remember(db) { QuoteRepository(db) }
    val scope = rememberCoroutineScope()
    var loaded by remember(quoteId) { mutableStateOf(false) }
    var quote by remember(quoteId) { mutableStateOf<QuoteEntity?>(null) }
    var initialTags by remember(quoteId) { mutableStateOf(emptyList<Int>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(quoteId) {
        try {
            if (quoteId != null) db.withTransaction {
                quote = db.quoteDao().getById(quoteId)
                initialTags = db.quoteTagCrossRefDao().getAll().filter { it.quoteId == quoteId }.map { it.tagId }
                check(quote != null) { "Quote no longer exists" }
            }
            loaded = true
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (exception: Exception) { error = "Could not load this quote. It may have been deleted." }
    }
    if (!loaded) {
        Column(Modifier.padding(16.dp)) {
            if (error == null) CircularProgressIndicator() else Text(error!!)
            TextButton(onClick = onBack) { Text("Back") }
        }
    } else CreateQuoteScreen(
        initialText = quote?.text ?: "", initialTagIds = initialTags,
        isEditing = quoteId != null, saving = saving, error = error, onBack = onBack,
        onCreate = { text, tags ->
            if (!saving) {
                saving = true; error = null
                scope.launch {
                    try {
                        if (quoteId == null) repository.createQuote(text, tags)
                        else repository.updateQuote(quoteId, text, tags)
                        onBack()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (exception: Exception) { error = "Could not save. Check whether a selected tag was deleted and try again." }
                    finally { saving = false }
                }
            }
        }
    )
}
