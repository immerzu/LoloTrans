package de.lolo.lolotrans

import android.content.Context

object AppContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun require(): Context {
        return appContext ?: error("AppContext is not initialized.")
    }
}

