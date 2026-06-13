package de.lolo.lolotrans

import android.app.Application

class LoloTransApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.init(this)
    }
}

