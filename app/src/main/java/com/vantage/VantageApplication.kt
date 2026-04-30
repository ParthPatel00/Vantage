package com.vantage

import android.app.Application

class VantageApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Phase 1B: initialize LiteRT-LM engine here
        // ModelPathHelper.findModel() -> VantageEngine.initialize()
    }
}
