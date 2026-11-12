package de.lolo.lolotrans

import android.content.Context
import android.graphics.Bitmap

/**
 * Liest markierten Text aus einem Bildschirmfoto (Texterkennung).
 *
 * In diesem Flavor (fdroid/github) ist keine Texterkennung enthalten; der Scanner bleibt
 * wirkungslos. Die App nutzt hier Auswahl und Zwischenablage.
 */
object ScreenTextScanner {

    /** True, wenn dieser Flavor eine Texterkennung mitbringt. */
    val isAvailable: Boolean = false

    /**
     * Sucht die markierte Stelle im Bildschirmfoto und liefert ihren Text.
     * @return erkannter Text oder null, wenn keine Markierung gefunden wurde.
     */
    suspend fun recognizeMarkedText(context: Context, screenshot: Bitmap): String? = null
}