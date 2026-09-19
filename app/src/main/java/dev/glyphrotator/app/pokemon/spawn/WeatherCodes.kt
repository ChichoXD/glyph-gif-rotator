package dev.glyphrotator.app.pokemon.spawn

/**
 * Traduce el código de tiempo de Open-Meteo a lo que entiende el juego.
 *
 * Se usa Open-Meteo y no OpenWeatherMap —que es lo que usa el original— porque **no pide
 * clave ni registro**: basta con las coordenadas. Eso quita la única pega que impedía tener
 * clima real, que era depender de que cada uno se sacara su clave.
 *
 * Los códigos son el estándar WMO 4677, el mismo que devuelve la API en `weather_code`.
 */
object WeatherCodes {

    /**
     * El tiempo del juego para un código WMO. Lo desconocido se trata como despejado, que es
     * el caso neutro: sin multiplicadores raros por un código que no supimos leer.
     */
    fun toWeather(code: Int): Weather = when (code) {
        // 0 despejado; 1 mayormente despejado.
        0, 1 -> Weather.CLEAR

        // 2 parcialmente nublado, 3 cubierto, 45/48 niebla.
        2, 3, 45, 48 -> Weather.CLOUDY

        // Llovizna (51-57), lluvia (61-67), chubascos (80-82).
        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        80, 81, 82 -> Weather.RAIN

        // Nieve (71-77) y chubascos de nieve (85, 86).
        71, 73, 75, 77, 85, 86 -> Weather.SNOW

        // Tormenta, con o sin granizo.
        95, 96, 99 -> Weather.THUNDERSTORM

        else -> Weather.CLEAR
    }

    /**
     * La URL de consulta. Sin clave: solo coordenadas.
     *
     * El formato va con [java.util.Locale.US] a la fuerza: con el teléfono en español, `%.4f`
     * escribe coma decimal y la URL sale inválida — la petición fallaría siempre y el clima
     * se quedaría en despejado sin que nada avisara.
     */
    fun currentWeatherUrl(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=weather_code"
            .format(java.util.Locale.US, latitude, longitude)

    /**
     * Saca el código de la respuesta JSON, o null si no viene.
     *
     * Se lee a mano en vez de con una librería de JSON: es un único número dentro de un
     * objeto conocido, y meter una dependencia nueva por esto no compensa.
     */
    fun parseWeatherCode(json: String): Int? {
        // La respuesta trae "weather_code" dos veces: en `current_units`, donde vale el texto
        // "wmo code", y en `current`, que es el número que queremos. Hay que buscar a partir
        // del bloque `current`, o se lee el de las unidades y no sale ningún número.
        val marker = "\"weather_code\""
        val currentBlock = json.indexOf("\"current\":")
        val start = json.indexOf(marker, if (currentBlock >= 0) currentBlock else 0)
        if (start < 0) return null

        val colon = json.indexOf(':', start + marker.length)
        if (colon < 0) return null

        val number = json.drop(colon + 1)
            .dropWhile { it == ' ' }
            .takeWhile { it.isDigit() || it == '-' }

        return number.toIntOrNull()
    }
}
