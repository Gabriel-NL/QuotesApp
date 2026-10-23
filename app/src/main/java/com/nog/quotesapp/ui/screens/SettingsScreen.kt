package com.nog.quotesapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.nog.quotesapp.backup.*
import com.nog.quotesapp.model.AppSettings
import com.nog.quotesapp.model.tagFilterKey
import com.nog.quotesapp.viewmodel.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state:SettingsUiState,onDraft:(SettingsDraft)->Unit,onSave:()->Unit,
    onClearHistory:()->Unit,onReset:()->Unit,onChooseFolder:()->Unit,onRemoveFolder:()->Unit,
    onToggleExport:(DataGroup)->Unit,onToggleAllExport:()->Unit,onDependencies:(Boolean)->Unit,onQuoteTags:(Boolean)->Unit,
    onPrepareExport:()->Unit,onDismissExport:()->Unit,onExportFolder:()->Unit,onSaveAs:()->Unit,
    onChooseImport:()->Unit,onToggleImport:(DataGroup)->Unit,onToggleKey:(String)->Unit,
    onMode:(ImportMode)->Unit,onPrepareImport:()->Unit,onDismissImport:()->Unit,onApplyImport:()->Unit,
    onAlarmAccess:()->Unit,onNotificationSettings:()->Unit,
    onDailyTag:(String)->Unit,onDailyWhitelist:(Boolean)->Unit,onClearDailyTags:()->Unit
) {
    var confirmation by remember { mutableStateOf<String?>(null) }
    var openSection by rememberSaveable { mutableStateOf<String?>(null) }
    var tagQuery by rememberSaveable { mutableStateOf("") }
    fun toggleSection(section: String) { openSection = if (openSection == section) null else section }
    val enabled=!state.busy && !state.loading
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Settings",style=MaterialTheme.typography.headlineMedium)
        if(state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        SettingsSection("General settings", openSection == "general", { toggleSection("general") }) {
            Text("Daily quote notifications use the time below. Dismissed notifications stay hidden until the next quote day or a Home reroll.")
            TextButton(onClick=onNotificationSettings,enabled=enabled) { Text("Notification permissions") }
            OutlinedTextField(state.draft.time,{onDraft(state.draft.copy(time=it))},label={Text("Daily quote time (HH:mm)")},singleLine=true,enabled=enabled,modifier=Modifier.fillMaxWidth())
            if(!state.preciseAlarms) {
                Text("Android may delay background updates without Alarms & reminders access.")
                TextButton(onClick=onAlarmAccess,enabled=enabled) { Text("Allow precise updates") }
            }
            OutlinedTextField(state.draft.history,{onDraft(state.draft.copy(history=it))},label={Text("Recent history (0-10000; 0 disables protection)")},singleLine=true,enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(state.draft.quoteLimit,{onDraft(state.draft.copy(quoteLimit=it))},label={Text("Quote character limit (1-100000)")},singleLine=true,enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            OutlinedTextField(state.draft.tagLimit,{onDraft(state.draft.copy(tagLimit=it))},label={Text("Tag character limit (1-1000)")},singleLine=true,enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            Text("Lower limits never shorten saved entries. Unchanged longer entries can remain.")
            Button(onClick=onSave,enabled=enabled && state.dirty) { Text("Save settings") }
            TextButton(onClick={confirmation="history"},enabled=enabled) { Text("Clear recent history") }
            TextButton(onClick={confirmation="defaults"},enabled=enabled) { Text("Restore default settings") }
        }
        SettingsSection("Home & widget tag filter", openSection == "dailyTags", { toggleSection("dailyTags") }) {
            Text("Applies to Home results and the shared daily quote. Changes are saved automatically.")
            OutlinedTextField(tagQuery,{tagQuery=it},label={Text("Search tags")},singleLine=true,
                enabled=enabled,modifier=Modifier.fillMaxWidth())
            val selectedNames=state.settings.dailyTagNames.map(::tagFilterKey).toSet()
            val suggestions=state.availableTags.filter { tag ->
                tag.name.contains(tagQuery,ignoreCase=true) && tagFilterKey(tag.name) !in selectedNames
            }.distinctBy { tagFilterKey(it.name) }.take(3)
            suggestions.forEach { tag ->
                TextButton(onClick={onDailyTag(tag.name);tagQuery=""},enabled=enabled) { Text("Add ${tag.name}") }
            }
            if(suggestions.isEmpty() && tagQuery.isNotBlank()) Text("No unselected tags match.")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                state.settings.dailyTagNames.sorted().forEach { name ->
                    InputChip(selected=true,onClick={onDailyTag(name)},enabled=enabled,
                        label={Text("$name (remove)")})
                }
            }
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked=state.settings.dailyWhitelist,onCheckedChange=onDailyWhitelist,enabled=enabled)
                Text(if(state.settings.dailyWhitelist) "Whitelist: allow selected tags" else "Blacklist: hide selected tags",
                    modifier=Modifier.padding(start=8.dp))
            }
            Text("Whitelist matches any selected tag. Blacklist excludes any selected tag. An empty list allows all quotes. Untagged quotes remain available in blacklist mode.")
            TextButton(onClick=onClearDailyTags,enabled=enabled && state.settings.dailyTagNames.isNotEmpty()) { Text("Clear all filter tags") }
        }
        SettingsSection("Backup folder", openSection == "folder", { toggleSection("folder") }) {
            Text(if(state.settings.backupFolder.isEmpty()) "Not selected. Exports will use Save as." else "Saved folder: ${state.folderName}")
            Text("The working database always stays inside the app. Folder permission is not included in backups.")
            Text("Changing this folder moves valid app CSV backups from the previous folder. Subfolders and other files stay there. Originals are removed only after verifying the new copies; existing destination files are kept.")
            Text("Changing this folder moves valid app CSV backups from the previous folder. Subfolders and other files stay there. Originals are removed only after verifying the new copies; existing destination files are kept.")
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick=onChooseFolder,enabled=enabled) { Text("Choose folder") }
                TextButton(onClick=onRemoveFolder,enabled=enabled && state.settings.backupFolder.isNotEmpty()) { Text("Use Save as") }
            }
        }
        SettingsSection("Export CSV", openSection == "export", { toggleSection("export") }) {
            val allChecked=state.exportGroups.containsAll(DataGroup.entries) && state.includeDependencies && state.includeQuoteTags
            TextButton(onClick=onToggleAllExport,enabled=enabled) { Text(if(allChecked) "Uncheck all" else "Check all") }
            Text("Select all for a complete backup, or choose specific datasets. Exporting never changes the database.")
            FlowRow(
                modifier=Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(12.dp),
                verticalArrangement=Arrangement.spacedBy(4.dp)
            ) {
                DataGroup.entries.forEach { group ->
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(group in state.exportGroups,{onToggleExport(group)},enabled=enabled)
                    Text(group.key)
                }
            }
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(state.includeDependencies,onDependencies,enabled=enabled);Text("Include required dependencies")
            }
            Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(state.includeQuoteTags,onQuoteTags,enabled=enabled);Text("Include tags and links with quotes")
            }
            }
            Button(onClick=onPrepareExport,enabled=enabled && state.exportGroups.isNotEmpty()) { Text("Preview export") }
        }
        SettingsSection("Import CSV", openSection == "import", { toggleSection("import") }) {
            Button(onClick=onChooseImport,enabled=enabled) { Text("Choose CSV file") }
            state.importCounts?.let { counts ->
                counts.entries.sortedBy { it.key.ordinal }.forEach { (group,count) ->
                    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(group in state.importGroups,{onToggleImport(group)},enabled=enabled)
                        Text("${group.key}: $count record(s)")
                    }
                }
                if(DataGroup.SETTINGS in state.importGroups) {
                    Text("Settings keys to replace:")
                    state.importedKeys.sorted().forEach { key ->
                        Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(key in state.selectedKeys,{onToggleKey(key)},enabled=enabled && key in AppSettings.KEYS)
                            Text(key+if(key in AppSettings.KEYS) "" else " (unsupported; ignored)")
                        }
                    }
                }
                if(state.importGroups.any { it in setOf(DataGroup.QUOTES,DataGroup.TAGS,DataGroup.LINKS) }) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.importMode==ImportMode.MERGE,{onMode(ImportMode.MERGE)},label={Text("Add / merge")},enabled=enabled)
                    FilterChip(state.importMode==ImportMode.REPLACE,{onMode(ImportMode.REPLACE)},label={Text("Replace")},enabled=enabled)
                }
                Text("History, daily state, and selected settings always use replacement. Absent settings stay unchanged.")
                Button(onClick=onPrepareImport,enabled=enabled && state.importGroups.isNotEmpty()) { Text("Validate and preview import") }
            }
        }
        state.message?.let { Text(it,style=MaterialTheme.typography.bodyMedium) }
        Spacer(Modifier.height(16.dp))
    }
    if(confirmation!=null) AlertDialog(
        onDismissRequest={if(!state.busy) confirmation=null},
        title={Text(if(confirmation=="history") "Clear recent history?" else "Restore defaults?")},
        text={Text(if(confirmation=="history") "The current daily quote is kept." else "Only settings are reset. Quotes, tags, and their links are kept.")},
        confirmButton={TextButton(enabled=!state.busy,onClick={val action=confirmation;confirmation=null;if(action=="history") onClearHistory() else onReset()}) {Text("Confirm")}},
        dismissButton={TextButton(onClick={confirmation=null}) {Text("Cancel")}}
    )
    state.exportPreview?.let { preview -> AlertDialog(
        onDismissRequest=onDismissExport,title={Text("Export preview")},
        text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
            Text(preview.summary);state.message?.let { Text(it) }
        }},
        confirmButton={TextButton(enabled=!state.busy,onClick=if(state.settings.backupFolder.isEmpty()) onSaveAs else onExportFolder) {Text(if(state.settings.backupFolder.isEmpty()) "Save as" else "Export to folder")}},
        dismissButton={Row {TextButton(enabled=!state.busy,onClick=onSaveAs) {Text("Save as")};TextButton(enabled=!state.busy,onClick=onDismissExport) {Text("Cancel")}}}
    ) }
    state.importPreview?.let { summary -> AlertDialog(
        onDismissRequest=onDismissImport,title={Text("Confirm import")},
        text={Text(summary,modifier=Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(enabled=!state.busy,onClick=onApplyImport) {Text("Apply import")}},
        dismissButton={TextButton(enabled=!state.busy,onClick=onDismissImport) {Text("Cancel")}}
    ) }
}

@Composable
private fun SettingsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    OutlinedButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().semantics {
            stateDescription = if (expanded) "Expanded" else "Collapsed"
        }
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(if (expanded) "Hide" else "Show")
    }
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
