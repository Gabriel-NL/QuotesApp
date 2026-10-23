package com.nog.quotesapp.ui.screens

import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nog.quotesapp.viewmodel.QuoteViewModel
import com.nog.quotesapp.viewmodel.TagsViewModel

@Composable
fun QuotesRoute(model: QuoteViewModel, onCreateClick: () -> Unit, onEditClick: (Int) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    QuotesScreen(state, model::deleteQuotes, onCreateClick, onEditClick)
}

@Composable
fun TagsRoute(model: TagsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    TagsScreen(state, model::saveTag, model::deleteTags, model::clearEditorError)
}

@Composable
fun HomeRoute(model: com.nog.quotesapp.viewmodel.HomeViewModel, onEdit: (Int) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { model.refresh() }
    HomeScreen(state, model::changeQuery, model::reroll, onEdit)
}
