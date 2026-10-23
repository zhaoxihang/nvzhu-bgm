package com.nvzhu.bgm

import android.content.Context
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.legacyDataStore by preferencesDataStore(name = "button_configs")

class ConfigRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun load(): AppConfig = mutex.withLock {
        val existing = prefs.getString(KEY_CONFIG, null)
        if (existing != null) {
            return@withLock parseOrDefault(existing)
        }
        val migrated = migrateLegacy()
        prefs.edit().putString(KEY_CONFIG, toJson(migrated)).commit()
        migrated
    }

    suspend fun save(config: AppConfig) = mutex.withLock {
        val normalized = config.copy(
            columns = config.columns.coerceIn(2, 4),
            buttonSize = normalizeSize(config.buttonSize)
        )
        prefs.edit().putString(KEY_CONFIG, toJson(normalized)).commit()
    }

    private suspend fun migrateLegacy(): AppConfig {
        return try {
            val stored = context.legacyDataStore.data.first()
            val hasLegacy = (0 until BuiltInButtons.DEFAULT_COUNT).any { index ->
                stored.contains(stringPreferencesKey("button_${index}_label")) ||
                    stored.contains(stringPreferencesKey("button_${index}_audio_uri")) ||
                    stored.contains(floatPreferencesKey("button_${index}_start_time")) ||
                    stored.contains(floatPreferencesKey("button_${index}_end_time"))
            }
            if (!hasLegacy) {
                return AppConfig(buttons = BuiltInButtons.defaults())
            }
            val buttons = (0 until BuiltInButtons.DEFAULT_COUNT).map { index ->
                val labelKey = stringPreferencesKey("button_${index}_label")
                val uriKey = stringPreferencesKey("button_${index}_audio_uri")
                val startKey = floatPreferencesKey("button_${index}_start_time")
                val endKey = floatPreferencesKey("button_${index}_end_time")
                ButtonConfig(
                    id = "slot-${index + 1}",
                    label = stored[labelKey] ?: "按钮 ${index + 1}",
                    audioUri = stored[uriKey] ?: "",
                    startTimeSeconds = stored[startKey] ?: 0f,
                    endTimeSeconds = stored[endKey] ?: -1f
                )
            }
            AppConfig(columns = 2, buttonSize = AppConfig.SIZE_MEDIUM, buttons = buttons)
        } catch (e: Exception) {
            AppConfig(buttons = BuiltInButtons.defaults())
        }
    }

    private fun parseOrDefault(json: String): AppConfig {
        return try {
            parse(json)
        } catch (e: Exception) {
            AppConfig(buttons = BuiltInButtons.defaults())
        }
    }

    private fun parse(json: String): AppConfig {
        val root = JSONObject(json)
        val columns = root.optInt("columns", 2).coerceIn(2, 4)
        val size = normalizeSize(root.optString("buttonSize", AppConfig.SIZE_MEDIUM))
        val arr = root.optJSONArray("buttons") ?: JSONArray()
        val buttons = ArrayList<ButtonConfig>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val resName = obj.optString("builtIn")
            buttons.add(
                ButtonConfig(
                    id = obj.optString("id").ifEmpty { UUID.randomUUID().toString() },
                    label = obj.optString("label"),
                    audioUri = obj.optString("audioUri"),
                    startTimeSeconds = obj.optDouble("start", 0.0).toFloat(),
                    endTimeSeconds = obj.optDouble("end", -1.0).toFloat(),
                    builtInResName = resName,
                    iconUri = obj.optString("iconUri"),
                    builtInResId = BuiltInButtons.resolve(context, resName)
                )
            )
        }
        return AppConfig(columns = columns, buttonSize = size, buttons = buttons)
    }

    private fun toJson(config: AppConfig): String {
        val root = JSONObject()
        root.put("columns", config.columns)
        root.put("buttonSize", config.buttonSize)
        val arr = JSONArray()
        for (button in config.buttons) {
            val obj = JSONObject()
            obj.put("id", button.id)
            obj.put("label", button.label)
            obj.put("audioUri", button.audioUri)
            obj.put("start", button.startTimeSeconds.toDouble())
            obj.put("end", button.endTimeSeconds.toDouble())
            obj.put("builtIn", button.builtInResName)
            obj.put("iconUri", button.iconUri)
            arr.put(obj)
        }
        root.put("buttons", arr)
        return root.toString()
    }

    private fun normalizeSize(size: String): String {
        return when (size) {
            AppConfig.SIZE_SMALL, AppConfig.SIZE_LARGE -> size
            else -> AppConfig.SIZE_MEDIUM
        }
    }

    companion object {
        private const val PREFS_NAME = "nvzhu_layout"
        private const val KEY_CONFIG = "config_json"
    }
}
