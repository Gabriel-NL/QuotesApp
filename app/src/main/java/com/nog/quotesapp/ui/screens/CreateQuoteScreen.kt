package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import com.nog.quotesapp.model.Tag
import com.nog.quotesapp.viewmodel.QuoteEditorUiState


@Composable
fun CreateQuoteScreen(
    state: QuoteEditorUiState,
    onTextChange: (String) -> Unit,
    onSelectionChange: (List<Int>) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    val text = state.text
    val selectedTagIds = state.selectedTagIds
    val saving = state.saving
    val isEditing = state.isEditing
    val error = state.error
    var confirmExit by remember { mutableStateOf(false) }
    val requestBack = {
        if (!saving) {
            if (state.dirty) confirmExit = true
            else onBack()
        }
    }
    BackHandler { requestBack() }

    val title = if (isEditing) "Edit a Quote" else "Create a Quote"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium
            )

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Quote") },
                enabled = !saving,
                singleLine = false,
                minLines = 4,
                supportingText = { Text("${text.length}/${state.quoteLimit}") },
                modifier = Modifier.fillMaxWidth()

            )

            TagSearchField(
                tags = state.tags,
                loadFailed = state.tagsError != null,
                enabled = !saving,
                selectedTagIds = selectedTagIds,
                onSelectionChange = onSelectionChange
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { requestBack() }, enabled = !saving) {
                Text("Back")
            }

            Button(
                onClick = onSave,
                enabled = text.isNotBlank() && !saving && (text == state.initialText || text.length <= state.quoteLimit)
            ) {
                Text(if (saving) "Saving..." else if (isEditing) "Save" else "Create")
            }

            Button(onClick = onReset, enabled = !saving) {
                Text(if (isEditing) "Reset" else "Clear")
            }
        }
    }
    if (confirmExit) AlertDialog(
        onDismissRequest = { confirmExit = false },
        title = { Text("Discard unsaved changes?") },
        confirmButton = { TextButton(onClick = { confirmExit = false; onBack() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Keep editing") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagSearchField(
    tags: List<Tag>,
    loadFailed: Boolean,
    enabled: Boolean,
    selectedTagIds: List<Int>,
    onSelectionChange: (List<Int>) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val suggestions = tags.filter {
        it.id !in selectedTagIds && it.name.contains(query.trim(), ignoreCase = true)
    }.take(3)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            label = { Text("Search existing tags") },
            singleLine = true,
            enabled = enabled,
            isError = loadFailed,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryEditable,
                    enabled = enabled
                )
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (suggestions.isEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(when {
                            loadFailed -> "Could not load tags"
                            tags.isEmpty() -> "No tags available"
                            else -> "No matching tags"
                        })
                    },
                    onClick = {},
                    enabled = false
                )
            }
            suggestions.forEach { tag ->
                DropdownMenuItem(
                    enabled = enabled,
                    text = { Text(tag.name) },
                    onClick = {
                        onSelectionChange(selectedTagIds + tag.id)
                        query = ""
                        expanded = false
                    }
                )
            }
        }
    }

    // The parent passes these selected IDs to the quote creation callback.
    tags.filter { it.id in selectedTagIds }.forEach { tag ->
        InputChip(
            enabled = enabled,
            selected = true,
            onClick = { onSelectionChange(selectedTagIds - tag.id) },
            label = { Text("${tag.name} (remove)") }
        )
    }
}
