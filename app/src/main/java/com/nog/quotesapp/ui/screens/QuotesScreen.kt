package com.nog.quotesapp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nog.quotesapp.data.db.DatabaseProvider
import com.nog.quotesapp.data.repository.QuoteRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuotesScreen(onCreateClick: () -> Unit, onEditClick: (Int) -> Unit) {
    val context = LocalContext.current.applicationContext
    val db = remember(context) { DatabaseProvider.getDatabase(context) }
    val repository = remember(db) { QuoteRepository(db) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val quoteFlow = remember(db) {
        db.quoteDao().observeAll().onEach { loading = false }.catch {
            if (it is CancellationException) throw it
            error = "Could not load quotes."; loading = false
        }
    }
    val tagFlow = remember(db) { db.tagDao().observeAll().catch {
        if (it is CancellationException) throw it
        error = "Could not load tags."
    } }
    val linkFlow = remember(db) { db.quoteTagCrossRefDao().observeAll().catch {
        if (it is CancellationException) throw it
        error = "Could not load tag links."
    } }
    val quotes by quoteFlow.collectAsState(initial = emptyList())
    val tags by tagFlow.collectAsState(initial = emptyList())
    val links by linkFlow.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query) { delay(200); debouncedQuery = query }
    var included by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var excluded by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var pendingDelete by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var deleting by remember { mutableStateOf(false) }
    BackHandler(enabled = selecting) { selecting = false; selected = emptyList() }
    val existingTags = tags.map { it.id }.toSet()
    val visible = rankEntries(quotes.filter { quote ->
        matchesTags(links.filter { it.quoteId == quote.id }.map { it.tagId }.toSet(),
            included.toSet().intersect(existingTags), excluded.toSet().intersect(existingTags))
    }, debouncedQuery) { it.text }

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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        else if (visible.isEmpty()) Text(if (quotes.isEmpty()) "No quotes yet. Create your first quote." else "No quotes match these tag filters.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
        dismissButton = { TextButton(onClick = { included = emptyList(); excluded = emptyList() }) { Text("Clear filters") } }
    )
    if (pendingDelete.isNotEmpty()) AlertDialog(
        onDismissRequest = { if (!deleting) pendingDelete = emptyList() },
        title = { Text("Delete ${pendingDelete.size} quote(s)?") },
        text = { Column { Text("This also removes their tag links and recent history."); error?.let { Text(it) } } },
        confirmButton = { TextButton(enabled = !deleting, onClick = {
            val ids = pendingDelete.toList(); deleting = true
            scope.launch {
                try {
                    repository.deleteQuotes(ids)
                    pendingDelete = emptyList(); selected = emptyList(); selecting = false; error = null
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (exception: Exception) { error = "Could not delete quotes." }
                finally { deleting = false }
            }
        }) { Text(if (deleting) "Deleting..." else "Delete") } },
        dismissButton = { TextButton(enabled = !deleting, onClick = { pendingDelete = emptyList() }) { Text("Cancel") } }
    )
}
