package com.nvzhu.bgm

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nvzhu.bgm.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repository: ConfigRepository
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var adapter: SettingsButtonAdapter
    private lateinit var touchHelper: ItemTouchHelper

    private var pickIndex = -1

    private val openAudio = registerForActivityResult(PersistableOpenDocument()) { uri ->
        applyPickedUri(uri, icon = false)
    }

    private val openIcon = registerForActivityResult(PersistableOpenDocument()) { uri ->
        applyPickedUri(uri, icon = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        repository = ConfigRepository(this)
        audioPlayer = AudioPlayer(this)
        adapter = SettingsButtonAdapter(
            scope = lifecycleScope,
            onPickAudio = { index ->
                pickIndex = index
                openAudio.launch("audio/*")
            },
            onPickIcon = { index ->
                pickIndex = index
                openIcon.launch("image/*")
            },
            onPreview = { button -> preview(button) },
            onDelete = { id -> confirmDelete(id) },
            onDrag = { holder -> touchHelper.startDrag(holder) }
        )

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.move(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                recyclerView.post { adapter.notifyDataSetChanged() }
            }
        })

        binding.buttonList.layoutManager = LinearLayoutManager(this)
        binding.buttonList.adapter = adapter
        touchHelper.attachToRecyclerView(binding.buttonList)

        binding.addButton.isEnabled = false
        binding.saveButton.isEnabled = false
        binding.addButton.setOnClickListener {
            adapter.add(
                ButtonConfig(
                    label = getString(R.string.new_button),
                    startTimeSeconds = 0f,
                    endTimeSeconds = -1f
                )
            )
            binding.buttonList.smoothScrollToPosition(adapter.itemCount - 1)
        }
        binding.saveButton.setOnClickListener { save() }
        binding.crashLogButton.setOnClickListener { showCrashLogDialog() }

        lifecycleScope.launch {
            try {
                val config = repository.load()
                when (config.columns) {
                    3 -> binding.columns3.isChecked = true
                    4 -> binding.columns4.isChecked = true
                    else -> binding.columns2.isChecked = true
                }
                when (config.buttonSize) {
                    AppConfig.SIZE_SMALL -> binding.sizeSmall.isChecked = true
                    AppConfig.SIZE_LARGE -> binding.sizeLarge.isChecked = true
                    else -> binding.sizeMedium.isChecked = true
                }
                adapter.submit(config.buttons)
                binding.addButton.isEnabled = true
                binding.saveButton.isEnabled = true
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, R.string.load_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        audioPlayer.release()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applyPickedUri(uri: Uri?, icon: Boolean) {
        if (uri == null) return
        val index = pickIndex
        if (index !in adapter.items.indices) return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.permission_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val current = adapter.items[index]
        val updated = if (icon) {
            current.copy(iconUri = uri.toString())
        } else {
            current.copy(audioUri = uri.toString())
        }
        adapter.update(index, updated)
    }

    private fun preview(button: ButtonConfig) {
        if (!adapter.timesValid()) {
            Toast.makeText(this, R.string.invalid_time, Toast.LENGTH_SHORT).show()
            return
        }
        val resolved = button.copy(
            builtInResId = if (button.builtInResId != 0) {
                button.builtInResId
            } else {
                BuiltInButtons.resolve(this, button.builtInResName)
            }
        )
        if (!resolved.hasAudio()) {
            Toast.makeText(this, R.string.no_audio_bound, Toast.LENGTH_SHORT).show()
            return
        }
        audioPlayer.play(
            config = resolved,
            onComplete = {},
            onError = {
                runOnUiThread {
                    if (!isDestroyed) {
                        Toast.makeText(this, R.string.audio_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun confirmDelete(id: String) {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_delete)
            .setPositiveButton(R.string.delete_button) { _, _ ->
                adapter.removeById(id)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save() {
        if (!adapter.timesValid()) {
            Toast.makeText(this, R.string.invalid_time, Toast.LENGTH_SHORT).show()
            return
        }
        val columns = when {
            binding.columns4.isChecked -> 4
            binding.columns3.isChecked -> 3
            else -> 2
        }
        val size = when {
            binding.sizeSmall.isChecked -> AppConfig.SIZE_SMALL
            binding.sizeLarge.isChecked -> AppConfig.SIZE_LARGE
            else -> AppConfig.SIZE_MEDIUM
        }
        val config = AppConfig(columns = columns, buttonSize = size, buttons = adapter.items.toList())
        lifecycleScope.launch {
            repository.save(config)
            Toast.makeText(this@SettingsActivity, R.string.settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showCrashLogDialog() {
        val crashLog = NvZhuApp.readCrashLog(this)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.view_crash_log)
            .setMessage(crashLog)
            .setPositiveButton(R.string.copy_crash_log) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("崩溃日志", crashLog))
                Toast.makeText(this, R.string.crash_log_copied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.clear_crash_log) { _, _ ->
                NvZhuApp.clearCrashLog(this)
                Toast.makeText(this, R.string.crash_log_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.close, null)
            .create()
        dialog.show()
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.apply {
            textSize = 10f
            setTextIsSelectable(true)
        }
    }
}
