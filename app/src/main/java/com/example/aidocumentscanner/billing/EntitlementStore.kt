package com.example.aidocumentscanner.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.billingEntitlementDataStore:
    DataStore<Preferences> by
    preferencesDataStore(
        name = "billing_entitlement"
    )

class EntitlementStore(
    context: Context
) {
    private val appContext =
        context.applicationContext

    private val proOwnedKey =
        booleanPreferencesKey(
            "pro_owned"
        )

    private val lastPlayCheckKey =
        longPreferencesKey(
            "last_successful_play_check"
        )

    val isPro: Flow<Boolean> =
        appContext
            .billingEntitlementDataStore
            .data
            .map {
                it[proOwnedKey]
                    ?: false
            }

    val lastSuccessfulPlayCheck:
        Flow<Long> =
        appContext
            .billingEntitlementDataStore
            .data
            .map {
                it[lastPlayCheckKey]
                    ?: 0L
            }

    suspend fun updateFromPlay(
        owned: Boolean
    ) {
        appContext
            .billingEntitlementDataStore
            .edit { prefs ->
                prefs[proOwnedKey] =
                    owned
                prefs[lastPlayCheckKey] =
                    System.currentTimeMillis()
            }
    }
}
