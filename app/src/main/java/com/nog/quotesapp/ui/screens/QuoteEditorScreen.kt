package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nog.quotesapp.viewmodel.QuoteEditorViewModel

@Composable
fun QuoteEditorScreen(model: QuoteEditorViewModel, onBack: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.saved) { if (state.saved) currentOnBack() }
    if (state.loading) {
        Column(Modifier.padding(16.dp)) {
            if (state.error == null) CircularProgressIndicator() else Text(state.error!!)
            TextButton(onClick = onBack) { Text("Back") }
        }
    } else CreateQuoteScreen(
        state = state,
        onTextChange = model::changeText,
        onSelectionChange = model::changeTags,
        onReset = model::reset,
        onSave = model::save,
        onBack = onBack
    )
}
