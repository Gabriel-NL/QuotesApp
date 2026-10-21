package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import com.nog.quotesapp.data.db.DatabaseProvider
import com.nog.quotesapp.data.entity.TagEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

const val MAX_TAG_NAME_LENGTH = 100

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagsScreen() {
    val context = LocalContext.current.applicationContext
    val db = remember(context) { DatabaseProvider.getDatabase(context) }
    val dao = db.tagDao()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val flow = remember(dao) {
        dao.observeAll().onEach { loading = false }.catch {
            if (it is CancellationException) throw it
            error = "Could not load tags."
            loading = false
        }
    }
    val tags by flow.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var pendingDelete by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var deleting by remember { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var tagName by rememberSaveable { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var editorError by remember { mutableStateOf<String?>(null) }
    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) { delay(200); debouncedQuery = query }
    val visibleTags = rankEntries(tags, debouncedQuery) { it.name }
    BackHandler(enabled = selectionMode && !showEditor && pendingDelete.isEmpty()) {
        selectionMode = false; selected = emptyList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tags", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectionMode) {
                Button(onClick = { selectionMode = false; selected = emptyList() }) { Text("Cancel") }
                Button(onClick = { pendingDelete = selected }, enabled = selected.isNotEmpty()) {
                    Text("Delete selected (${selected.size})")
                }
            } else {
                Button(onClick = {
                    editingId = null
                    tagName = ""
                    editorError = null
                    showEditor = true
                }) { Text("Create a Tag") }
                Button(onClick = { selectionMode = true }, enabled = tags.isNotEmpty()) { Text("Delete Multiple") }
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Search tags") }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        else if (visibleTags.isEmpty()) Text(if (query.isBlank()) "No tags yet." else "No matching tags.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleTags, key = { it.id }) { tag ->
                Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = {
                    if (!selectionMode) {
                        editingId = tag.id; tagName = tag.name; editorError = null; showEditor = true
                    }
                })) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (selectionMode) Checkbox(checked = tag.id in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + tag.id else selected - tag.id
                        })
                        Text(tag.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!selectionMode) {
                            TextButton(onClick = {
                                editingId = tag.id
                                tagName = tag.name
                                editorError = null
                                showEditor = true
                            }) { Text("Edit") }
                            TextButton(onClick = { pendingDelete = listOf(tag.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    if (showEditor) AlertDialog(
        onDismissRequest = { if (!saving) showEditor = false },
        title = { Text(if (editingId == null) "Create a Tag" else "Edit a Tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tagName,
                    onValueChange = { tagName = it.replace("\r", "").replace("\n", " ").take(MAX_TAG_NAME_LENGTH) },
                    label = { Text("Tag name") },
                    singleLine = true,
                    enabled = !saving
                )
                editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = tagName.isNotBlank() && !saving, onClick = {
                val id = editingId
                val name = tagName.trim()
                saving = true
                editorError = null
                scope.launch {
                    try {
                        db.withTransaction {
                            if (id == null) dao.insert(TagEntity(name = name))
                            else {
                                check(dao.exists(id)) { "Tag no longer exists" }
                                dao.updateTagNameUsingId(id, name)
                            }
                        }
                        showEditor = false
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (exception: Exception) { editorError = "Could not save the tag." }
                    finally { saving = false }
                }
            }) { Text(if (saving) "Saving..." else if (editingId == null) "Create" else "Save") }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = { showEditor = false }) { Text("Cancel") }
        }
    )
    if (pendingDelete.isNotEmpty()) AlertDialog(
        onDismissRequest = { if (!deleting) pendingDelete = emptyList() },
        title = { Text("Delete ${pendingDelete.size} tag(s)?") },
        text = { Column { Text("Their links to quotes will also be removed. The quotes themselves will be kept."); error?.let { Text(it) } } },
        confirmButton = {
            TextButton(enabled = !deleting, onClick = {
                val ids = pendingDelete.toList()
                deleting = true
                scope.launch {
                    try {
                        db.withTransaction { ids.forEach { dao.deleteById(it) } }
                        selected = selected - ids.toSet()
                        pendingDelete = emptyList()
                        selectionMode = false
                        selected = emptyList()
                        error = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (exception: Exception) { error = "Could not delete tags." }
                    finally { deleting = false }
                }
            }) { Text(if (deleting) "Deleting..." else "Delete") }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = emptyList() }) { Text("Cancel") } }
    )
}
