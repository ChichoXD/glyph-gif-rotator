package dev.glyphrotator.app.achievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {

    @Test
    fun `sin nada hecho no hay ningun logro`() {
        val unlocked = AchievementCatalog.unlocked(GameSnapshot())

        assertTrue("no debería haber ninguno, hay ${unlocked.size}", unlocked.isEmpty())
    }

    @Test
    fun `los ids son unicos`() {
        val ids = AchievementCatalog.all.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
    }

    /** Un objetivo a cero dividiría entre cero al calcular la fracción de progreso. */
    @Test
    fun `todos los logros piden algo`() {
        assertTrue(AchievementCatalog.all.all { it.target > 0 })
    }

    @Test
    fun `el progreso nunca se pasa del objetivo`() {
        val enorme = GameSnapshot(
            caughtCount = 9999,
            pokedexCount = 9999,
            bestStreak = 9999,
            highestLevel = 9999,
        )

        assertTrue(AchievementCatalog.all.all { it.progress(enorme) <= it.target })
        assertTrue(AchievementCatalog.all.all { it.fraction(enorme) <= 1f })
    }

    @Test
    fun `las capturas desbloquean en escalera`() {
        val snapshot = GameSnapshot(caughtCount = 10)

        assertTrue(AchievementCatalog.all.first { it.id == "catch_1" }.isUnlocked(snapshot))
        assertTrue(AchievementCatalog.all.first { it.id == "catch_10" }.isUnlocked(snapshot))
        assertFalse(AchievementCatalog.all.first { it.id == "catch_50" }.isUnlocked(snapshot))
    }

    @Test
    fun `la pokedex completa se desbloquea con las 151`() {
        val logro = AchievementCatalog.all.first { it.id == "dex_full" }

        assertFalse(logro.isUnlocked(GameSnapshot(pokedexCount = 150)))
        assertTrue(logro.isUnlocked(GameSnapshot(pokedexCount = 151)))
    }

    @Test
    fun `los iniciales cuentan los tres`() {
        val logro = AchievementCatalog.all.first { it.id == "starters" }

        assertFalse(logro.isUnlocked(GameSnapshot(startersOwned = 2)))
        assertTrue(logro.isUnlocked(GameSnapshot(startersOwned = 3)))
    }

    @Test
    fun `el huevo cuenta los que tienes y los que ya abriste`() {
        val logro = AchievementCatalog.all.first { it.id == "egg_first" }

        assertTrue(logro.isUnlocked(GameSnapshot(eggsHeld = 1)))
        // Ya abierto también cuenta: si no, quien abriera el primero perdería el logro al
        // quedarse sin huevos, que es exactamente al revés de lo que debería pasar.
        assertTrue(logro.isUnlocked(GameSnapshot(eggsHatched = 1)))
    }

    /**
     * Guarda contra un typo en `AchievementRewards.MILESTONE_IDS`: si un id no existe en el
     * catálogo, ese logro nunca podría dar su objeto y nadie lo notaría hasta que alguien
     * llegara a desbloquearlo y viera que no pasa nada.
     */
    @Test
    fun `los ids de los logros con objeto existen todos en el catalogo`() {
        val ids = AchievementCatalog.all.map { it.id }.toSet()
        for (milestoneId in AchievementRewards.MILESTONE_IDS) {
            assertTrue("'$milestoneId' no existe en AchievementCatalog", milestoneId in ids)
        }
    }
}
