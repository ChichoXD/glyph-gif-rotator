package dev.glyphrotator.app.glyph

/**
 * Una tipografía de puntos, 5 de ancho por 8 de alto, dibujada a mano.
 *
 * **Por qué a mano y no una fuente descargada.** El proyecto ya habla en puntos: los iconos de
 * [DotIcons] y, sobre todo, la Matrix de atrás, que literalmente es una rejilla de LEDs. Meter
 * una fuente de puntos de fuera sería otro dibujo más que *se parece* a eso; escribirla aquí
 * hace que los títulos de la app estén hechos con el mismo material que la pantalla trasera. Y
 * de paso evita el problema de la licencia: esto no es de nadie.
 *
 * La fila 0 se reserva para las tildes y va vacía en casi todas las letras. Cuesta una fila de
 * puntos apagados encima de cada título, que no molesta —forma parte de la rejilla— y evita
 * tener que aplastar la Ó para que le quepa el acento.
 */
object DotFont {

    const val WIDTH = 5
    const val HEIGHT = 8

    private val VACIA = listOf(".....", ".....", ".....", ".....", ".....", ".....", ".....", ".....")

    private val GLYPHS_A = letra(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#")
    private val GLYPHS_E = letra("#####", "#....", "#....", "####.", "#....", "#....", "#####")
    private val GLYPHS_I = letra("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####")
    private val GLYPHS_O = letra(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
    private val GLYPHS_U = letra("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.")
    private val GLYPHS_N = letra("#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#")

    private val GLYPHS: Map<Char, List<String>> = buildMap {
        put(' ', VACIA)

        put('A', letra(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"))
        put('B', letra("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."))
        put('C', letra(".####", "#....", "#....", "#....", "#....", "#....", ".####"))
        put('D', letra("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."))
        put('E', letra("#####", "#....", "#....", "####.", "#....", "#....", "#####"))
        put('F', letra("#####", "#....", "#....", "####.", "#....", "#....", "#...."))
        put('G', letra(".###.", "#...#", "#....", "#..##", "#...#", "#...#", ".###."))
        put('H', letra("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"))
        put('I', letra("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"))
        put('J', letra("....#", "....#", "....#", "....#", "#...#", "#...#", ".###."))
        put('K', letra("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"))
        put('L', letra("#....", "#....", "#....", "#....", "#....", "#....", "#####"))
        put('M', letra("#...#", "##.##", "#.#.#", "#...#", "#...#", "#...#", "#...#"))
        put('N', letra("#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"))
        put('O', letra(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."))
        put('P', letra("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."))
        put('Q', letra(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"))
        put('R', letra("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"))
        put('S', letra(".####", "#....", "#....", ".###.", "....#", "....#", "####."))
        put('T', letra("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."))
        put('U', letra("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."))
        put('V', letra("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."))
        put('W', letra("#...#", "#...#", "#...#", "#...#", "#.#.#", "##.##", "#...#"))
        put('X', letra("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"))
        put('Y', letra("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."))
        put('Z', letra("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"))

        put('0', letra(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."))
        put('1', letra("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."))
        put('2', letra(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"))
        put('3', letra("####.", "....#", "....#", ".###.", "....#", "....#", "####."))
        put('4', letra("#..#.", "#..#.", "#..#.", "#####", "...#.", "...#.", "...#."))
        put('5', letra("#####", "#....", "####.", "....#", "....#", "#...#", ".###."))
        put('6', letra(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."))
        put('7', letra("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."))
        put('8', letra(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."))
        put('9', letra(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."))

        // Con tilde: la fila de arriba, que en el resto va vacía, aquí se usa.
        put('Á', conTilde("..#..", GLYPHS_A))
        put('É', conTilde("..#..", GLYPHS_E))
        put('Í', conTilde("..#..", GLYPHS_I))
        put('Ó', conTilde("..#..", GLYPHS_O))
        put('Ú', conTilde("..#..", GLYPHS_U))
        put('Ñ', conTilde(".###.", GLYPHS_N))

        put('.', letra(".....", ".....", ".....", ".....", ".....", ".....", "..#.."))
        put(',', letra(".....", ".....", ".....", ".....", ".....", "..#..", ".#..."))
        put(':', letra(".....", "..#..", ".....", ".....", ".....", "..#..", "....."))
        put('-', letra(".....", ".....", ".....", "#####", ".....", ".....", "....."))
        put('/', letra("....#", "....#", "...#.", "..#..", ".#...", "#....", "#...."))
        put('#', letra(".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#."))
        put('?', letra(".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#.."))
        put('!', letra("..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#.."))
        put('%', letra("##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##"))
        put('·', letra(".....", ".....", ".....", "..#..", ".....", ".....", "....."))
        put('(', letra("...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#."))
        put(')', letra(".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#..."))
    }

    /** Siete filas de dibujo, más la de arriba vacía para la tilde que no lleva. */
    private fun letra(vararg filas: String): List<String> = listOf(".....") + filas.toList()

    private fun conTilde(tilde: String, base: List<String>): List<String> =
        listOf(tilde) + base.drop(1)

    /**
     * El texto convertido en rejilla de puntos, listo para [DotIcons.render].
     *
     * Entre letra y letra se deja una columna, que es lo que hace que se lean como caracteres
     * sueltos de un display y no como una mancha continua. Lo que no esté en la fuente se
     * traduce a un espacio: más vale un hueco que un símbolo raro en mitad de un título.
     */
    fun grid(text: String, spacing: Int = 1): Array<BooleanArray> {
        val letras = text.uppercase().map { GLYPHS[it] ?: VACIA }
        if (letras.isEmpty()) return Array(HEIGHT) { BooleanArray(0) }

        val ancho = letras.size * WIDTH + (letras.size - 1) * spacing
        return Array(HEIGHT) { y ->
            BooleanArray(ancho) { x ->
                val bloque = x / (WIDTH + spacing)
                val dentro = x % (WIDTH + spacing)
                if (dentro >= WIDTH) false
                else letras.getOrNull(bloque)?.getOrNull(y)?.getOrNull(dentro) == '#'
            }
        }
    }
}
