package dev.glyphrotator.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.glyphrotator.app.R
import dev.glyphrotator.app.databinding.ItemPokemonBinding
import androidx.core.view.isVisible
import dev.glyphrotator.app.pokemon.CaughtPokemon
import dev.glyphrotator.app.pokemon.SpriteLibrary
import pl.droidsonroids.gif.GifDrawable
import dev.glyphrotator.app.pokemon.LevelCalculator

class PokemonAdapter(
    private val onTrain: (CaughtPokemon) -> Unit,
    private val onUseItem: (CaughtPokemon) -> Unit,
    /** Tocar la ficha suena su cry. */
    private val onCry: (CaughtPokemon) -> Unit,
    /** Elegir hasta qué nivel sube. */
    private val onLevelCap: (CaughtPokemon) -> Unit,
    /** Para sacar el sprite animado de cada especie. */
    private val library: SpriteLibrary
) : ListAdapter<CaughtPokemon, PokemonAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemPokemonBinding) : RecyclerView.ViewHolder(binding.root)

    /**
     * El sprite animado del Pokémon en su ficha, tal cual está el GIF: aquí hay sitio de
     * sobra, así que no se le aplica el procesado de la Matrix, que es solo para 25x25.
     *
     * Si esa especie no está importada, el hueco se oculta y la ficha queda como antes.
     */
    private fun showSprite(holder: ViewHolder, speciesId: Int) {
        val image = holder.binding.imageSprite
        val card = holder.binding.cardSprite
        val file = library.fileFor(speciesId)
        if (file == null) {
            image.setImageDrawable(null)
            card.isVisible = false
            return
        }
        card.isVisible = true
        // Si falla la decodificación se deja el hueco vacío en vez de tirar la lista abajo.
        val drawable = runCatching { GifDrawable(file) }.getOrNull()
        if (drawable == null) {
            image.setImageDrawable(null)
            card.isVisible = false
        } else {
            image.setImageDrawable(drawable)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemPokemonBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pokemon = getItem(position)
        val species = pokemon.species
        val context = holder.binding.root.context

        // Identidad arriba: número, nombre y tipos, cada uno con su peso. Antes iban los tres
        // mezclados en dos líneas y el nivel colgando a la derecha.
        holder.binding.textPokemonDex.text =
            context.getString(R.string.pokemon_dex_number, pokemon.speciesId)
        holder.binding.textPokemonName.text = pokemon.displayName
        holder.binding.textPokemonTypes.text =
            listOfNotNull(species?.type1?.name, species?.type2?.name).joinToString(" / ")

        // Lectura debajo: etiqueta a la izquierda, valor a la derecha. Los ceros por delante no
        // son un capricho: mantienen el ancho fijo, y así la columna no baila al subir de nivel.
        holder.binding.textPokemonLevel.text =
            context.getString(R.string.pokemon_level_value, pokemon.level)
        holder.binding.textPokemonExp.text = context.getString(
            R.string.pokemon_exp_value,
            pokemon.exp,
            LevelCalculator.EXP_PER_LEVEL
        )

        holder.binding.segmentExp.fraction =
            LevelCalculator.progressFraction(pokemon.level, pokemon.exp)
        // El rojo solo aquí, y solo en el que está entrenando: marca dónde entra la experiencia.
        holder.binding.segmentExp.isActive = pokemon.isTrainingPartner

        holder.binding.buttonTrain.text = context.getString(
            if (pokemon.isTrainingPartner) R.string.pokemon_partner_current else R.string.pokemon_set_partner
        )
        holder.binding.buttonTrain.isEnabled = !pokemon.isTrainingPartner
        holder.binding.buttonTrain.setOnClickListener { onTrain(pokemon) }
        holder.binding.buttonUseItem.setOnClickListener { onUseItem(pokemon) }
        holder.binding.root.setOnClickListener { onCry(pokemon) }

        // El texto lleva el tope puesto: así se ve de un vistazo cuál está limitado y a qué
        // nivel, sin tener que abrir nada.
        holder.binding.buttonLevelCap.text = pokemon.levelCap?.let {
            context.getString(R.string.pokemon_level_cap_set, it)
        } ?: context.getString(R.string.pokemon_level_cap)
        holder.binding.buttonLevelCap.setOnClickListener { onLevelCap(pokemon) }

        showSprite(holder, pokemon.speciesId)
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<CaughtPokemon>() {
            override fun areItemsTheSame(oldItem: CaughtPokemon, newItem: CaughtPokemon) =
                oldItem.uid == newItem.uid

            override fun areContentsTheSame(oldItem: CaughtPokemon, newItem: CaughtPokemon) =
                oldItem == newItem
        }
    }
}
