package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.nog.quotesapp.model.EntryOrder
import com.nog.quotesapp.ui.components.OrderControls
import com.nog.quotesapp.model.rankEntries
import com.nog.quotesapp.model.matchesTags
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.nog.quotesapp.viewmodel.TagsUiState


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagsScreen(
    state: TagsUiState,
    onSave: (Int?, String) -> Unit,
    onDelete: (List<Int>) -> Unit,
    onClearEditorError: () -> Unit
) {
    val tags = state.tags
    val loading = state.loading
    val error = state.error
    val editorError = state.editorError
    val saving = state.saving
    val deleting = state.deleting
    var query by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(EntryOrder.ALPHABETIC) }
    var descending by rememberSaveable { mutableStateOf(false) }
    val listState=rememberLazyListState()
    LaunchedEffect(query,order,descending) { listState.scrollToItem(0) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var pendingDelete by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Int?>(null) }
    var tagName by rememberSaveable { mutableStateOf("") }
    var handledSave by rememberSaveable { mutableStateOf(state.savedVersion) }
    var handledDeletion by rememberSaveable { mutableStateOf(state.deletedVersion) }
    LaunchedEffect(state.savedVersion) {
        if (state.savedVersion != handledSave) {
            showEditor = false
            handledSave = state.savedVersion
        }
    }
    LaunchedEffect(state.deletedVersion) {
        if (state.deletedVersion != handledDeletion) {
            pendingDelete = emptyList(); selectionMode = false; selected = emptyList()
            handledDeletion = state.deletedVersion
        }
    }
    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) { delay(200); debouncedQuery = query }
    val visibleTags = rankEntries(tags, debouncedQuery, order, descending) { it.name }
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
                    onClearEditorError()
                    showEditor = true
                }) { Text("Create a Tag") }
                Button(onClick = { selectionMode = true }, enabled = tags.isNotEmpty()) { Text("Delete Multiple") }
            }
        }
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Search tags") }, modifier = Modifier.fillMaxWidth())
        OrderControls(order,descending,{order=it},{descending=it})
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        else if (visibleTags.isEmpty()) Text(if (query.isBlank()) "No tags yet." else "No matching tags.")
        LazyColumn(Modifier.weight(1f), state=listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleTags, key = { it.id }) { tag ->
                Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = {
                    if (!selectionMode) {
                        editingId = tag.id; tagName = tag.name; onClearEditorError(); showEditor = true
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
                                onClearEditorError()
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
                    onValueChange = { tagName = it.replace("\r", "").replace("\n", " ") },
                    label = { Text("Tag name") },
                    supportingText = { Text("${tagName.length}/${state.tagLimit}") },
                    singleLine = true,
                    enabled = !saving
                )
                editorError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = tagName.isNotBlank() && !saving && (tagName.length <= state.tagLimit || tags.firstOrNull { it.id == editingId }?.name == tagName), onClick = {
                onSave(editingId, tagName)
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
                onDelete(pendingDelete.toList())
            }) { Text(if (deleting) "Deleting..." else "Delete") }
        },
        dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = emptyList() }) { Text("Cancel") } }
    )
}
