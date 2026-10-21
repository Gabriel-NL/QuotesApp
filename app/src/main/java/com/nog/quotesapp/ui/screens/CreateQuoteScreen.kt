package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nog.quotesapp.data.db.DatabaseProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch

@Composable
fun CreateQuoteScreen(
    onCreate: (String, List<Int>) -> Unit,
    onBack: () -> Unit,
    initialText: String = "",
    isEditing: Boolean = false,
    saving: Boolean = false,
    error: String? = null
) {
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptyList<Int>()) }
    var confirmExit by remember { mutableStateOf(false) }
    val requestBack = {
        if (!saving) {
            if (text != initialText || selectedTagIds.isNotEmpty()) confirmExit = true
            else onBack()
        }
    }
    BackHandler { requestBack() }

    val title = if (isEditing) "Edit a Quote" else "Create a Quote"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it.replace("\r", "").replace("\n", " ")
            },
            label = { Text("Quote") },
            enabled = !saving,
            singleLine = false,
            minLines = 4,
            modifier = Modifier.fillMaxWidth()

        )

        TagSearchField(
            selectedTagIds = selectedTagIds,
            onSelectionChange = { if (!saving) selectedTagIds = it }
        )

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { requestBack() }, enabled = !saving) {
                Text("Back")
            }

            Button(
                onClick = { onCreate(text.trim(), selectedTagIds.toList()) },
                enabled = text.isNotBlank() && !saving
            ) {
                Text(if (saving) "Saving..." else if (isEditing) "Save" else "Create")
            }

            Button(onClick = {
                text = initialText
                selectedTagIds = emptyList()
            }, enabled = !saving) {
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
    selectedTagIds: List<Int>,
    onSelectionChange: (List<Int>) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val tagDao = remember(context) { DatabaseProvider.getDatabase(context).tagDao() }
    var loadFailed by remember { mutableStateOf(false) }
    val tagFlow = remember(tagDao) {
        tagDao.observeAll().catch { error ->
            if (error is CancellationException) throw error
            loadFailed = true
            emit(emptyList())
        }
    }
    val tags by tagFlow.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val suggestions = tags.filter {
        it.id !in selectedTagIds && it.name.contains(query.trim(), ignoreCase = true)
    }.take(3)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
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
            isError = loadFailed,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryEditable,
                    enabled = true
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
            selected = true,
            onClick = { onSelectionChange(selectedTagIds - tag.id) },
            label = { Text("${tag.name} ×") }
        )
    }
}
