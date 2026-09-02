package com.example.aidocumentscanner

import android.app.Application
import com.example.aidocumentscanner.di.AppContainer

/**
 * Single process-level composition root.
 *
 * Manual DI is intentional in Phase 9: this app is small enough that explicit
 * construction is easier to audit than adding Hilt/Koin and generated components.
 */
class DocuScanApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
