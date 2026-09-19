package dev.glyphrotator.app.pokemon

import dev.glyphrotator.app.R

/**
 * Reglas del juego aplicadas sobre el estado guardado: entrenar al compañero, usar objetos
 * y evolucionar. Sin dependencias de Android salvo el propio [PokemonRepository].
 *
 * Los motivos de fallo son referencias a recurso (`R.string`), que son `Int` y no arrastran
 * `Context`: la clase sigue montándose en los tests tal cual.
 */
class PokemonGame(private val repository: PokemonStore) {

    sealed class Outcome {
        data object NothingHappened : Outcome()
        data class LeveledUp(val pokemon: CaughtPokemon, val newLevel: Int) : Outcome()
        data class Evolved(val from: PokemonSpecies, val to: PokemonSpecies, val pokemon: CaughtPokemon) : Outcome()
        /**
         * Por qué no se pudo, como recurso y no como texto.
         *
         * Estaba escrito a pelo en español y se enseña en un toast, así que en inglés salía en
         * español. [arg] es el único hueco que necesitan estos mensajes —el nombre del Pokémon o
         * de la especie—; quien lo pinta decide con qué idioma se rellena.
         */
        data class Failed(
            @androidx.annotation.StringRes val reasonRes: Int,
            val arg: String? = null,
        ) : Outcome()
    }

    // =====================================================================================
    // Entrenamiento
    // =====================================================================================

    /**
     * Aplica la experiencia correspondiente a [minutesWithScreenOff] minutos de pantalla
     * apagada al compañero de entrenamiento, y evoluciona si el nivel alcanzado lo permite.
     */
    fun applyTraining(minutesWithScreenOff: Int, allowEvolution: Boolean = true): Outcome =
        awardExp(TrainingRules.expForScreenOffMinutes(minutesWithScreenOff), allowEvolution)

    /**
     * Entrega [gainedExp] directamente al compañero.
     *
     * Existe para poder ir pagando el entrenamiento **a plazos**, minuto a minuto, mientras la
     * pantalla sigue apagada. Cobrarlo todo de golpe al encender tenía un problema tonto pero
     * fatal: la evolución caía justo cuando el usuario está mirando la pantalla, que es
     * exactamente cuando no puede ver la Matrix, que está detrás.
     *
     * Con [allowEvolution] a false sube de nivel pero no cambia de forma, y la evolución queda
     * pendiente para [evolvePartnerIfDue]. Es lo que permite no evolucionar de madrugada.
     */
    fun awardExp(gainedExp: Int, allowEvolution: Boolean = true): Outcome {
        val partner = repository.getTrainingPartner() ?: return Outcome.NothingHappened
        // Al llegar a su tope deja de ganar experiencia: es lo que permite conservarlo en la
        // forma y el nivel que has elegido.
        if (partner.isAtLevelCap) return Outcome.NothingHappened

        val result = LevelCalculator.applyExp(partner.level, partner.exp, gainedExp)
            ?: return Outcome.NothingHappened

        // El tope corta en seco: se queda justo ahí, sin experiencia sobrante que lo empuje
        // al pasar de nivel en cuanto lo subas.
        val cap = partner.levelCap
        val updated = if (cap != null && result.level >= cap) {
            partner.copy(level = cap, exp = 0)
        } else {
            partner.copy(level = result.level, exp = result.exp)
        }
        repository.updateCaught(updated)

        if (!result.leveledUp) return Outcome.NothingHappened

        val evolution = if (allowEvolution) maybeEvolveByLevel(updated) else null
        return evolution ?: Outcome.LeveledUp(updated, result.level)
    }

    /**
     * Evoluciona al compañero si ya le tocaba y no se hizo en su momento.
     *
     * Se necesita porque la evolución se aplaza a propósito —de madrugada, o con la pantalla
     * encendida— y luego hay que cobrarla. Sin esto habría que esperar al siguiente nivel para
     * que se volviera a comprobar, y un Pokémon podría quedarse días sin evolucionar.
     */
    fun evolvePartnerIfDue(): Outcome.Evolved? {
        val partner = repository.getTrainingPartner() ?: return null
        return maybeEvolveByLevel(partner)
    }

