package dev.glyphrotator.app.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Iconos de pixel art dibujados como **puntos**, que es el lenguaje visual de Nothing.
 *
 * La gracia no está en el dibujo sino en cómo se pinta: cada píxel encendido sale como un punto
 * redondo separado de los demás, igual que los LEDs de la Matrix. Así un widget del escritorio y
 * la pantalla de atrás del teléfono parecen la misma cosa, que es justo lo que se busca.
 *
 * Los dibujos van como texto y no como archivos PNG a propósito: a 12x12 un icono cabe en doce
 * líneas que se leen y se editan aquí mismo, sin abrir un editor de imágenes ni cargar recursos.
 */
object DotIcons {

    /** El tamaño de la rejilla de todos los iconos. */
    const val GRID = 12

    /**
     * Los iconos disponibles. El nombre es lo que se guarda en preferencias, así que cambiarlo
     * dejaría huérfanos los widgets ya colocados.
     */
    enum class Icon(val pattern: List<String>) {
        DUMBBELL(
            listOf(
                "............",
                "............",
                ".##......##.",
                ".##......##.",
                "###......###",
                "###.####.###",
                "###.####.###",
                "###......###",
                ".##......##.",
                ".##......##.",
                "............",
                "............",
            )
        ),
        CIGARETTE(
            listOf(
                "..#....#....",
                "...#....#...",
                "..#....#....",
                "...#....#...",
                "............",
                "............",
                ".##########.",
                ".##########.",
                ".##########.",
                "............",
                "............",
                "............",
            )
        ),
        DROP(
            listOf(
                ".....##.....",
                ".....##.....",
                "....####....",
                "....####....",
                "...######...",
                "...######...",
                "..########..",
                "..########..",
                "..########..",
                "...######...",
                "....####....",
                "............",
            )
        ),
        MOON(
            listOf(
                "....####....",
                "..######....",
                ".#######....",
                ".######.....",
                ".#####......",
                ".#####......",
                ".#####......",
                ".######.....",
                ".#######....",
                "..######....",
                "....####....",
                "............",
            )
        ),
        HEART(
            listOf(
                "............",
                "..##...##...",
                ".####.####..",
                ".##########.",
                ".##########.",
                ".##########.",
                "..########..",
                "...######...",
                "....####....",
                ".....##.....",
                "............",
                "............",
            )
        ),
        FLAME(
            listOf(
                ".....##.....",
                "....###.....",
                "...####.....",
                "...#####....",
                "..######....",
                "..#######...",
                "..#######...",
                "..#######...",
                "...#####....",
                "....###.....",
                "............",
                "............",
            )
        ),
        STAR(
            listOf(
                ".....##.....",
                ".....##.....",
                "....####....",
                ".##########.",
                "..########..",
                "...######...",
                "..########..",
                ".###....###.",
                ".##......##.",
                "............",
                "............",
                "............",
            )
        ),
        BOOK(
            listOf(
                "............",
                ".##########.",
                ".#........#.",
                ".#.######.#.",
                ".#........#.",
                ".#.######.#.",
                ".#........#.",
                ".#.######.#.",
                ".#........#.",
                ".##########.",
                "............",
                "............",
            )
        ),
        CLOCK(
            listOf(
                "....####....",
                "..########..",
                ".###....###.",
                "##...#....##",
                "##...#....##",
                "##...####.##",
                "##........##",
                ".###....###.",
                "..########..",
                "....####....",
                "............",
                "............",
            )
        ),
        POKEBALL(
            listOf(
                "....####....",
                "..########..",
                ".##########.",
                "############",
                "###..##..###",
                "##..####..##",
                "###..##..###",
                "############",
                ".##########.",
                "..########..",
                "....####....",
                "............",
            )
        ),
        EGG(
            listOf(
                ".....##.....",
                "....####....",
                "...######...",
                "..########..",
                "..########..",
                ".##########.",
                ".##########.",
                ".##########.",
                "..########..",
                "...######...",
                "....####....",
                "............",
            )
        ),
        /** El caramelo raro: el cuerpo y los dos extremos retorcidos del envoltorio. */
        RARE_CANDY(
            listOf(
                "............",
                "..#......#..",
                ".###....###.",
                ".#.######.#.",
                ".##########.",
                ".##########.",
                ".##########.",
                ".#.######.#.",
                ".###....###.",
                "..#......#..",
                "............",
                "............",
            )
        ),

