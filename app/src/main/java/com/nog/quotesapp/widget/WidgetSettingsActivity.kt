package com.nog.quotesapp.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nog.quotesapp.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID ||
            manager.getAppWidgetInfo(id)?.provider != ComponentName(this, QuoteWidgetProvider::class.java)) {
            finish(); return
        }
        val saved = WidgetPreferences.load(this, id)
        setContent {
            MyApplicationTheme {
                var transparent by rememberSaveable { mutableStateOf(saved.transparent) }
                var hex by rememberSaveable { mutableStateOf(String.format("%06X", saved.color and 0xFFFFFF)) }
                var lightText by rememberSaveable { mutableStateOf(saved.lightText) }
                var saving by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                val color = if (hex.matches(Regex("[0-9a-fA-F]{6}"))) AndroidColor.parseColor("#$hex") else null
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Widget appearance", style = MaterialTheme.typography.headlineMedium)
                        Text("Appearance is saved separately for each widget. The quote is shared with Home.")
                        Row { Switch(transparent, { transparent = it }, enabled = !saving); Text("Transparent background", Modifier.padding(12.dp)) }
                        if (!transparent) {
                            Text("Background color")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("F5F0FA", "FFFFFF", "202124", "D7E3FF").forEach { value ->
                                    Button(onClick = { hex = value }, enabled = !saving,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(AndroidColor.parseColor("#$value"))),
                                        modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { }
                                }
                            }
                            OutlinedTextField(hex, { hex = it.removePrefix("#").take(6) }, label = { Text("Custom color (RRGGBB)") },
                                singleLine = true, isError = color == null, enabled = !saving)
                        }
                        Row { Switch(lightText, { lightText = it }, enabled = !saving); Text("White text (off: black)", Modifier.padding(12.dp)) }
                        Text("Quote preview", color = if (lightText) Color.White else Color.Black,
                            modifier = Modifier.fillMaxWidth().background(if (transparent) MaterialTheme.colorScheme.surfaceVariant else Color(color ?: saved.color)).padding(24.dp))
                        Text("Resize by holding the widget on your home screen and dragging its edges. Available sizes follow your launcher's grid.")
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(enabled = !saving && (transparent || color != null), onClick = {
                            saving = true; error = null
                            lifecycleScope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        check(WidgetPreferences.save(this@WidgetSettingsActivity, id,
                                            WidgetAppearance(transparent, color ?: saved.color, lightText))) { "Could not save appearance." }
                                        QuoteWidgetProvider.syncAll(this@WidgetSettingsActivity)
                                    }
                                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                    finish()
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = "Could not update widget. Please try again."; saving = false }
                            }
                        }) { Text(if (saving) "Saving..." else "Save") }
                        TextButton(onClick = { finish() }, enabled = !saving) { Text("Cancel") }
                    }
                }
            }
        }
    }
}
