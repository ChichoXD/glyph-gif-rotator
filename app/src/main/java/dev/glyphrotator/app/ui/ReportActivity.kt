package dev.glyphrotator.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import dev.glyphrotator.app.BuildConfig
import dev.glyphrotator.app.R

/**
 * Reportar un fallo, con los datos del móvil ya rellenados.
 *
 * Sin servidor propio a propósito. Un backend para recoger reportes es una cuenta que pagar, un
 * sitio donde se guardan datos de otras personas y una cosa más que se puede caer; para una app
 * que va a estar en GitHub, los Issues del propio repo hacen exactamente el mismo trabajo y
 * además dejan que quien reporta vea si ya está arreglado.
 *
 * Se ofrecen dos caminos porque no todo el mundo tiene cuenta de GitHub, y a quien la tiene no se
 * le puede obligar a mandar un correo.
 *
 * Los datos técnicos se añaden solos. Pedirlos a mano no funciona: la mitad de los reportes
 * llegan sin versión ni modelo, y sin eso la mayoría no se pueden ni empezar a mirar.
 */
class ReportActivity : AppCompatActivity() {

    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PADDING, PADDING, PADDING, PADDING)
        }

        root.addView(
            TextView(this).apply {
                text = getString(R.string.report_title)
                textSize = 22f
                isAllCaps = true
                letterSpacing = 0.12f
                setTextColor(getColor(R.color.glyph_white))
            }
        )
        root.addView(
            TextView(this).apply {
                text = getString(R.string.report_hint)
                textSize = 13f
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, GAP, 0, GAP)
            }
        )

        input = EditText(this).apply {
            hint = getString(R.string.report_placeholder)
            minLines = 6
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setTextColor(getColor(R.color.glyph_white))
            setHintTextColor(getColor(R.color.glyph_grey))
        }
        root.addView(input)

        root.addView(
            TextView(this).apply {
                text = getString(R.string.report_device_note, deviceInfo())
                textSize = 11f
                setTextColor(getColor(R.color.glyph_grey))
                setPadding(0, GAP, 0, GAP)
            }
        )

        root.addView(
            Button(this).apply {
                text = getString(R.string.report_github)
                setOnClickListener { sendToGithub() }
            }
        )
        root.addView(
            Button(this).apply {
                text = getString(R.string.report_email)
                setOnClickListener { sendByEmail() }
            }
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(getColor(R.color.glyph_black))
                addView(root)
            }
        )
    }

    /**
     * Modelo, versión de Android y versión de la app.
     *
     * Es lo mínimo para poder reproducir un fallo. Nada de identificadores: no hace falta saber
     * quién eres para arreglar que una animación se quede congelada.
     */
    private fun deviceInfo(): String = buildString {
        append("App ").append(BuildConfig.VERSION_NAME)
        append(" · Android ").append(Build.VERSION.RELEASE)
        append(" (SDK ").append(Build.VERSION.SDK_INT).append(')')
        append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
    }

    private fun body(): String = buildString {
        append(input.text.toString().trim())
        append("\n\n---\n")
        append(deviceInfo())
    }

    private fun sendToGithub() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            toastEmpty()
            return
        }
        // El cuerpo va en la URL para que llegue ya escrito: si hubiera que copiarlo y pegarlo
        // a mano, se perdería justo la parte técnica, que es la que sirve.
        val url = Uri.parse(ISSUE_URL).buildUpon()
            .appendQueryParameter("title", text.lineSequence().first().take(TITLE_CHARS))
            .appendQueryParameter("body", body())
            .build()

        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
            .onFailure { toast(getString(R.string.report_no_browser)) }
    }

    private fun sendByEmail() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            toastEmpty()
            return
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.report_email_subject))
            putExtra(Intent.EXTRA_TEXT, body())
        }
        runCatching { startActivity(intent) }
            .onFailure { toast(getString(R.string.report_no_email)) }
    }

    private fun toastEmpty() = toast(getString(R.string.report_empty))

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private companion object {
        /**
         * El repositorio donde caen los reportes. **Cámbialo al tuyo antes de publicar**: tal
         * cual apunta a un sitio que no existe y el botón no llevará a ninguna parte.
         */
        const val ISSUE_URL = "https://github.com/ChichoXD/glyph-gif-rotator/issues/new"

        /** El correo de respaldo, para quien no tenga cuenta de GitHub. */
        const val REPORT_EMAIL = "tu-correo@ejemplo.com"

        const val TITLE_CHARS = 70
        const val PADDING = 56
        const val GAP = 24
    }
}
