package dev.glyphrotator.app.pokemon

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Almacén de los sprites de Pokémon, **aparte del carrusel**.
 *
 * Los diseños que rotan en la Matrix viven en `GifRepository` y son los que el usuario añade
 * a mano. Los sprites no: son 151, se importan de golpe desde una carpeta y no deben
 * aparecer nunca en la rotación. Por eso tienen su propio sitio, una copia dentro de la app
 * indexada por número de Pokédex.
 *
 * Se copian en vez de guardar la URI de la carpeta porque el permiso del selector se pierde
 * al reiniciar y con 151 archivos no compensa arriesgarse.
 */
class SpriteLibrary(context: Context) {

    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, DIRECTORY_NAME)

    /** Cuántos sprites se copiaron y cuántos archivos se descartaron por no tener número. */
    data class ImportResult(val imported: Int, val skipped: Int)

    fun fileFor(dexNumber: Int): File? {
        val file = File(directory, fileName(dexNumber))
        if (file.isFile) return file
        // No está copiado todavía: se saca del propio APK. Así el juego funciona nada más
        // instalarlo, sin que nadie tenga que buscar los sprites por su cuenta.
        return extractFromAssets(dexNumber)
    }

    /**
     * Saca un sprite de los que vienen dentro del APK y lo deja en la carpeta de la app.
     *
     * Se copia en vez de leerlo del APK cada vez porque todo lo demás —la vista previa, la
     * captura, el widget— espera un archivo con ruta, no un flujo de assets. Copiándolo una vez,
     * el resto del código no se entera de que vino de dentro.
     *
     * Y como se copia a la misma carpeta que los importados, **reimportar sigue mandando**:
     * si el usuario mete su propia versión de un sprite, sobrescribe la del APK y esta ya no
     * vuelve a mirarse.
     */
    private fun extractFromAssets(dexNumber: Int): File? = runCatching {
        val name = fileName(dexNumber)
        if (!directory.exists()) directory.mkdirs()
        val destination = File(directory, name)
        appContext.assets.open("$ASSETS_FOLDER/$name").use { input ->
            destination.outputStream().use(input::copyTo)
        }
        destination.takeIf { it.isFile }
    }.getOrNull()

    fun uriFor(dexNumber: Int): Uri? = fileFor(dexNumber)?.let(Uri::fromFile)

    fun hasSprite(dexNumber: Int): Boolean = fileFor(dexNumber) != null

    /**
     * Cuántos hay disponibles. Cuenta también los que vienen dentro del APK aunque todavía no
     * se hayan copiado: si no, al recién instalar diría "0 de 151" teniéndolos todos.
     */
    fun importedCount(): Int {
        val copied = directory.listFiles()?.count { it.isFile } ?: 0
        val bundled = runCatching { appContext.assets.list(ASSETS_FOLDER)?.size ?: 0 }.getOrDefault(0)
        return maxOf(copied, bundled)
    }

    /**
     * Copia a la app todos los GIFs de [treeUri] cuyo nombre empiece por el número de
     * Pokédex (`0130.gif`, `130.gif`, `130-gyarados.gif`...). Reimportar sobrescribe, así que
     * corregir un sprite es cambiar el archivo y volver a importar.
     */
    fun importFrom(treeUri: Uri): ImportResult {
        val tree = DocumentFile.fromTreeUri(appContext, treeUri) ?: return ImportResult(0, 0)
        if (!directory.exists()) directory.mkdirs()

        var imported = 0
        var skipped = 0
        for (document in tree.listFiles()) {
            val name = document.name
            if (!document.isFile || name == null) continue
            val dexNumber = dexNumberFrom(name)
            if (dexNumber == null) {
                skipped++
                continue
            }
            val copied = runCatching {
                appContext.contentResolver.openInputStream(document.uri)?.use { input ->
                    File(directory, fileName(dexNumber)).outputStream().use(input::copyTo)
                }
            }.getOrNull()
            if (copied != null) imported++ else skipped++
        }
        return ImportResult(imported, skipped)
    }

    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
    }

    /**
     * El nombre de archivo de un sprite: cuatro cifras, `0001.gif`.
     *
     * **Cuatro y no tres.** Los 151 sprites que vienen dentro del APK están guardados con cuatro
     * (`assets/sprites/0001.gif`), y esto formateaba con tres. Resultado: `assets.open` no
     * encontraba ni uno solo, `fileFor` devolvía null siempre y no se veía ningún Pokémon —
     * mientras el contador seguía diciendo "151 de 151", porque contaba los archivos del APK sin
     * comprobar que se pudieran abrir. Se instalaba la app y no había sprites por ningún lado.
     */
    private fun fileName(dexNumber: Int) = "%04d.gif".format(dexNumber)

    companion object {
        const val DIRECTORY_NAME = "pokemon_sprites"

        /** Carpeta dentro del APK con los 151 sprites que vienen de fábrica. */
        const val ASSETS_FOLDER = "sprites"

        /** Número de Pokédex sacado del nombre del archivo, o null si no lo lleva delante. */
        fun dexNumberFrom(fileName: String): Int? {
            val digits = fileName.takeWhile { it.isDigit() }
            if (digits.isEmpty()) return null
            val number = digits.toIntOrNull() ?: return null
            return number.takeIf { it in 1..PokemonRegistry.all.size }
        }
    }
}
