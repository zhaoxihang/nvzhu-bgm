package com.nvzhu.bgm

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.nvzhu.bgm.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ConfigRepository
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var adapter: ButtonGridAdapter

    private var appConfig = AppConfig()
    private var playingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ConfigRepository(this)
        audioPlayer = AudioPlayer(this)
        adapter = ButtonGridAdapter(lifecycleScope) { button ->
            onButtonClick(button)
        }

        binding.buttonList.adapter = adapter
        binding.buttonList.layoutManager = GridLayoutManager(this, 2)
        ItemTouchHelper(dragCallback).attachToRecyclerView(binding.buttonList)

        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onPause() {
        super.onPause()
        stopPlayback()
    }

    override fun onDestroy() {
        audioPlayer.release()
        super.onDestroy()
    }

    private fun reload() {
        lifecycleScope.launch {
            appConfig = repository.load()
            showConfig()
        }
    }

    private fun showConfig() {
        val columns = appConfig.columns.coerceIn(2, 4)
        val layoutManager = binding.buttonList.layoutManager as? GridLayoutManager
        if (layoutManager == null || layoutManager.spanCount != columns) {
            binding.buttonList.layoutManager = GridLayoutManager(this, columns)
        }
        adapter.submit(appConfig.buttons, appConfig.buttonSize, playingId)
        binding.emptyText.visibility = if (appConfig.buttons.isEmpty()) View.VISIBLE else View.GONE
        binding.hintText.visibility = if (appConfig.buttons.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun onButtonClick(button: ButtonConfig) {
        if (!button.hasAudio()) {
            Toast.makeText(this, R.string.no_audio_bound, Toast.LENGTH_SHORT).show()
            return
        }

        if (playingId == button.id && audioPlayer.isPlaying()) {
            stopPlayback()
            return
        }

        playingId = button.id
        adapter.playingId = button.id
        adapter.notifyDataSetChanged()

        audioPlayer.play(
            config = button,
            onComplete = {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    if (playingId == button.id) {
                        playingId = null
                        adapter.playingId = null
                        adapter.notifyDataSetChanged()
                    }
                }
            },
            onError = {
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    Toast.makeText(this, R.string.audio_error, Toast.LENGTH_SHORT).show()
                    if (playingId == button.id) {
                        playingId = null
                        adapter.playingId = null
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        )
    }

    private fun stopPlayback() {
        audioPlayer.stop()
        if (playingId != null) {
            playingId = null
            adapter.playingId = null
            if (adapter.itemCount > 0) {
                adapter.notifyDataSetChanged()
            }
        }
    }

    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            adapter.move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            adapter.isDragging = actionState == ItemTouchHelper.ACTION_STATE_DRAG
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            adapter.isDragging = false
            val ordered = adapter.items.toList()
            appConfig = appConfig.copy(buttons = ordered)
            lifecycleScope.launch {
                repository.save(appConfig)
            }
        }
    }
}
