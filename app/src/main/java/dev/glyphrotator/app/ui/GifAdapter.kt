package dev.glyphrotator.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.glyphrotator.app.R
import dev.glyphrotator.app.data.GifItem
import dev.glyphrotator.app.databinding.ItemGifBinding

class GifAdapter(
    private val onRemove: (GifItem) -> Unit,
    private val onPreview: (GifItem) -> Unit
) : ListAdapter<GifItem, GifAdapter.GifViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GifViewHolder {
        val binding = ItemGifBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GifViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GifViewHolder, position: Int) {
        holder.bind(getItem(position), onRemove, onPreview)
    }

    class GifViewHolder(private val binding: ItemGifBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GifItem, onRemove: (GifItem) -> Unit, onPreview: (GifItem) -> Unit) {
            binding.textGifName.text = item.displayName
            try {
                binding.imageGifPreview.setImageURI(item.uri)
            } catch (e: Exception) {
                binding.imageGifPreview.setImageResource(R.drawable.ic_broken_image)
            }
            binding.buttonRemoveGif.setOnClickListener { onRemove(item) }
            // Tocar el diseño lo muestra en la Matrix, sin esperar a que salga en la rotación.
            binding.root.setOnClickListener { onPreview(item) }
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<GifItem>() {
            override fun areItemsTheSame(oldItem: GifItem, newItem: GifItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: GifItem, newItem: GifItem) = oldItem == newItem
        }
    }
}
