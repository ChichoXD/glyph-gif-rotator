package dev.glyphrotator.app.ui.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dev.glyphrotator.app.R
import dev.glyphrotator.app.glyph.DotIcons
import dev.glyphrotator.app.ui.MinimalIcons

/**
 * La pantalla que sale al colocar un widget: con qué lo decoras.
 *
 * Es una **actividad de configuración** de las de Android, no una pantalla dentro de la app,
 * porque el momento natural de elegir el dibujo es justo al colocarlo. Eso obliga a devolver
 * RESULT_OK con el id del widget: si se sale sin eso, Android cancela la colocación, y ese es
 * el comportamiento correcto —cancelar es cancelar—.
 *
 * Se construye a mano en vez de con XML porque es una rejilla de iconos generados en tiempo de
 * ejecución: un layout tendría que listar los doce a mano y habría que tocarlo cada vez que se
 * añada uno.
 */
class WidgetDecorActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            // Permiso persistente: sin esto la imagen se ve hoy y mañana el widget aparece
            // vacío, porque el permiso temporal muere con la actividad.
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            WidgetDecor.setImported(this, appWidgetId, uri)
            finishWithResult()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cancelado por defecto: si el usuario se va con el botón atrás, el widget no se coloca.
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(buildContent())
    }

    private fun buildContent(): ViewGroup {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.glyph_black))
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }

        root.addView(
            TextView(this).apply {
                setText(R.string.widget_decor_title)
                setTextColor(getColor(R.color.glyph_white))
                textSize = 20f
            }
        )
        root.addView(
            TextView(this).apply {
                setText(R.string.widget_decor_hint)
                setTextColor(getColor(R.color.glyph_grey))
                textSize = 13f
                setPadding(0, PADDING / 2, 0, PADDING)
            }
        )

        val grid = GridLayout(this).apply {
            columnCount = COLUMNS
        }
        for (icon in DotIcons.Icon.entries) {
            grid.addView(iconButton(icon))
        }
        root.addView(
            ScrollView(this).apply {
                addView(grid)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0
                ).apply { weight = 1f }
            }
        )

        root.addView(
            Button(this).apply {
                setText(R.string.widget_decor_import)
                setOnClickListener {
                    importLauncher.launch(arrayOf("image/*"))
                }
            }
        )
        root.addView(
            Button(this).apply {
                setText(R.string.widget_decor_none)
                setOnClickListener {
                    WidgetDecor.clear(this@WidgetDecorActivity, appWidgetId)
                    finishWithResult()
                }
            }
        )
        return root
    }

    private fun iconButton(icon: DotIcons.Icon): ImageView = ImageView(this).apply {
        // `this` aquí dentro es el ImageView, no la actividad: hay que nombrarla.
        setImageResource(MinimalIcons.resFor(icon))
        imageTintList = android.content.res.ColorStateList.valueOf(
            MinimalIcons.tintFor(this@WidgetDecorActivity, MinimalIcons.Variant.DEFAULT)
        )
        layoutParams = GridLayout.LayoutParams().apply {
            width = CELL
            height = CELL
            setMargins(MARGIN, MARGIN, MARGIN, MARGIN)
        }
        setPadding(MARGIN, MARGIN, MARGIN, MARGIN)
        contentDescription = icon.name
        setOnClickListener {
            WidgetDecor.setIcon(this@WidgetDecorActivity, appWidgetId, icon)
            finishWithResult()
        }
    }

    /**
     * Devuelve el id y **pinta el widget antes de salir**.
     *
     * Android no llama a onUpdate en un widget con actividad de configuración: se da por hecho
     * que lo deja pintado quien configura. Sin esto, el widget recién colocado sale en blanco
     * hasta el siguiente refresco, que puede ser media hora después.
     */
    private fun finishWithResult() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(appWidgetId)?.provider
        if (provider != null) {
            sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                    component = provider
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
                }
            )
        }
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )
        finish()
    }

    private companion object {
        const val COLUMNS = 4
        const val CELL = 200
        const val MARGIN = 12
        const val PADDING = 48
    }
}
