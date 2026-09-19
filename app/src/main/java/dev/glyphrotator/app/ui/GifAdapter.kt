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
            // El nombre ya no se pinta: la galería va sin etiquetas. Sigue aquí para que TalkBack
            // pueda decir cuál es cada tile, que si no serían veinte "imagen" seguidas.
            binding.imageGifPreview.contentDescription = shortName(item.displayName)
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

        /**
         * El nombre del archivo, recortado a lo que distingue un diseño de otro.
         *
         * En rejilla el hueco es de dos líneas cortas: `glyph_animation_lego_brick.gif` no cabe y
         * además todo lo que importa está al final. Se quita la extensión, se tiran los prefijos
         * que llevan todos por venir del mismo sitio, y los guiones bajos pasan a espacios para
         * que el texto pueda partir en dos líneas en vez de cortarse a mitad de palabra.
         *
         * Si al quitar los prefijos no queda nada —un archivo que se llame solo `glyph.gif`— se
         * devuelve el nombre sin extensión: es preferible un nombre feo a una celda sin etiqueta.
         */
        fun shortName(displayName: String): String {
            val withoutExtension = displayName.substringBeforeLast('.', displayName)
            val trimmed = PREFIXES.fold(withoutExtension) { name, prefix ->
                name.removePrefix(prefix)
            }
            val clean = (if (trimmed.isBlank()) withoutExtension else trimmed)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
            return clean.ifBlank { displayName }
        }

        /** Los prefijos que arrastran los archivos de la carpeta de siempre y no dicen nada. */
        val PREFIXES = listOf("glyph_animation_", "glyph_art_", "glyph_")

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<GifItem>() {
            override fun areItemsTheSame(oldItem: GifItem, newItem: GifItem) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: GifItem, newItem: GifItem) = oldItem == newItem
        }
    }
}
