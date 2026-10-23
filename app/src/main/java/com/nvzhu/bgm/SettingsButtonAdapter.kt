package com.nvzhu.bgm

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nvzhu.bgm.databinding.SettingsButtonItemBinding
import kotlinx.coroutines.CoroutineScope

class SettingsButtonAdapter(
    private val scope: CoroutineScope,
    private val onPickAudio: (Int) -> Unit,
    private val onPickIcon: (Int) -> Unit,
    private val onPreview: (ButtonConfig) -> Unit,
    private val onDelete: (String) -> Unit,
    private val onDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<SettingsButtonAdapter.Holder>() {

    val items = mutableListOf<ButtonConfig>()
    private val invalidKeys = mutableSetOf<String>()

    fun submit(buttons: List<ButtonConfig>) {
        items.clear()
        items.addAll(buttons)
        invalidKeys.clear()
        notifyDataSetChanged()
    }

    fun add(button: ButtonConfig) {
        items.add(button)
        notifyItemInserted(items.lastIndex)
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun update(index: Int, button: ButtonConfig) {
        if (index !in items.indices) return
        items[index] = button
        notifyItemChanged(index)
    }

    fun removeById(id: String) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        invalidKeys.remove(startKey(id))
        invalidKeys.remove(endKey(id))
        items.removeAt(index)
        notifyItemRemoved(index)
        notifyItemRangeChanged(index, items.size - index)
    }

    fun timesValid(): Boolean = invalidKeys.isEmpty()

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = SettingsButtonItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position], position)
    }

    private fun startKey(id: String) = "$id|start"
    private fun endKey(id: String) = "$id|end"

    inner class Holder(private val binding: SettingsButtonItemBinding) : RecyclerView.ViewHolder(binding.root) {
        var suppress = false

        init {
            binding.labelEdit.addTextChangedListener(afterChange { text ->
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@afterChange
                items[pos] = items[pos].copy(label = text)
            })
            binding.startTimeEdit.addTextChangedListener(afterChange { text ->
                updateTime(text, isEnd = false)
            })
            binding.endTimeEdit.addTextChangedListener(afterChange { text ->
                updateTime(text, isEnd = true)
            })
            binding.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onDrag(this)
                }
                false
            }
            binding.pickIconButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onPickIcon(pos)
            }
            binding.clearIconButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                items[pos] = items[pos].copy(iconUri = "")
                notifyItemChanged(pos)
            }
            binding.selectAudioButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onPickAudio(pos)
            }
            binding.resetDefaultButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                val item = items[pos]
                if (item.builtInResName.isEmpty()) return@setOnClickListener
                invalidKeys.remove(startKey(item.id))
                invalidKeys.remove(endKey(item.id))
                items[pos] = item.copy(audioUri = "", startTimeSeconds = 0f, endTimeSeconds = 10f)
                notifyItemChanged(pos)
            }
            binding.previewButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                onPreview(items[pos])
            }
            binding.deleteButton.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                onDelete(items[pos].id)
            }
        }

        fun bind(item: ButtonConfig, position: Int) {
            suppress = true
            val context = binding.root.context
            binding.buttonTitle.text = context.getString(R.string.button_number, position + 1)
            binding.labelEdit.setText(item.label)
            binding.startTimeEdit.setText(formatSeconds(item.startTimeSeconds))
            binding.endTimeEdit.setText(if (item.endTimeSeconds < 0f) "" else formatSeconds(item.endTimeSeconds))
            binding.audioPathText.text = audioLabel(item)
            binding.resetDefaultButton.visibility =
                if (item.builtInResName.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.clearIconButton.isEnabled = item.iconUri.isNotEmpty()
            IconLoader.bind(scope, binding.iconPreview, item.iconUri, 256, hideWhenEmpty = false)
            suppress = false
        }

        private fun updateTime(text: String, isEnd: Boolean) {
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return
            val item = items[pos]
            val key = if (isEnd) endKey(item.id) else startKey(item.id)
            val trimmed = text.trim()
            if (trimmed.isEmpty()) {
                invalidKeys.remove(key)
                items[pos] = if (isEnd) item.copy(endTimeSeconds = -1f) else item.copy(startTimeSeconds = 0f)
                return
            }
            val value = trimmed.toFloatOrNull()
            if (value == null || value < 0f) {
                invalidKeys.add(key)
                return
            }
            invalidKeys.remove(key)
            items[pos] = if (isEnd) item.copy(endTimeSeconds = value) else item.copy(startTimeSeconds = value)
        }

        private fun audioLabel(item: ButtonConfig): String {
            val context = binding.root.context
            if (item.audioUri.isNotEmpty()) {
                return displayName(item.audioUri)
            }
            if (item.builtInResName.isNotEmpty()) {
                return context.getString(R.string.using_built_in)
            }
            return context.getString(R.string.audio_not_selected)
        }

        private fun displayName(uriString: String): String {
            val context = binding.root.context
            val uri = Uri.parse(uriString)
            return try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else uri.lastPathSegment
                } ?: uri.lastPathSegment ?: context.getString(R.string.audio_selected)
            } catch (e: Exception) {
                context.getString(R.string.audio_selected)
            }
        }

        private fun afterChange(block: (String) -> Unit): TextWatcher {
            return object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppress) return
                    block(s?.toString().orEmpty())
                }
            }
        }
    }

    private fun formatSeconds(value: Float): String {
        if (value < 0f) return ""
        val asLong = value.toLong()
        return if (value == asLong.toFloat()) asLong.toString() else value.toString()
    }
}
