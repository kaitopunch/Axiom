package com.duylt.demo.axiom

import android.app.Application
import com.axiom.Axiom
import com.axiom.client.HttpLogging
import com.duylt.demo.axiom.data.DemoSettings
import com.duylt.demo.axiom.data.DemoTasks
import com.duylt.demo.axiom.data.demo
import com.duylt.demo.axiom.di.appModule
import com.duylt.demo.axiom.log.AxiomLogRecorder
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        // 1. Axiom first, in Application.onCreate: WorkManager may start this process only to run an
        //    AxiomWorker, and the worker finds the runtime through Axiom.get() (README §2 step 4).
        Axiom.init(this) {
            isDebug = DemoSettings.IS_DEBUG // see DemoSettings for why this is not BuildConfig.DEBUG here
            defaultStaleAfter = DemoSettings.DEFAULT_STALE_AFTER
            logger = AxiomLogRecorder
            demo(
                baseUrl = BuildConfig.DUMMYJSON_BASE_URL,
                httpLogging = if (BuildConfig.DEBUG) HttpLogging.BASIC else HttpLogging.NONE,
            )
        }

        // 2. Then DI. `axiomModule` resolves Axiom.get() lazily, but the order still documents the contract.
        startKoin {
            androidLogger()
            androidContext(this@DemoApp)
            modules(appModule)
        }

        // 3. Schedule the content tasks. `KEEP` policy: a relaunch while one is still running does not
        //    stack a second. Whether each then fetches is the freshness window's call (README §4.1).
        //    The probe tasks are deliberately not scheduled here — the Lab screen triggers them.
        val axiom = Axiom.get()
        DemoTasks.content.forEach { axiom.sync(it.key) }
    }
}
