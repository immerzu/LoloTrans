package de.lolo.lolotrans

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log

private const val PROCESS_TEXT_TAG = "ProcessTextTranslate"

class ProcessTextTranslateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
            ?.toString()
            ?.trim()
            ?.take(ClipboardTranslateActivity.MAX_TRANSLATE_TEXT_LENGTH)
            .orEmpty()

        Log.d(PROCESS_TEXT_TAG, "PROCESS_TEXT length=${selectedText.length}")

        if (selectedText.isBlank()) {
            finish()
            return
        }

        startActivity(Intent(this, ClipboardTranslateActivity::class.java).apply {
            putExtra(ClipboardTranslateActivity.EXTRA_TEXT_TO_TRANSLATE, selectedText)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        })
        finish()
    }
}
