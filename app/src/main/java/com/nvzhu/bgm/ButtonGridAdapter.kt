package com.nvzhu.bgm

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nvzhu.bgm.databinding.ButtonTileBinding
import kotlinx.coroutines.CoroutineScope

class ButtonGridAdapter(
    private val scope: CoroutineScope,
    private val onClick: (ButtonConfig) -> Unit
) : RecyclerView.Adapter<ButtonGridAdapter.Holder>() {

    val items = mutableListOf<ButtonConfig>()
    var buttonSize: String = AppConfig.SIZE_MEDIUM
    var playingId: String? = null
    var isDragging: Boolean = false

    init {
        setHasStableIds(true)
    }

    fun submit(buttons: List<ButtonConfig>, size: String, playing: String?) {
        items.clear()
        items.addAll(buttons)
        buttonSize = size
        playingId = playing
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from == to || from !in items.indices || to !in items.indices) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    override fun getItemId(position: Int): Long = items[position].id.hashCode().toLong()

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ButtonTileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val binding = holder.binding
        val context = binding.root.context
        val density = context.resources.displayMetrics.density

        val (heightDp, textSp, iconDp) = when (buttonSize) {
            AppConfig.SIZE_SMALL -> Triple(108, 13f, 36)
            AppConfig.SIZE_LARGE -> Triple(196, 16f, 72)
            else -> Triple(148, 15f, 52)
        }

        val lp = binding.root.layoutParams
        val heightPx = (heightDp * density).toInt()
        if (lp.height != heightPx) {
            lp.height = heightPx
            binding.root.layoutParams = lp
        }

        binding.label.text = item.label.ifBlank { context.getString(R.string.unnamed_button) }
        binding.label.textSize = textSp
        binding.card.setCardBackgroundColor(ButtonPalette.colorFor(item.id))

        val playing = item.id == playingId
        binding.card.strokeWidth = if (playing) (4 * density).toInt() else 0
        binding.card.strokeColor = context.getColor(R.color.button_playing)

        val iconPx = (iconDp * density).toInt()
        val iconLp = binding.icon.layoutParams
        iconLp.width = iconPx
        iconLp.height = iconPx
        binding.icon.layoutParams = iconLp
        IconLoader.bind(scope, binding.icon, item.iconUri, iconPx)

        binding.card.setOnClickListener {
            if (!isDragging) onClick(item)
        }
    }

    class Holder(val binding: ButtonTileBinding) : RecyclerView.ViewHolder(binding.root)
}
