package dev.glyphrotator.app.service

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationFlashTest {

    private val inicio = 1_000_000L

    @Before
    @After
    fun limpiar() {
        NotificationFlash.reset()
        NotificationFlash.onChanged = null
    }

    @Test
    fun `de partida no estamos apartados`() {
        assertFalse(NotificationFlash.isActive(inicio))
    }

    @Test
    fun `una notificacion nos aparta durante el tiempo previsto`() {
        NotificationFlash.trigger(inicio)
        assertTrue(NotificationFlash.isActive(inicio))
        assertTrue(NotificationFlash.isActive(inicio + NotificationFlash.DURATION_MS - 1))
        assertFalse(NotificationFlash.isActive(inicio + NotificationFlash.DURATION_MS))
    }

    @Test
    fun `una notificacion durante otra alarga la cesion, no la recorta`() {
        NotificationFlash.trigger(inicio)
        NotificationFlash.trigger(inicio + 1_000)
        assertTrue(NotificationFlash.isActive(inicio + NotificationFlash.DURATION_MS))
        assertFalse(NotificationFlash.isActive(inicio + 1_000 + NotificationFlash.DURATION_MS))
    }

    @Test
    fun `una notificacion mas antigua no acorta la cesion en curso`() {
        NotificationFlash.trigger(inicio + 1_000)
        NotificationFlash.trigger(inicio)
        assertEquals(
            NotificationFlash.DURATION_MS + 1_000,
            NotificationFlash.remainingMs(inicio)
        )
    }

    @Test
    fun `solo se avisa al servicio al empezar la cesion, no en cada notificacion`() {
        // El servicio ya se ha apartado; volver a avisarle solo le haría repintar de más.
        var avisos = 0
        NotificationFlash.onChanged = { avisos++ }

        NotificationFlash.trigger(inicio)
        NotificationFlash.trigger(inicio + 500)
        assertEquals(1, avisos)

        NotificationFlash.trigger(inicio + NotificationFlash.DURATION_MS + 10_000)
        assertEquals(2, avisos)
    }

    @Test
    fun `lo que queda de cesion nunca es negativo`() {
        NotificationFlash.trigger(inicio)
        assertEquals(0L, NotificationFlash.remainingMs(inicio + NotificationFlash.DURATION_MS * 5))
    }
}
