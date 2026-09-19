package dev.glyphrotator.app.pokemon

/**
 * Un Pokémon concreto ya capturado por el usuario (una instancia, no la especie).
 */
data class CaughtPokemon(
    val uid: String,
    val speciesId: Int,
    val caughtAtMillis: Long,
    val level: Int = 1,
    val exp: Int = 0,
    val isTrainingPartner: Boolean = false,
    val nickname: String? = null,
    /**
     * Impide que evolucione aunque llegue al nivel.
     *
     * Es lo que permite tener a la vez a Charmander, Charmeleon y Charizard: sin esto, el
     * que subes de nivel se convierte en el siguiente y pierdes el estado anterior.
     */
    val isEvolutionLocked: Boolean = false,
    /**
     * Nivel al que dejas de subirlo, o null para sin tope.
     *
     * Es más fino que el bloqueo a secas: eliges el nivel exacto donde quieres conservarlo.
     * Poniendo el tope justo debajo del nivel de evolución, conservas esa forma; poniéndolo
     * más arriba, dejas que evolucione pero controlas hasta dónde crece.
     */
    val levelCap: Int? = null,
) {

    /** Si ya llegó a su tope y no debe ganar más experiencia. */
    val isAtLevelCap: Boolean get() = levelCap != null && level >= levelCap
    val species: PokemonSpecies? get() = PokemonRegistry[speciesId]
    val displayName: String get() = nickname ?: species?.name ?: "???"
}