        /**
         * Las tres zetas del emoji de dormir, creciendo hacia arriba a la derecha.
         *
         * El tamaño distinto es lo que las hace legibles: tres zetas iguales a 12x12 se leen como
         * una mancha, y la diagonal es lo que dice "esto es sueño" y no "esto es una letra".
         */
        SLEEP_ZZZ(
            listOf(
                "......######",
                "..........##",
                "........##..",
                "......##....",
                "......######",
                "...####.....",
                ".....##.....",
                "....##......",
                "...####.....",
                "###.........",
                ".#..........",
                "###.........",
            )
        ),

        /** El aparato: carcasa, pantalla y los dos botones. */
        POKEDEX(
            listOf(
                ".##########.",
                ".#........#.",
                ".#.######.#.",
                ".#.######.#.",
                ".#.######.#.",
                ".#........#.",
                ".#.##..##.#.",
                ".#........#.",
                ".#.######.#.",
                ".##########.",
                "............",
                "............",
            )
        ),
        LEAF(
            listOf(
                "..........##",
                "........####",
                ".......#####",
                "......######",
                ".....######.",
                "....######..",
                "...######...",
                "..######....",
                ".#####......",
                ".####.......",
                ".##.........",
                "............",
            )
        );

        /** La rejilla en crudo: true donde el punto va encendido. */
        fun grid(): Array<BooleanArray> = Array(GRID) { y ->
            BooleanArray(GRID) { x -> pattern.getOrNull(y)?.getOrNull(x) == '#' }
        }
    }

    /**
     * Pinta una rejilla como puntos.
     *
     * Los apagados se dibujan también, muy tenues, y esa es la diferencia entre "un dibujo de
     * puntos" y la estética de Nothing: se ve **la rejilla entera**, como una pantalla de LEDs
     * apagada donde solo algunos están encendidos. Sin los apagados el icono flota en el vacío.
     */
    fun render(
        grid: Array<BooleanArray>,
        sizePx: Int,
        litColor: Int = 0xFFFFFFFF.toInt(),
        dimColor: Int = 0x22FFFFFF,
    ): Bitmap {
        val rows = grid.size.coerceAtLeast(1)
        val columns = grid.firstOrNull()?.size ?: 1
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cell = sizePx.toFloat() / maxOf(rows, columns)
        // El punto no llena su celda: el hueco entre puntos es lo que hace la trama.
        val radius = cell * DOT_RATIO / 2f
        val offsetX = (sizePx - cell * columns) / 2f
        val offsetY = (sizePx - cell * rows) / 2f

        for (y in 0 until rows) {
            for (x in 0 until columns) {
                paint.color = if (grid[y][x]) litColor else dimColor
                canvas.drawCircle(
                    offsetX + cell * (x + 0.5f),
                    offsetY + cell * (y + 0.5f),
                    radius,
                    paint
                )
            }
        }
        return bitmap
    }

    fun render(icon: Icon, sizePx: Int): Bitmap = render(trim(icon.grid()), sizePx)

    /**
     * Recorta las filas y columnas de fuera que están completamente apagadas.
     *
     * Los dibujos se escriben en una rejilla fija de 12x12 por comodidad al editarlos, pero casi
     * ninguno la llena: la mancuerna deja dos filas vacías arriba y dos abajo. Sin recortar, esas
     * filas se pintaban igual —como puntos apagados— y el icono salía pequeño y rodeado de un
     * marco de puntos muertos.
     *
     * Los apagados **de dentro** se conservan: son los que dan la trama. Lo que se va es solo el
     * borde que no forma parte del dibujo.
     */
    fun trim(grid: Array<BooleanArray>): Array<BooleanArray> {
        val rows = grid.indices.filter { y -> grid[y].any { it } }
        if (rows.isEmpty()) return grid

        val columns = (grid.firstOrNull()?.indices ?: return grid)
            .filter { x -> grid.any { row -> row.getOrNull(x) == true } }
        if (columns.isEmpty()) return grid

        val top = rows.first()
        val bottom = rows.last()
        val left = columns.first()
        val right = columns.last()

        return Array(bottom - top + 1) { y ->
            BooleanArray(right - left + 1) { x -> grid[top + y][left + x] }
        }
    }

