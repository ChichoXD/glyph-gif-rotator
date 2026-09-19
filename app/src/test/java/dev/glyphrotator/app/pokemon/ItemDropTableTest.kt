package dev.glyphrotator.app.pokemon

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemDropTableTest {

    /**
     * La regla que no se puede romper nunca: `SUPER_ROD` no sale de aquí, en ninguna de las tres
     * tablas. No evoluciona nada — `EvolutionResolver.canUseItemOn` la deja fija en `false` —
     * así que darla sería regalar un objeto inerte, que se lee como un fallo y no como un premio.
     */
    @Test
    fun `la caña super no sale nunca`() {
        for (source in ItemDropTable.Source.entries) {
            assertTrue(
                "$source no debería incluir SUPER_ROD",
                PokemonItem.SUPER_ROD !in ItemDropTable.weightsFor(source)
            )
        }
    }

    /** Con mil tiradas, los ocho objetos posibles tienen que haber salido al menos una vez. */
    @Test
    fun `las tres tablas pueden dar cualquiera de sus objetos`() {
        for (source in ItemDropTable.Source.entries) {
            val random = Random(source.ordinal.toLong())
            val seen = (1..1000).map { ItemDropTable.roll(source, random) }.toSet()
            assertEquals(
                "$source no cubrió todos sus objetos en 1000 tiradas",
                ItemDropTable.weightsFor(source).keys,
                seen
            )
        }
    }

    /**
     * Cuanto más cuesta la fuente, más favorece el Caramelo Raro sobre las piedras. Es el
     * balance pedido: capturar es lo más barato y reparte sobre todo piedras; un logro grande es
     * lo más caro y es el único que de verdad favorece el objeto más directo.
     */
    @Test
    fun `el caramelo raro pesa mas cuanto mas cuesta la fuente`() {
        fun candyShare(source: ItemDropTable.Source): Float {
            val weights = ItemDropTable.weightsFor(source)
            return weights.getValue(PokemonItem.RARE_CANDY) / weights.values.sum()
        }

        val capture = candyShare(ItemDropTable.Source.CAPTURE)
        val perfectDay = candyShare(ItemDropTable.Source.PERFECT_DAY)
        val achievement = candyShare(ItemDropTable.Source.ACHIEVEMENT)

        assertTrue("captura < día perfecto", capture < perfectDay)
        assertTrue("día perfecto < logro", perfectDay < achievement)
    }

    /** Con una sola tirada al azar del sistema no debería romperse nunca (sin seed fija). */
    @Test
    fun `sin seed fija tambien devuelve algo valido`() {
        for (source in ItemDropTable.Source.entries) {
            val item = ItemDropTable.roll(source)
            assertTrue(item in ItemDropTable.weightsFor(source))
        }
    }
}
