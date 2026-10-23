package com.nvzhu.bgm

import android.content.Context
import java.util.UUID

data class ButtonConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "",
    val audioUri: String = "",
    val startTimeSeconds: Float = 0f,
    val endTimeSeconds: Float = -1f,
    val builtInResName: String = "",
    val iconUri: String = "",
    val builtInResId: Int = 0
) {
    fun hasAudio(): Boolean = audioUri.isNotEmpty() || builtInResId != 0
}

data class AppConfig(
    val columns: Int = 2,
    val buttonSize: String = SIZE_MEDIUM,
    val buttons: List<ButtonConfig> = emptyList()
) {
    companion object {
        const val SIZE_SMALL = "small"
        const val SIZE_MEDIUM = "medium"
        const val SIZE_LARGE = "large"
    }
}

object BuiltInButtons {
    const val DEFAULT_COUNT = 6

    fun resolve(context: Context, resName: String): Int {
        if (resName.isEmpty()) return 0
        return context.resources.getIdentifier(resName, "raw", context.packageName)
    }

    fun defaults(): List<ButtonConfig> {
        return (1..DEFAULT_COUNT).map { index ->
            ButtonConfig(
                id = "slot-$index",
                label = "按钮 $index",
                startTimeSeconds = 0f,
                endTimeSeconds = -1f
            )
        }
    }
}

object ButtonPalette {
    private val colors = intArrayOf(
        0xFFFF69B4.toInt(),
        0xFFFFD700.toInt(),
        0xFFDA70D6.toInt(),
        0xFFFF1493.toInt(),
        0xFFFFB6C1.toInt(),
        0xFFBA55D3.toInt()
    )

    fun colorFor(id: String): Int {
        val index = (id.hashCode() and Int.MAX_VALUE) % colors.size
        return colors[index]
    }
}
