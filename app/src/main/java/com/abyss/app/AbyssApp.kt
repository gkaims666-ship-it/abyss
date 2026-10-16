package com.abyss.app

import android.app.Application
import com.abyss.app.core.ServiceLocator
import com.abyss.app.core.improve.HarvestScheduler
import com.abyss.app.ui.i18n.I18n
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AbyssApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Services/notifications outside Compose read I18n.current: seed it with the
        // saved UI language (defaults to the system language until this lands).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { I18n.current = I18n.resolve(ServiceLocator.settings.settings.first().language) }
        }
        // Register background self-improvement (the worker itself respects the
        // on/off toggle, so this is safe to schedule unconditionally).
        runCatching { HarvestScheduler.schedule(this, 6) }
    }
}
