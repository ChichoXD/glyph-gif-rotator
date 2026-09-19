package dev.glyphrotator.app.pokemon

import android.content.Context
import dev.glyphrotator.app.glyph.MatrixImageProcessor.RenderMode

/**
 * Con qué procesado se dibuja cada sprite en la Matrix.
 *
 * No hay un modo que gane siempre: el brillo funciona con la mayoría, pero deja rotos a los
 * Pokémon oscuros y convierte en manchas a los claros y uniformes. En vez de imponer uno a
 * los 151 —que ya se probó y estropeaba los que iban bien— cada sprite guarda el suyo y solo
 * se cambian los que hagan falta.
 *
 * Lo que no está guardado usa [RenderMode.LUMINANCE], que es como se han visto siempre.
 */
class SpriteRenderModes(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun modeFor(dexNumber: Int): RenderMode {
        val stored = prefs.getString(key(dexNumber), null) ?: return RenderMode.LUMINANCE
        return runCatching { RenderMode.valueOf(stored) }.getOrDefault(RenderMode.LUMINANCE)
    }

    fun setMode(dexNumber: Int, mode: RenderMode) {
        val editor = prefs.edit()
        // Lo que va en el modo de siempre no se guarda: así "restablecer" es volver a él y la
        // lista de excepciones se queda con los que de verdad son casos aparte.
        if (mode == RenderMode.LUMINANCE) editor.remove(key(dexNumber)) else editor.putString(key(dexNumber), mode.name)
        editor.apply()
    }

    /** El siguiente modo al girar el ajuste de un sprite. */
    fun next(mode: RenderMode): RenderMode = when (mode) {
        RenderMode.LUMINANCE -> RenderMode.SILHOUETTE
        RenderMode.SILHOUETTE -> RenderMode.SILHOUETTE_SOFT
        RenderMode.SILHOUETTE_SOFT -> RenderMode.SILHOUETTE_DETAIL
        RenderMode.SILHOUETTE_DETAIL -> RenderMode.OUTLINE
        RenderMode.OUTLINE -> RenderMode.LUMINANCE
    }

    /** Los que no van con el modo de siempre, para saber de un vistazo qué se ha tocado. */
    fun exceptions(): Map<Int, RenderMode> = prefs.all.keys
        .mapNotNull { storedKey -> storedKey.removePrefix(KEY_PREFIX).toIntOrNull() }
        .associateWith { modeFor(it) }
        .filterValues { it != RenderMode.LUMINANCE }

    /**
     * Deja puestos los modos que ya se decidieron al revisar los sprites, una sola vez.
     *
     * Es "una sola vez" a propósito: si se aplicaran en cada arranque, pisarían lo que el
     * usuario haya cambiado a mano después.
     */
    fun applyPresetsOnce() {
        if (prefs.getInt(KEY_PRESETS_VERSION, 0) >= PRESETS_VERSION) return
        val editor = prefs.edit()
        for ((dexNumber, mode) in PRESETS) editor.putString(key(dexNumber), mode.name)
        editor.putInt(KEY_PRESETS_VERSION, PRESETS_VERSION)
        editor.apply()
    }

    private fun key(dexNumber: Int) = "$KEY_PREFIX$dexNumber"

    private companion object {
        const val PREFS_NAME = "sprite_render_modes"
        const val KEY_PREFIX = "mode_"
        const val KEY_PRESETS_VERSION = "presets_version"
        const val PRESETS_VERSION = 3

        /**
         * Lo decidido tras verlos en la Matrix.
         *
         * Casi todos son claros o grises y con el brillo de siempre salían como una mancha:
         * con las divisiones talladas recuperan forma. Bellsprout es la excepción: a tope le
         * tallan los ojos y la cara queda con una mueca rara, pero sin ellas la cabeza sale
         * como una mancha blanca, así que va con las divisiones apenas marcadas. Gyarados no
         * está: con el brillo de siempre ya se ve bien.
         */
        val PRESETS: Map<Int, RenderMode> = buildMap {
            for (dexNumber in listOf(36, 38, 39, 54, 68, 74, 75, 78, 81, 82, 84, 85, 111)) {
                put(dexNumber, RenderMode.SILHOUETTE_DETAIL)
            }
            put(69, RenderMode.SILHOUETTE_SOFT)
        }
    }
}
