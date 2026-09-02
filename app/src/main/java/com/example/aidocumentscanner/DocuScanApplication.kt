package com.example.aidocumentscanner

import android.app.Application
import android.os.StrictMode
import com.example.aidocumentscanner.di.AppContainer

class DocuScanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            enableDebugStrictMode()
        }

        container = AppContainer(this)
    }

    /**
     * Debug-only diagnostics.
     *
     * penaltyLog() is deliberate: Phase 10 wants visibility into accidental main-thread
     * disk/network work and leaked closable objects without turning every debug finding
     * into an end-user crash.
     */
    private fun enableDebugStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy
                .Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy
                .Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
    }
}