    /**
     * Evoluciona por nivel tantas veces como haga falta.
     *
     * Encadena a propósito: el entrenamiento se cobra de golpe al encender la pantalla, así
     * que una noche entera puede dar doce niveles de una sentada. Evolucionando una sola vez,
     * un Caterpie que se acuesta a nivel 5 se levanta a nivel 17 convertido en Metapod y
     * atascado ahí, cuando a nivel 10 ya le tocaba Butterfree.
     *
     * El resultado que se devuelve va del punto de partida al final de la cadena, que es lo
     * que tiene sentido enseñar: "Caterpie evolucionó a Butterfree".
     */
    private fun maybeEvolveByLevel(pokemon: CaughtPokemon): Outcome.Evolved? {
        // Con el bloqueo puesto sube de nivel pero no cambia de forma: es lo que permite
        // conservar cada estado de la cadena en el equipo.
        if (pokemon.isEvolutionLocked) return null
        val from = pokemon.species ?: return null
        var current = pokemon
        var last: PokemonSpecies? = null

        // El tope es una red de seguridad: si algún día la cadena tuviera un ciclo, esto
        // evita colgar el hilo en vez de dar vueltas para siempre.
        var steps = 0
        while (steps < MAX_EVOLUTION_STEPS) {
            val to = EvolutionResolver.levelEvolutionTarget(current.speciesId, current.level) ?: break
            current = current.copy(speciesId = to.id)
            repository.updateCaught(current)
            repository.registerInPokedex(to.id)
            last = to
            steps++
        }

        val target = last ?: return null
        return Outcome.Evolved(from, target, current)
    }

    // =====================================================================================
    // Objetos
    // =====================================================================================

    /** Usa [item] sobre el Pokémon [uid]; consume el objeto solo si tuvo efecto. */
    fun useItem(item: PokemonItem, uid: String): Outcome {
        val pokemon = repository.getCaught().firstOrNull { it.uid == uid }
            ?: return Outcome.Failed(R.string.game_fail_not_in_list)

        if (repository.getQuantity(item) <= 0) {
            return Outcome.Failed(R.string.game_fail_no_item)
        }
        if (!EvolutionResolver.canUseItemOn(item, pokemon.speciesId, pokemon.level)) {
            return Outcome.Failed(R.string.game_fail_item_useless, pokemon.displayName)
        }

        return when (item) {
            PokemonItem.RARE_CANDY -> applyRareCandy(pokemon, item)
            PokemonItem.LINKING_CORD -> applyEvolutionItem(pokemon, item) {
                EvolutionResolver.tradeEvolutionTarget(it.speciesId)
            }
            else -> applyEvolutionItem(pokemon, item) {
                EvolutionResolver.stoneEvolutionTarget(it.speciesId, item)
            }
        }
    }

    private fun applyRareCandy(pokemon: CaughtPokemon, item: PokemonItem): Outcome {
        // El tope manda también aquí. Se escapaba: el caramelo subía el nivel sin mirarlo, así
        // que podía hacer evolucionar justo al que habías topado para que no evolucionara.
        if (pokemon.isAtLevelCap) {
            return Outcome.Failed(R.string.game_fail_level_capped, pokemon.displayName)
        }

        val result = LevelCalculator.applyExp(pokemon.level, pokemon.exp, LevelCalculator.EXP_PER_LEVEL)
            ?: return Outcome.Failed(R.string.game_fail_max_level)
        if (!repository.consumeItem(item)) return Outcome.Failed(R.string.game_fail_no_item)

        val updated = pokemon.copy(level = result.level, exp = result.exp)
        repository.updateCaught(updated)
        return maybeEvolveByLevel(updated) ?: Outcome.LeveledUp(updated, result.level)
    }

    private fun applyEvolutionItem(
        pokemon: CaughtPokemon,
        item: PokemonItem,
        findTarget: (CaughtPokemon) -> PokemonSpecies?
    ): Outcome {
        val from = pokemon.species ?: return Outcome.Failed(R.string.game_fail_unknown_species)
        val to = findTarget(pokemon) ?: return Outcome.Failed(R.string.game_fail_no_evolution, from.name)
        if (!repository.consumeItem(item)) return Outcome.Failed(R.string.game_fail_no_item)

        val evolved = pokemon.copy(speciesId = to.id)
        repository.updateCaught(evolved)
        repository.registerInPokedex(to.id)
        return Outcome.Evolved(from, to, evolved)
    }

    // =====================================================================================
    // Consultas de apoyo
    // =====================================================================================

    /** Objetos del inventario que harían algo sobre este Pokémon ahora mismo. */
    fun usableItemsFor(pokemon: CaughtPokemon): List<PokemonItem> =
        repository.getInventory().keys.filter {
            EvolutionResolver.canUseItemOn(it, pokemon.speciesId, pokemon.level)
        }

    fun pokedexProgress(): Pair<Int, Int> = repository.getPokedex().size to PokemonRegistry.all.size

    private companion object {
        /** Ninguna cadena de la primera generación pasa de dos saltos; sobra de margen. */
        const val MAX_EVOLUTION_STEPS = 5
    }
}
