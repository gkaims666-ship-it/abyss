package com.abyss.app

import android.app.Application
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.improve.HarvestScheduler

class AbyssApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Register background self-improvement (the worker itself respects the
        // on/off toggle, so this is safe to schedule unconditionally).
        runCatching { HarvestScheduler.schedule(this, 6) }
    }
}
