package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nog.quotesapp.viewmodel.HomeUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onReroll: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.query) { listState.scrollToItem(0) }
    val available = !state.loading && state.quotes.isNotEmpty()
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Quote of the day", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth().alpha(if (available) 1f else 0.4f)) {
            Text(state.dailyQuote?.text ?: if (state.loading) "Loading..." else "No quote available. Check your quotes and Home tag filter.",
                maxLines = 6, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp))
        }
        Button(onClick = onReroll, enabled = available && !state.rerolling, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.rerolling) "..." else "Reroll")
        }
        OutlinedTextField(
            value = state.query, onValueChange = onQueryChange, singleLine = true,
            enabled = available, label = { Text("Search quotes") }, modifier = Modifier.fillMaxWidth()
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading) CircularProgressIndicator()
        LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.results, key = { it.id }) { quote ->
                Card(Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onEdit(quote.id) })) {
                    Text(quote.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp).fillMaxWidth())
                }
            }
        }
    }
}
