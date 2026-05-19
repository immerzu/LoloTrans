package com.example.translatorapp

/**
 * Fünf Bubble-Größen mit getrennten Maßen für Touchfläche, Sichtbarkeit und Icon.
 *
 * - touchDp: Größe der antippbaren WindowManager-Fläche (min. 48 dp)
 * - visualDp: Durchmesser des sichtbaren farbigen Kreises
 * - iconDp: Größe des Translate-Icons innerhalb des Kreises
 */
enum class BubbleSize(
    val touchDp: Int,
    val visualDp: Int,
    val iconDp: Int,
    val labelResId: Int
) {
    XS(48, 24, 16, R.string.bubble_size_xs),
    S(48, 32, 22, R.string.bubble_size_s),
    M(56, 40, 28, R.string.bubble_size_m),
    L(64, 48, 34, R.string.bubble_size_l),
    XL(72, 56, 40, R.string.bubble_size_xl);

    companion object {
        /** Mapping alter Namen für Rückwärtskompatibilität. */
        private val legacyNameMapping = mapOf(
            "MINI" to XS,
            "SMALL" to S,
            "MEDIUM" to M,
            "LARGE" to L,
        )

        /** Aus dem gespeicherten Namen laden (inkl. alter Namen, Standard: M). */
        fun fromName(name: String?): BubbleSize {
            if (name == null) return M
            // Direkter Treffer
            entries.find { it.name == name }?.let { return it }
            // Alter Name
            return legacyNameMapping[name] ?: M
        }

        /**
         * Rückwärtskompatibles Mapping von alten dp-Werten:
         * 48 → S, 56 → M, 64 → L, 72 → XL, sonst M.
         */
        fun fromLegacyDp(dp: Int): BubbleSize = when (dp) {
            72 -> XL
            64 -> L
            56 -> M
            48 -> S
            else -> M
        }
    }
}
