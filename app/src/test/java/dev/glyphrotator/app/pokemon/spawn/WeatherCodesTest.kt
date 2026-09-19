package dev.glyphrotator.app.pokemon.spawn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La traducción del código de Open-Meteo al tiempo del juego, y la lectura de su respuesta.
 *
 * Es lo único de todo el clima que se puede comprobar sin red, y también donde es más fácil
 * equivocarse: son cuarenta y tantos códigos repartidos en cinco categorías.
 */
class WeatherCodesTest {

    @Test
    fun `despejado y mayormente despejado`() {
        assertEquals(Weather.CLEAR, WeatherCodes.toWeather(0))
        assertEquals(Weather.CLEAR, WeatherCodes.toWeather(1))
    }

    @Test
    fun `nubes y niebla cuentan como nublado`() {
        assertEquals(Weather.CLOUDY, WeatherCodes.toWeather(2))
        assertEquals(Weather.CLOUDY, WeatherCodes.toWeather(3))
        assertEquals(Weather.CLOUDY, WeatherCodes.toWeather(45))
        assertEquals(Weather.CLOUDY, WeatherCodes.toWeather(48))
    }

    @Test
    fun `llovizna, lluvia y chubascos son lluvia`() {
        // Los tres rangos van juntos: para el juego, mojarse es mojarse.
        for (code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82)) {
            assertEquals("código $code", Weather.RAIN, WeatherCodes.toWeather(code))
        }
    }

    @Test
    fun `la nieve incluye los chubascos de nieve`() {
        for (code in listOf(71, 73, 75, 77, 85, 86)) {
            assertEquals("código $code", Weather.SNOW, WeatherCodes.toWeather(code))
        }
    }

    @Test
    fun `la tormenta incluye la de granizo`() {
        assertEquals(Weather.THUNDERSTORM, WeatherCodes.toWeather(95))
        assertEquals(Weather.THUNDERSTORM, WeatherCodes.toWeather(96))
        assertEquals(Weather.THUNDERSTORM, WeatherCodes.toWeather(99))
    }

    @Test
    fun `un codigo desconocido no rompe nada`() {
        // Despejado es el caso neutro: sin multiplicadores raros por algo que no supimos leer.
        assertEquals(Weather.CLEAR, WeatherCodes.toWeather(999))
        assertEquals(Weather.CLEAR, WeatherCodes.toWeather(-1))
    }

    @Test
    fun `la URL no lleva clave, solo coordenadas`() {
        // Es justo lo que hace viable el clima: Open-Meteo no exige registrarse.
        val url = WeatherCodes.currentWeatherUrl(40.4168, -3.7038)
        assertTrue(url.startsWith("https://api.open-meteo.com/"))
        assertTrue(url.contains("latitude=40.4168"))
        assertTrue(url.contains("longitude=-3.7038"))
        assertTrue("no debería llevar clave", !url.contains("appid") && !url.contains("key"))
    }

    @Test
    fun `lee el codigo de una respuesta real`() {
        val json = """
            {"latitude":40.42,"longitude":-3.70,"current_units":{"weather_code":"wmo code"},
             "current":{"time":"2026-08-09T12:00","interval":900,"weather_code":61}}
        """.trimIndent()
        assertEquals(61, WeatherCodes.parseWeatherCode(json))
    }

    @Test
    fun `lee el codigo con espacios de por medio`() {
        assertEquals(3, WeatherCodes.parseWeatherCode("""{"weather_code" :   3 }"""))
    }

    @Test
    fun `sin el campo devuelve null en vez de inventarse un valor`() {
        assertNull(WeatherCodes.parseWeatherCode("""{"current":{"time":"2026-08-09T12:00"}}"""))
        assertNull(WeatherCodes.parseWeatherCode(""))
        assertNull(WeatherCodes.parseWeatherCode("no es json"))
    }
}
