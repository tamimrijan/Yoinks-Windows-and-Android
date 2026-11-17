package com.tamimrijan.yoinks

import android.app.Application
import com.tamimrijan.yoinks.data.History
import com.tamimrijan.yoinks.data.Prefs
import com.tamimrijan.yoinks.engine.Engine
import com.tamimrijan.yoinks.service.DownloadService

class YoinksApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var history: History
        private set
    lateinit var engine: Engine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        history = History(this, prefs)
        engine = Engine(this, prefs, history)
        // Screenshot tests run on the PC, where the Android-only Python/FFmpeg can't load.
        if (!isRobolectric) engine.start()
        DownloadService.watch(this, engine)
    }

    companion object {
        lateinit var instance: YoinksApp
            private set
        val isRobolectric = "robolectric" == android.os.Build.FINGERPRINT
    }
}
