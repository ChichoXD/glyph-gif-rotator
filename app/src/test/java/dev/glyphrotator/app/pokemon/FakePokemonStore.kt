package dev.glyphrotator.app.pokemon

import java.util.UUID

/** Implementación en memoria de [PokemonStore] para probar las reglas sin Android. */
class FakePokemonStore : PokemonStore {

    private val caught = mutableListOf<CaughtPokemon>()
    private val pokedex = mutableSetOf<Int>()
    private val inventory = mutableMapOf<PokemonItem, Int>()
    override var lastTrainingTickMillis: Long = 0L

    override fun getCaught(): List<CaughtPokemon> = caught.toList()

    override fun addCaught(speciesId: Int, level: Int): CaughtPokemon {
        val pokemon = CaughtPokemon(
            uid = UUID.randomUUID().toString(),
            speciesId = speciesId,
            caughtAtMillis = 0L,
            level = level
        )
        caught += pokemon
        pokedex += speciesId
        return pokemon
    }

    override fun updateCaught(updated: CaughtPokemon) {
        val index = caught.indexOfFirst { it.uid == updated.uid }
        if (index >= 0) caught[index] = updated
    }

    override fun releaseCaught(uid: String) {
        caught.removeAll { it.uid == uid }
    }

    override fun getPokedex(): Set<Int> = pokedex.toSet()

    override fun registerInPokedex(speciesId: Int) {
        pokedex += speciesId
    }

    override fun getTrainingPartner(): CaughtPokemon? = caught.firstOrNull { it.isTrainingPartner }

    override fun setTrainingPartner(uid: String?) {
        caught.replaceAll { it.copy(isTrainingPartner = it.uid == uid) }
    }

    override fun getInventory(): Map<PokemonItem, Int> = inventory.filterValues { it > 0 }

    override fun getQuantity(item: PokemonItem): Int = inventory[item] ?: 0

    override fun addItem(item: PokemonItem, amount: Int) {
        inventory[item] = (getQuantity(item) + amount).coerceAtLeast(0)
    }

    override fun consumeItem(item: PokemonItem): Boolean {
        val quantity = getQuantity(item)
        if (quantity <= 0) return false
        inventory[item] = quantity - 1
        return true
    }
}
