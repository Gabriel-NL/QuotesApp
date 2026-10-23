package com.nog.quotesapp.widget

import android.content.Context
import android.graphics.Color

data class WidgetAppearance(
    val transparent: Boolean = false,
    val color: Int = Color.rgb(245, 240, 250),
    val lightText: Boolean = false
)

object WidgetPreferences {
    private fun prefs(context: Context) = context.getSharedPreferences("widget_appearance", Context.MODE_PRIVATE)
    fun load(context: Context, id: Int): WidgetAppearance = prefs(context).let {
        WidgetAppearance(it.getBoolean("${id}_transparent", false),
            it.getInt("${id}_color", Color.rgb(245, 240, 250)), it.getBoolean("${id}_light", false))
    }
    fun save(context: Context, id: Int, value: WidgetAppearance): Boolean = prefs(context).edit()
        .putBoolean("${id}_transparent", value.transparent).putInt("${id}_color", value.color)
        .putBoolean("${id}_light", value.lightText).commit()
    fun delete(context: Context, id: Int) {
        prefs(context).edit().remove("${id}_transparent").remove("${id}_color").remove("${id}_light").apply()
    }
}
