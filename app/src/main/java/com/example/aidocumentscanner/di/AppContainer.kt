package com.example.aidocumentscanner.di

import android.content.Context
import com.example.aidocumentscanner.billing.EntitlementStore
import com.example.aidocumentscanner.billing.PlayBillingManager
import com.example.aidocumentscanner.data.AppDatabase
import com.example.aidocumentscanner.data.DocumentRepository
import com.example.aidocumentscanner.domain.search.SearchDocumentsUseCase
import com.example.aidocumentscanner.storage.DocumentFileStore

/**
 * Manual application dependency container.
 *
 * Rules:
 * - applicationContext only;
 * - no Activity/NavController/Composable references;
 * - database constructed once;
 * - UI receives ViewModels/use-cases/repositories, not DAOs.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(appContext)
    }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            documentDao = database.documentDao(),
            folderDao = database.folderDao()
        )
    }

    val documentFileStore: DocumentFileStore by lazy {
        DocumentFileStore()
    }

    val entitlementStore: EntitlementStore by lazy {
        EntitlementStore(appContext)
    }

    val billingManager: PlayBillingManager by lazy {
        PlayBillingManager(
            context = appContext,
            entitlementStore = entitlementStore
        )
    }

    val searchDocumentsUseCase: SearchDocumentsUseCase by lazy {
        SearchDocumentsUseCase(
            context = appContext,
            repository = documentRepository
        )
    }
}
