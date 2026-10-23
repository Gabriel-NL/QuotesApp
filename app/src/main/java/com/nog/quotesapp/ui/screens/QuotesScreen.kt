package com.nog.quotesapp.ui.screens

import com.nog.quotesapp.model.EntryOrder
import com.nog.quotesapp.ui.components.OrderControls
import com.nog.quotesapp.model.rankEntries
import com.nog.quotesapp.model.matchesTags
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.nog.quotesapp.viewmodel.QuotesUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuotesScreen(
    state: QuotesUiState,
    onDelete: (List<Int>) -> Unit,
    onCreateClick: () -> Unit,
    onEditClick: (Int) -> Unit
) {
    val quotes = state.quotes
    val tags = state.tags
    val links = state.links
    val error = state.error
    val loading = state.loading
    val deleting = state.deleting
    var query by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(EntryOrder.ALPHABETIC) }
    var descending by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(query,order,descending) { listState.scrollToItem(0) }
    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) { delay(200); debouncedQuery = query }
    var included by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var excluded by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var onlyUntagged by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var pendingDelete by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var handledDeletion by rememberSaveable { mutableStateOf(state.deletedVersion) }
    LaunchedEffect(state.deletedVersion) {
        if (state.deletedVersion != handledDeletion) {
            pendingDelete = emptyList(); selected = emptyList(); selecting = false
            handledDeletion = state.deletedVersion
        }
    }
    BackHandler(enabled = selecting) { selecting = false; selected = emptyList() }
    val existingTags = tags.map { it.id }.toSet()
    val visible = rankEntries(quotes.filter { quote ->
        matchesTags(links.filter { it.quoteId == quote.id }.map { it.tagId }.toSet(),
            included.toSet().intersect(existingTags), excluded.toSet().intersect(existingTags), onlyUntagged)
    }, debouncedQuery, order, descending) { it.text }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quotes", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selecting) {
                Button(onClick = { selecting = false; selected = emptyList() }) { Text("Cancel") }
                Button(onClick = { pendingDelete = selected }, enabled = selected.isNotEmpty()) {
                    Text("Delete selected (${selected.size})")
                }
            } else {
                Button(onClick = onCreateClick, modifier = Modifier.weight(1f)) { Text("Create") }
                Button(onClick = { selecting = true }, enabled = quotes.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Delete Multiple") }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search") }, modifier = Modifier.weight(1f))
            Button(onClick = { showTags = true }, modifier = Modifier.fillMaxHeight()) {
                Text("Tags (${included.size + excluded.size})")
            }
        }
        FilterChip(selected = onlyUntagged, onClick = {
            onlyUntagged = !onlyUntagged
            if (onlyUntagged) { included = emptyList(); excluded = emptyList() }
        }, label = { Text("Untagged") })
        OrderControls(order,descending,{order=it},{descending=it})
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        else if (visible.isEmpty()) Text(if (quotes.isEmpty()) "No quotes yet. Create your first quote." else "No quotes match these tag filters.")
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { quote ->
                Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = {
                    if (!selecting) onEditClick(quote.id)
                })) {
                    Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        if (selecting) Checkbox(checked = quote.id in selected, onCheckedChange = {
                            selected = if (it) selected + quote.id else selected - quote.id
                        })
                        Text(quote.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (!selecting) {
                            TextButton(onClick = { onEditClick(quote.id) }) { Text("Edit") }
                            TextButton(onClick = { pendingDelete = listOf(quote.id) }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }
    if (showTags) AlertDialog(
        onDismissRequest = { showTags = false }, title = { Text("Filter by tags") },
        text = {
            Column {
                Text("Tap to cycle: Any / Include / Exclude. All included tags must match.")
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        val state = when (tag.id) { in included -> "Include"; in excluded -> "Exclude"; else -> "Any" }
                        TextButton(onClick = {
                            onlyUntagged = false
                            when (tag.id) {
                                in included -> { included = included - tag.id; excluded = excluded + tag.id }
                                in excluded -> excluded = excluded - tag.id
                                else -> included = included + tag.id
                            }
                        }) {
                            Text("${tag.name}: $state", color = when (tag.id) {
                                in included -> Color(0xFF2E7D32)
                                in excluded -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            })
                        }
                    }
                    if (tags.isEmpty()) item { Text("No tags yet. Create tags on the Tags screen.") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showTags = false }) { Text("Done") } },
        dismissButton = { TextButton(onClick = { included = emptyList(); excluded = emptyList(); onlyUntagged = false }) { Text("Clear filters") } }
    )
    if (pendingDelete.isNotEmpty()) AlertDialog(
        onDismissRequest = { if (!deleting) pendingDelete = emptyList() },
        title = { Text("Delete ${pendingDelete.size} quote(s)?") },
        text = { Column { Text("This also removes their tag links and recent history."); error?.let { Text(it) } } },
        confirmButton = { TextButton(enabled = !deleting, onClick = {
            onDelete(pendingDelete.toList())
        }) { Text(if (deleting) "Deleting..." else "Delete") } },
        dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = emptyList() }) { Text("Cancel") } }
    )
}
