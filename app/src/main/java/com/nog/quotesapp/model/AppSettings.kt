package com.nog.quotesapp.model

import kotlinx.coroutines.flow.Flow

data class SettingValue(val key: String, val type: String, val value: String)
data class AppSettings(
    val dailyMinutes: Int = 0, val historyLimit: Int = 5,
    val quoteLimit: Int = 5000, val tagLimit: Int = 100,
    val backupFolder: String = "",
    val dailyTagNames: Set<String> = emptySet(), val dailyWhitelist: Boolean = false
) {
    fun values() = listOf(
        SettingValue("daily_minutes","integer",dailyMinutes.toString()),
        SettingValue("history_limit","integer",historyLimit.toString()),
        SettingValue("quote_limit","integer",quoteLimit.toString()),
        SettingValue("tag_limit","integer",tagLimit.toString()),
        SettingValue("backup_folder","string",backupFolder),
        SettingValue("daily_tag_names","string",dailyTagNames.sorted().joinToString("") { "${it.length}:$it" }),
        SettingValue("daily_tag_whitelist","boolean",dailyWhitelist.toString())
    )
    fun validated(): AppSettings {
        require(dailyMinutes in 0..1439) { "Time must be between 00:00 and 23:59." }
        require(historyLimit in 0..10000) { "History must be between 0 and 10000." }
        require(quoteLimit in 1..100000) { "Quote limit must be between 1 and 100000." }
        require(tagLimit in 1..1000) { "Tag limit must be between 1 and 1000." }
        require(dailyTagNames.all { it.isNotBlank() && it.length <= 1000 }) { "Invalid filter tag name." }
        require(backupFolder.isEmpty() || backupFolder.startsWith("content://")) { "Choose a folder using the Android picker." }
        return this
    }
    companion object {
        val KEYS = AppSettings().values().map { it.key }.toSet()
        fun from(values: List<SettingValue>, base: AppSettings = AppSettings()): AppSettings {
            val merged=base.values().associateBy { it.key }.toMutableMap()
            values.forEach { value ->
                require(value.key in KEYS) { "Unknown setting: ${value.key}" }
                require(value.type == merged.getValue(value.key).type) { "Wrong type for ${value.key}" }
                merged[value.key]=value
            }
            fun number(key:String)=merged.getValue(key).value.toIntOrNull() ?: error("Invalid integer: $key")
            val encoded=merged.getValue("daily_tag_names").value
            val names=mutableSetOf<String>();var offset=0
            while(offset<encoded.length) {
                val colon=encoded.indexOf(':',offset)
                require(colon>offset) { "Invalid tag filter." }
                val length=encoded.substring(offset,colon).toIntOrNull() ?: error("Invalid tag filter.")
                require(length in 1..1000 && length<=encoded.length-colon-1) { "Invalid tag filter." }
                names+=encoded.substring(colon+1,colon+1+length);offset=colon+1+length
            }
            val whitelist=merged.getValue("daily_tag_whitelist").value
            require(whitelist in setOf("true","false")) { "Invalid filter mode." }
            return AppSettings(number("daily_minutes"),number("history_limit"),number("quote_limit"),number("tag_limit"),merged.getValue("backup_folder").value,names,whitelist=="true").validated()
        }
    }
}
interface SettingsStore {
    fun observe(): Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun save(settings: AppSettings)
    suspend fun clearHistory()
}
