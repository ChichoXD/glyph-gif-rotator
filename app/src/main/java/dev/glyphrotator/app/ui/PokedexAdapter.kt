package dev.glyphrotator.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import dev.glyphrotator.app.R
import dev.glyphrotator.app.databinding.ItemPokedexBinding
import dev.glyphrotator.app.pokemon.PokemonRegistry
import dev.glyphrotator.app.pokemon.SpriteLibrary
import pl.droidsonroids.gif.GifDrawable

/**
 * La Pokédex entera: las 151, registradas o no.
 *
 * Se pintan todas siempre. Enseñar solo las que llevas convierte la pantalla en una lista corta
 * que no dice nada; enseñar los huecos convierte la misma pantalla en el mapa de lo que falta,
 * que es lo que hace volver. Es la misma decisión que con los logros pendientes.
 *
 * Las que no están registradas no llevan silueta oscurecida sino el círculo apagado y `???`. No
 * hay siluetas aparte de los sprites, y oscurecer el GIF dejaría adivinar la forma — con lo que
 * el hueco dejaría de ser un hueco.
 */
class PokedexAdapter(
    private val library: SpriteLibrary,
    /** Qué se toca al pulsar una registrada; en las demás no pasa nada. */
    private val onSeen: (Int) -> Unit,
) : RecyclerView.Adapter<PokedexAdapter.ViewHolder>() {

    /** Los números de Pokédex ya registrados. */
    private var seen: Set<Int> = emptySet()

    class ViewHolder(val binding: ItemPokedexBinding) : RecyclerView.ViewHolder(binding.root)

    fun submitSeen(species: Set<Int>) {
        seen = species
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = PokemonRegistry.all.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPokedexBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dexNumber = position + 1
        val context = holder.binding.root.context
        val registrada = dexNumber in seen

        holder.binding.textDexNumber.text =
            context.getString(R.string.pokemon_dex_number, dexNumber)

        if (!registrada) {
            holder.binding.imageDexSprite.setImageDrawable(null)
            holder.binding.textDexName.text = context.getString(R.string.pokedex_unknown)
            holder.binding.textDexName.setTextColor(context.getColor(R.color.glyph_grey))
            holder.binding.root.setOnClickListener(null)
            holder.binding.root.isClickable = false
            return
        }

        holder.binding.textDexName.text =
            PokemonRegistry[dexNumber]?.displayName.orEmpty()
        holder.binding.textDexName.setTextColor(context.getColor(R.color.glyph_white))

        // Si el sprite no se puede leer se queda el círculo vacío en vez de tirar la lista abajo.
        val file = library.fileFor(dexNumber)
        val drawable = file?.let { runCatching { GifDrawable(it) }.getOrNull() }
        holder.binding.imageDexSprite.setImageDrawable(drawable)

        holder.binding.root.isClickable = true
        holder.binding.root.setOnClickListener { onSeen(dexNumber) }
    }
}
