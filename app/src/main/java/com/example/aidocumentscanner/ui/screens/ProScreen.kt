package com.example.aidocumentscanner.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.aidocumentscanner.DocuScanApplication
import com.example.aidocumentscanner.billing.MonetizationConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onBack: () -> Unit
) {
    val context =
        LocalContext.current

    val app =
        context.applicationContext as
            DocuScanApplication

    val billing =
        app.container.billingManager

    val state by
        billing.state.collectAsState()

    val snackbar =
        remember {
            SnackbarHostState()
        }

    val activity =
        remember(context) {
            context.findActivity()
        }

    LaunchedEffect(Unit) {
        billing.start()
        billing.refreshProductDetails()
        billing.refreshPurchases()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            billing.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbar)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DocuScan Pro",
                        fontWeight =
                            FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier =
                            Modifier.size(
                                48.dp
                            )
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding =
                PaddingValues(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(
                    16.dp
                )
        ) {
            item {
                Surface(
                    shape =
                        RoundedCornerShape(
                            24.dp
                        ),
                    color =
                        MaterialTheme
                            .colorScheme
                            .primaryContainer
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                20.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                10.dp
                            )
                    ) {
                        Icon(
                            Icons.Default
                                .WorkspacePremium,
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(
                                    44.dp
                                ),
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )

                        Text(
                            if (state.isPro) {
                                "Lifetime Pro is active"
                            } else {
                                "Unlock advanced PDF editing"
                            },
                            style =
                                MaterialTheme
                                    .typography
                                    .headlineSmall,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "One purchase. No subscription. Core scanning, Study Mode, OCR/search, PDF creation, viewing and sharing stay free."
                        )

                        if (
                            !MonetizationConfig
                                .ENABLED
                        ) {
                            Text(
                                "Pro activation is disabled in this build until the post-launch monetization gate is approved.",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Pro unlocks",
                    style =
                        MaterialTheme
                            .typography
                            .titleMedium,
                    fontWeight =
                        FontWeight.SemiBold
                )
            }

            item { BenefitCard("Merge PDFs", "Combine multiple PDF documents.") }
            item { BenefitCard("Split PDFs", "Create separate PDFs from page groups.") }
            item { BenefitCard("Edit existing PDF pages", "Remove, reorder and rotate pages in existing PDFs.") }
            item { BenefitCard("Watermark", "Add a visible watermark to a PDF copy.") }
            item { BenefitCard("Password protection", "Create a password-protected PDF copy.") }

            item {
                Card(
                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceContainerHigh
                        )
                ) {
                    Column(
                        modifier =
                            Modifier.padding(
                                16.dp
                            ),
                        verticalArrangement =
                            Arrangement.spacedBy(
                                8.dp
                            )
                    ) {
                        Text(
                            "Always free",
                            fontWeight =
                                FontWeight.SemiBold
                        )
                        FreeLine("Unlimited document scanning")
                        FreeLine("Scan crop, filters and page editing")
                        FreeLine("PDF creation, viewing and sharing")
                        FreeLine("OCR extraction and global search")
                        FreeLine("Study Mode and subject folders")
                        FreeLine("Images to PDF and PDF to images")
                        FreeLine("Rename and safe PDF optimization")
                    }
                }
            }

            item {
                if (state.isPro) {
                    Surface(
                        modifier =
                            Modifier.fillMaxWidth(),
                        shape =
                            RoundedCornerShape(
                                16.dp
                            ),
                        color =
                            MaterialTheme
                                .colorScheme
                                .secondaryContainer
                    ) {
                        Row(
                            modifier =
                                Modifier.padding(
                                    16.dp
                                ),
                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription =
                                    null
                            )
                            Spacer(
                                Modifier.width(
                                    10.dp
                                )
                            )
                            Text(
                                "Pro unlocked",
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                    }
                } else {
                    Button(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    54.dp
                                ),
                        enabled =
                            MonetizationConfig
                                .ENABLED &&
                                state
                                    .productAvailable &&
                                !state
                                    .purchasing &&
                                activity != null,
                        onClick = {
                            activity?.let(
                                billing::
                                    launchPurchase
                            )
                        }
                    ) {
                        if (state.purchasing) {
                            CircularProgressIndicator(
                                modifier =
                                    Modifier.size(
                                        20.dp
                                    ),
                                strokeWidth =
                                    2.dp
                            )
                            Spacer(
                                Modifier.width(
                                    8.dp
                                )
                            )
                            Text(
                                "Opening Google Play…"
                            )
                        } else {
                            Text(
                                buildString {
                                    append(
                                        "Unlock Lifetime Pro"
                                    )
                                    state
                                        .formattedPrice
                                        ?.let {
                                            append(" • ")
                                            append(it)
                                        }
                                }
                            )
                        }
                    }
                }
            }

            if (
                state.purchasePending
            ) {
                item {
                    Text(
                        "Payment is pending. Free features remain available and Pro unlocks only after Google Play confirms payment.",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }

            item {
                FilledTonalButton(
                    modifier =
                        Modifier.fillMaxWidth(),
                    enabled =
                        MonetizationConfig
                            .ENABLED,
                    onClick =
                        billing::
                            restorePurchases
                ) {
                    Icon(
                        Icons.Default.Restore,
                        contentDescription =
                            null
                    )
                    Spacer(
                        Modifier.width(8.dp)
                    )
                    Text(
                        "Restore purchase"
                    )
                }
            }

            item {
                Text(
                    "Purchases are processed by Google Play. DocuScan does not receive your card number or payment credentials. Buying or restoring requires Google Play; a last-known verified lifetime entitlement is cached for temporary offline use.",
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BenefitCard(
    title: String,
    detail: String
) {
    Card(
        shape =
            RoundedCornerShape(
                14.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surfaceContainerHigh
            )
    ) {
        Row(
            modifier =
                Modifier.padding(
                    14.dp
                ),
            verticalAlignment =
                Alignment.Top
        ) {
            Icon(
                Icons.Default
                    .WorkspacePremium,
                contentDescription =
                    null,
                tint =
                    MaterialTheme
                        .colorScheme
                        .primary,
                modifier =
                    Modifier.size(
                        22.dp
                    )
            )
            Spacer(
                Modifier.width(10.dp)
            )
            Column {
                Text(
                    title,
                    fontWeight =
                        FontWeight.SemiBold
                )
                Text(
                    detail,
                    style =
                        MaterialTheme
                            .typography
                            .bodySmall
                )
            }
        }
    }
}

@Composable
private fun FreeLine(
    text: String
) {
    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier =
                Modifier.size(18.dp),
            tint =
                MaterialTheme
                    .colorScheme
                    .primary
        )
        Spacer(
            Modifier.width(8.dp)
        )
        Text(text)
    }
}

private tailrec fun Context.findActivity():
    Activity? =
    when (this) {
        is Activity ->
            this

        is ContextWrapper ->
            baseContext
                .findActivity()

        else ->
            null
    }
