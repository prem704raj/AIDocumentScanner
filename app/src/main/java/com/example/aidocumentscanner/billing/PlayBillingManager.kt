package com.example.aidocumentscanner.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BillingUiState(
    val connected: Boolean = false,
    val isPro: Boolean = false,
    val productAvailable: Boolean = false,
    val formattedPrice: String? = null,
    val purchasePending: Boolean = false,
    val purchasing: Boolean = false,
    val message: String? = null
)

/**
 * One application-scoped BillingClient.
 *
 * Client-only design is intentional for the first low-cost local lifetime unlock.
 * Google recommends secure-backend verification for stronger fraud protection.
 * If a backend is added later, update Privacy Policy/Data Safety before release.
 */
class PlayBillingManager(
    context: Context,
    private val entitlementStore:
        EntitlementStore
) : PurchasesUpdatedListener {

    private val appContext =
        context.applicationContext

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate
        )

    private val _state =
        MutableStateFlow(
            BillingUiState()
        )

    val state:
        StateFlow<BillingUiState> =
        _state.asStateFlow()

    @Volatile
    private var connecting =
        false

    private val billingClient:
        BillingClient =
        BillingClient
            .newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams
                    .newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

    init {
        scope.launch {
            entitlementStore
                .isPro
                .collect { owned ->
                    _state.update {
                        it.copy(
                            isPro = owned
                        )
                    }
                }
        }
    }

    fun start() {
        if (
            !MonetizationConfig.ENABLED ||
            billingClient.isReady ||
            connecting
        ) {
            return
        }

        connecting = true

        billingClient.startConnection(
            object :
                BillingClientStateListener {

                override fun onBillingSetupFinished(
                    billingResult:
                        BillingResult
                ) {
                    connecting = false

                    if (
                        billingResult
                            .responseCode ==
                        BillingClient
                            .BillingResponseCode
                            .OK
                    ) {
                        _state.update {
                            it.copy(
                                connected = true,
                                message = null
                            )
                        }

                        refreshProductDetails()
                        refreshPurchases()
                    } else {
                        _state.update {
                            it.copy(
                                connected = false,
                                message =
                                    billingMessage(
                                        billingResult
                                    )
                            )
                        }
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    _state.update {
                        it.copy(
                            connected = false
                        )
                    }
                }
            }
        )
    }

    fun refreshProductDetails() {
        if (!MonetizationConfig.ENABLED) return

        if (!billingClient.isReady) {
            start()
            return
        }

        queryProductDetails { details, offer ->
            _state.update {
                it.copy(
                    productAvailable = true,
                    formattedPrice =
                        offer.formattedPrice
                )
            }
        }
    }

    fun refreshPurchases(
        userInitiatedRestore:
            Boolean = false
    ) {
        if (!MonetizationConfig.ENABLED) return

        if (!billingClient.isReady) {
            if (userInitiatedRestore) {
                _state.update {
                    it.copy(
                        message =
                            "Connecting to Google Play…"
                    )
                }
            }
            start()
            return
        }

        billingClient.queryPurchasesAsync(
            QueryPurchasesParams
                .newBuilder()
                .setProductType(
                    BillingClient
                        .ProductType
                        .INAPP
                )
                .build()
        ) {
                billingResult,
                purchases ->

            if (
                billingResult.responseCode !=
                BillingClient
                    .BillingResponseCode
                    .OK
            ) {
                _state.update {
                    it.copy(
                        message =
                            billingMessage(
                                billingResult
                            )
                    )
                }
                return@queryPurchasesAsync
            }

            val relevant =
                purchases.filter {
                    MonetizationConfig
                        .PRO_PRODUCT_ID in
                        it.products
                }

            val purchased =
                relevant.filter {
                    it.purchaseState ==
                        Purchase
                            .PurchaseState
                            .PURCHASED
                }

            val pending =
                relevant.any {
                    it.purchaseState ==
                        Purchase
                            .PurchaseState
                            .PENDING
                }

            if (purchased.isEmpty()) {
                scope.launch {
                    entitlementStore
                        .updateFromPlay(false)

                    _state.update {
                        it.copy(
                            purchasePending =
                                pending,
                            purchasing = false,
                            message =
                                when {
                                    pending ->
                                        "Payment is pending. Pro unlocks only after Google Play confirms payment."
                                    userInitiatedRestore ->
                                        "No DocuScan Pro purchase was found for this Play account."
                                    else ->
                                        it.message
                                }
                        )
                    }
                }
            } else {
                purchased.forEach(
                    ::processPurchased
                )

                if (userInitiatedRestore) {
                    _state.update {
                        it.copy(
                            message =
                                "DocuScan Pro restored."
                        )
                    }
                }
            }
        }
    }

    fun restorePurchases() =
        refreshPurchases(
            userInitiatedRestore = true
        )

    fun launchPurchase(
        activity: Activity
    ) {
        if (!MonetizationConfig.ENABLED) {
            _state.update {
                it.copy(
                    message =
                        "DocuScan Pro is not enabled in this release."
                )
            }
            return
        }

        if (_state.value.isPro) {
            _state.update {
                it.copy(
                    message =
                        "DocuScan Pro is already active."
                )
            }
            return
        }

        if (!billingClient.isReady) {
            _state.update {
                it.copy(
                    message =
                        "Connecting to Google Play. Try again in a moment."
                )
            }
            start()
            return
        }

        _state.update {
            it.copy(
                purchasing = true,
                message = null
            )
        }

        queryProductDetails { details, offer ->
            val productParamsBuilder =
                BillingFlowParams
                    .ProductDetailsParams
                    .newBuilder()
                    .setProductDetails(
                        details
                    )

            offer.offerToken?.let { token ->
                productParamsBuilder
                    .setOfferToken(token)
            }

            val productParams =
                productParamsBuilder.build()

            val result =
                billingClient
                    .launchBillingFlow(
                        activity,
                        BillingFlowParams
                            .newBuilder()
                            .setProductDetailsParamsList(
                                listOf(
                                    productParams
                                )
                            )
                            .build()
                    )

            if (
                result.responseCode !=
                BillingClient
                    .BillingResponseCode
                    .OK
            ) {
                _state.update {
                    it.copy(
                        purchasing = false,
                        message =
                            billingMessage(
                                result
                            )
                    )
                }
            }
        }
    }

    fun clearMessage() {
        _state.update {
            it.copy(
                message = null
            )
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases:
            List<Purchase>?
    ) {
        when (
            billingResult.responseCode
        ) {
            BillingClient
                .BillingResponseCode
                .OK -> {
                if (
                    purchases.isNullOrEmpty()
                ) {
                    _state.update {
                        it.copy(
                            purchasing = false
                        )
                    }
                    return
                }

                purchases
                    .filter {
                        MonetizationConfig
                            .PRO_PRODUCT_ID in
                            it.products
                    }
                    .forEach { purchase ->
                        when (
                            purchase.purchaseState
                        ) {
                            Purchase
                                .PurchaseState
                                .PURCHASED ->
                                processPurchased(
                                    purchase
                                )

                            Purchase
                                .PurchaseState
                                .PENDING ->
                                _state.update {
                                    it.copy(
                                        purchasing =
                                            false,
                                        purchasePending =
                                            true,
                                        message =
                                            "Payment is pending. Pro will unlock after Google Play confirms payment."
                                    )
                                }

                            else ->
                                _state.update {
                                    it.copy(
                                        purchasing =
                                            false
                                    )
                                }
                        }
                    }
            }

            BillingClient
                .BillingResponseCode
                .USER_CANCELED ->
                _state.update {
                    it.copy(
                        purchasing = false,
                        message = null
                    )
                }

            BillingClient
                .BillingResponseCode
                .ITEM_ALREADY_OWNED -> {
                _state.update {
                    it.copy(
                        purchasing = false
                    )
                }
                restorePurchases()
            }

            else ->
                _state.update {
                    it.copy(
                        purchasing = false,
                        message =
                            billingMessage(
                                billingResult
                            )
                    )
                }
        }
    }

    private fun processPurchased(
        purchase: Purchase
    ) {
        if (
            MonetizationConfig
                .PRO_PRODUCT_ID !in
            purchase.products ||
            purchase.purchaseState !=
            Purchase
                .PurchaseState
                .PURCHASED
        ) {
            return
        }

        scope.launch {
            entitlementStore
                .updateFromPlay(true)

            _state.update {
                it.copy(
                    isPro = true,
                    purchasePending = false,
                    purchasing = false
                )
            }
        }

        if (purchase.isAcknowledged) return

        billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(
                    purchase.purchaseToken
                )
                .build()
        ) { result ->
            if (
                result.responseCode !=
                BillingClient
                    .BillingResponseCode
                    .OK
            ) {
                _state.update {
                    it.copy(
                        message =
                            "Pro is unlocked, but Google Play acknowledgement did not finish. Reconnect soon so DocuScan can retry."
                    )
                }
            }
        }
    }

    private fun queryProductDetails(
        onReady:
            (
                ProductDetails,
                ProductDetails
                    .OneTimePurchaseOfferDetails
            ) -> Unit
    ) {
        val product =
            QueryProductDetailsParams
                .Product
                .newBuilder()
                .setProductId(
                    MonetizationConfig
                        .PRO_PRODUCT_ID
                )
                .setProductType(
                    BillingClient
                        .ProductType
                        .INAPP
                )
                .build()

        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(
                    listOf(product)
                )
                .build()
        ) {
                billingResult,
                queryResult ->

            if (
                billingResult.responseCode !=
                BillingClient
                    .BillingResponseCode
                    .OK
            ) {
                _state.update {
                    it.copy(
                        purchasing = false,
                        productAvailable =
                            false,
                        message =
                            billingMessage(
                                billingResult
                            )
                    )
                }
                return@queryProductDetailsAsync
            }

            val details =
                queryResult
                    .productDetailsList
                    .firstOrNull {
                        it.productId ==
                            MonetizationConfig
                                .PRO_PRODUCT_ID
                    }

            val offer =
                details?.let(
                    ::chooseBuyOffer
                )

            if (
                details == null ||
                offer == null
            ) {
                _state.update {
                    it.copy(
                        purchasing = false,
                        productAvailable =
                            false,
                        message =
                            "DocuScan Pro is not available for this Play account or region yet."
                    )
                }
                return@queryProductDetailsAsync
            }

            _state.update {
                it.copy(
                    productAvailable = true,
                    formattedPrice =
                        offer.formattedPrice
                )
            }

            onReady(
                details,
                offer
            )
        }
    }

    private fun chooseBuyOffer(
        details: ProductDetails
    ):
        ProductDetails
            .OneTimePurchaseOfferDetails? {

        val eligibleBuyOffers =
            details
                .oneTimePurchaseOfferDetailsList
                ?.filter {
                    it.rentalDetails == null &&
                        it.preorderDetails == null
                }
                .orEmpty()

        return eligibleBuyOffers
            .minByOrNull {
                it.priceAmountMicros
            }
            ?: details
                .oneTimePurchaseOfferDetails
    }

    private fun billingMessage(
        result: BillingResult
    ): String =
        when (
            result.responseCode
        ) {
            BillingClient
                .BillingResponseCode
                .SERVICE_UNAVAILABLE ->
                "Google Play Billing is temporarily unavailable."

            BillingClient
                .BillingResponseCode
                .BILLING_UNAVAILABLE ->
                "Purchases are not available on this device or Play account."

            BillingClient
                .BillingResponseCode
                .ITEM_UNAVAILABLE ->
                "DocuScan Pro is not available in this region yet."

            BillingClient
                .BillingResponseCode
                .NETWORK_ERROR ->
                "Google Play could not complete the billing network request."

            else ->
                result.debugMessage
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?: "Google Play Billing error (${result.responseCode})."
        }
}
