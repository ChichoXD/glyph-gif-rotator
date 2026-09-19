package dev.glyphrotator.app.pokemon

/**
 * Almacenamiento del progreso del juego. [PokemonRepository] lo implementa sobre
 * SharedPreferences; en los tests se sustituye por una versión en memoria, de modo que
 * las reglas de [PokemonGame] se puedan verificar sin depender de Android.
 */
interface PokemonStore {

    fun getCaught(): List<CaughtPokemon>
    fun addCaught(speciesId: Int, level: Int = 1): CaughtPokemon
    fun updateCaught(updated: CaughtPokemon)
    fun releaseCaught(uid: String)

    fun getPokedex(): Set<Int>
    fun registerInPokedex(speciesId: Int)

    fun getTrainingPartner(): CaughtPokemon?
    fun setTrainingPartner(uid: String?)

    fun getInventory(): Map<PokemonItem, Int>
    fun getQuantity(item: PokemonItem): Int
    fun addItem(item: PokemonItem, amount: Int = 1)
    fun consumeItem(item: PokemonItem): Boolean

    var lastTrainingTickMillis: Long
}
