package com.zcodeandriod.app

import android.app.Application
import com.zcodeandriod.app.core.Graph

class ZcodeAApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
