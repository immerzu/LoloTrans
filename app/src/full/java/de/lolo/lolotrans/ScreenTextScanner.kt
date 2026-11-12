package de.lolo.lolotrans

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "ScreenTextScanner"

/**
 * Liest markierten Text aus einem Bildschirmfoto (ML Kit Texterkennung, on-device).
 *
 * Ablauf: die markierte (blau hinterlegte) Flaeche im Bild suchen, diesen Bereich
 * ausschneiden und den Text darin erkennen. So funktioniert das Uebersetzen auch in Apps,
 * die ihre Textauswahl nicht an Accessibility weitergeben.
 *
 * Grenze: ML Kit erkennt nur lateinische Schrift. Liefert die Spracherkennung kein
 * eindeutiges Ergebnis (z. B. kyrillischer Text), wird bewusst nichts zurueckgegeben –
 * lieber eine Fehlermeldung als ein verfaelschter Text.
 */
object ScreenTextScanner {

    val isAvailable: Boolean = true

    private const val MIN_MARK_WIDTH = 30
    private const val MIN_MARK_HEIGHT = 16
    private const val MAX_BITMAP_SIDE = 2200

    suspend fun recognizeMarkedText(context: Context, screenshot: Bitmap): String? {
        val cropped = try {
            val region = findMarkedRegion(screenshot) ?: run {
                Log.w(TAG, "keine Markierung im Bildschirmfoto gefunden")
                return null
            }
            Log.w(TAG, "Markierung gefunden: $region")
            Bitmap.createBitmap(
                screenshot,
                region.left,
                region.top,
                region.width(),
                region.height()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Zuschnitt fehlgeschlagen: ${e.message}")
            return null
        }

        val latinText = recognizeText(cropped)
        val chosen = latinText?.takeIf { hasKnownLanguage(it) }

        if (cropped !== screenshot) cropped.recycle()
        Log.w(TAG, "OCR-Ergebnis: ${chosen?.length ?: 0} Zeichen")
        return chosen
    }

    /** Prueft, ob die Spracherkennung dem Text eine konkrete Sprache zuordnen kann. */
    private suspend fun hasKnownLanguage(text: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            val identifier = LanguageIdentification.getClient()
            identifier.identifyLanguage(text.take(200))
                .addOnSuccessListener { code ->
                    identifier.close()
                    Log.w(TAG, "Spracherkennung: $code")
                    if (continuation.isActive) continuation.resume(code != "und" && code.isNotEmpty())
                }
                .addOnFailureListener { e ->
                    identifier.close()
                    Log.w(TAG, "Spracherkennung fehlgeschlagen: ${e.message}")
                    if (continuation.isActive) continuation.resume(false)
                }
        }

    /** Sucht die groesste zusammenhaengende blau hinterlegte Flaeche (die Markierung). */
    private fun findMarkedRegion(bitmap: Bitmap): Rect? {
        val scaled = scaleDown(bitmap)
        try {
            return findMarkedRegionIn(scaled, bitmap)
        } finally {
            // Die herunterskalierte Kopie wird nur fuer die Suche gebraucht.
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun findMarkedRegionIn(scaled: Bitmap, original: Bitmap): Rect? {
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var bestArea = 0
        var bestRect: Rect? = null

        for (start in 0 until w * h) {
            if (visited[start] || !isMarkedColor(pixels[start])) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true

            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = -1
            var maxY = -1
            var area = 0

            while (head < tail) {
                val index = queue[head++]
                val x = index % w
                val y = index / w
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0) pushIfMarked(index - 1, pixels, visited, queue, tail).let { if (it > tail) tail = it }
                if (x < w - 1) pushIfMarked(index + 1, pixels, visited, queue, tail).let { if (it > tail) tail = it }
                if (y > 0) pushIfMarked(index - w, pixels, visited, queue, tail).let { if (it > tail) tail = it }
                if (y < h - 1) pushIfMarked(index + w, pixels, visited, queue, tail).let { if (it > tail) tail = it }
            }

            if (area > bestArea) {
                bestArea = area
                bestRect = Rect(minX, minY, maxX + 1, maxY + 1)
            }
        }

        val rect = bestRect ?: return null
        if (bestArea < 400) return null
        if (rect.width() < MIN_MARK_WIDTH || rect.height() < MIN_MARK_HEIGHT) return null

        val sx = original.width.toFloat() / w
        val sy = original.height.toFloat() / h
        val pad = 10
        val left = (rect.left * sx).toInt().coerceAtLeast(0)
        val top = (rect.top * sy).toInt().coerceAtLeast(0)
        val right = (rect.right * sx).toInt().coerceAtMost(original.width)
        val bottom = (rect.bottom * sy).toInt().coerceAtMost(original.height)
        return Rect(
            (left - pad).coerceAtLeast(0),
            (top - pad).coerceAtLeast(0),
            (right + pad).coerceAtMost(original.width),
            (bottom + pad).coerceAtMost(original.height)
        )
    }

    /** Haengt einen markierten Nachbarn an die Warteschlange; liefert den neuen tail. */
    private fun pushIfMarked(
        index: Int,
        pixels: IntArray,
        visited: BooleanArray,
        queue: IntArray,
        tail: Int
    ): Int {
        if (visited[index]) return tail
        if (!isMarkedColor(pixels[index])) return tail
        visited[index] = true
        queue[tail] = index
        return tail + 1
    }

    /**
     * Farbe einer Textmarkierung: deutlich blau, ausreichend gesaettigt, nicht zu dunkel.
     */
    private fun isMarkedColor(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return b >= 140 && b - r >= 45 && b - g >= 18 && g >= 80
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_BITMAP_SIDE) return bitmap
        val factor = MAX_BITMAP_SIDE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt().coerceAtLeast(1),
            (bitmap.height * factor).toInt().coerceAtLeast(1),
            true
        )
    }

    private suspend fun recognizeText(bitmap: Bitmap): String? =
        suspendCancellableCoroutine { continuation ->
            val recognizer: TextRecognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    recognizer.close()
                    val text = result.text.takeIf { it.isNotBlank() }
                    if (continuation.isActive) continuation.resume(text)
                }
                .addOnFailureListener { e ->
                    recognizer.close()
                    Log.w(TAG, "Texterkennung fehlgeschlagen: ${e.message}")
                    if (continuation.isActive) continuation.resume(null)
                }
        }
}