    /**
     * Adivina el icono por el nombre del hábito.
     *
     * Es un atajo tonto a base de palabras sueltas, y a propósito: acertar con "gym" o "leer"
     * ahorra tener que elegir icono cada vez que apuntas algo, y cuando no acierta se queda en la
     * estrella, que no molesta. Nadie tiene que configurar nada para que se vea bien.
     */
    fun guess(name: String): Icon {
        val text = name.lowercase()
        return when {
            listOf("gym", "gimnasio", "pesas", "entren", "deporte", "correr", "caminar", "andar")
                .any { it in text } -> Icon.DUMBBELL
            listOf("fum", "tabaco", "cigarr", "vape").any { it in text } -> Icon.CIGARETTE
            listOf("leer", "lectura", "libro", "estudi").any { it in text } -> Icon.BOOK
            listOf("agua", "beber", "hidrat").any { it in text } -> Icon.DROP
            listOf("dorm", "sueño", "sueno", "acost").any { it in text } -> Icon.SLEEP_ZZZ
            listOf("medit", "calma", "respir").any { it in text } -> Icon.LEAF
            listOf("com", "diet", "azúcar", "azucar").any { it in text } -> Icon.FLAME
            listOf("uñas", "unas", "morder").any { it in text } -> Icon.HEART
            listOf("hora", "tiempo", "puntual", "movil", "móvil", "pantalla", "distrac")
                .any { it in text } -> Icon.CLOCK
            else -> Icon.STAR
        }
    }

    /**
     * Convierte una imagen cualquiera —o el primer frame de un GIF— a la misma rejilla de puntos.
     *
     * Se reduce a 12x12 y se decide punto a punto por brillo. Es una reducción brutal y es
     * justo la intención: lo que llega al widget tiene el mismo aspecto que lo dibujado a mano,
     * en vez de una foto pequeña pegada al lado de unos iconos de puntos.
     *
     * El umbral se calcula sobre la propia imagen (el punto medio entre su parte más clara y la
     * más oscura) en vez de fijarlo: con un valor fijo, una imagen clara salía toda encendida y
     * una oscura toda apagada.
     */
    fun fromBitmap(source: Bitmap, grid: Int = GRID): Array<BooleanArray> {
        val scaled = Bitmap.createScaledBitmap(source, grid, grid, true)
        val pixels = IntArray(grid * grid)
        scaled.getPixels(pixels, 0, grid, 0, 0, grid, grid)

        val luminance = IntArray(pixels.size) { index ->
            val pixel = pixels[index]
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < ALPHA_FLOOR) return@IntArray 0
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            (red * 30 + green * 59 + blue * 11) / 100
        }

        val darkest = luminance.min()
        val brightest = luminance.max()
        val threshold = (darkest + brightest) / 2

        // Una imagen plana no tiene nada que separar: se deja apagada en vez de sacar ruido.
        if (brightest - darkest < MIN_RANGE) return Array(grid) { BooleanArray(grid) }

        return Array(grid) { y -> BooleanArray(grid) { x -> luminance[y * grid + x] > threshold } }
    }

    /** Cuánto del ancho de su celda ocupa cada punto. */
    private const val DOT_RATIO = 0.72f

    /** Por debajo de esta opacidad, un píxel cuenta como fondo. */
    private const val ALPHA_FLOOR = 40

    /** Diferencia mínima entre lo más claro y lo más oscuro para que haya dibujo. */
    private const val MIN_RANGE = 25
}
