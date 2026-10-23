package com.nog.quotesapp.ui.screens

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nog.quotesapp.viewmodel.SettingsViewModel

@Composable
fun SettingsRoute(model:SettingsViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { model.selectFolder(it?.toString()) }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { model.loadImport(it?.toString()) }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { model.exportToUri(it?.toString()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        model.setPreciseAlarms(Build.VERSION.SDK_INT<31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
    }
    SettingsScreen(
        state=state,onDraft=model::changeDraft,onSave=model::save,onClearHistory=model::clearHistory,onReset=model::reset,
        onChooseFolder={folder.launch(null)},onRemoveFolder=model::removeFolder,
        onToggleExport=model::toggleExport,onToggleAllExport=model::toggleAllExport,onDependencies=model::exportDependencies,onQuoteTags=model::exportQuoteTags,
        onPrepareExport=model::prepareExport,onDismissExport=model::dismissExport,onExportFolder=model::exportToFolder,
        onSaveAs={model.hideExportPreview();exporter.launch(model.fileName())},
        onChooseImport={importer.launch(arrayOf("text/*","application/csv","application/octet-stream"))},
        onToggleImport=model::toggleImport,onToggleKey=model::toggleSetting,onMode=model::setMode,
        onPrepareImport=model::prepareImport,onDismissImport=model::dismissImport,onApplyImport=model::applyImport,
        onAlarmAccess={if(Build.VERSION.SDK_INT>=31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))},
        onDailyTag=model::toggleDailyTag,onDailyWhitelist=model::dailyWhitelist,onClearDailyTags=model::clearDailyTags,
        onNotificationSettings={
            context.startActivity(if(Build.VERSION.SDK_INT>=26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))
        }
    )
}
