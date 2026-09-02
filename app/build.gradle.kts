import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val uploadKeystorePropertiesFile =
    rootProject.file("keystore.properties")

val uploadKeystoreProperties =
    Properties().apply {
        if (uploadKeystorePropertiesFile.isFile) {
            uploadKeystorePropertiesFile
                .inputStream()
                .use(::load)
        }
    }

android {
    namespace = "com.example.aidocumentscanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.aidocumentscanner"
        minSdk = 26
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadKeystorePropertiesFile.isFile) {
            create("uploadRelease") {
                storeFile = rootProject.file(
                    requireNotNull(
                        uploadKeystoreProperties
                            .getProperty("storeFile")
                    ) {
                        "keystore.properties: storeFile is missing"
                    }
                )
                storePassword =
                    requireNotNull(
                        uploadKeystoreProperties
                            .getProperty("storePassword")
                    ) {
                        "keystore.properties: storePassword is missing"
                    }
                keyAlias =
                    requireNotNull(
                        uploadKeystoreProperties
                            .getProperty("keyAlias")
                    ) {
                        "keystore.properties: keyAlias is missing"
                    }
                keyPassword =
                    requireNotNull(
                        uploadKeystoreProperties
                            .getProperty("keyPassword")
                    ) {
                        "keystore.properties: keyPassword is missing"
                    }
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            if (uploadKeystorePropertiesFile.isFile) {
                signingConfig =
                    signingConfigs
                        .getByName("uploadRelease")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    ksp {
        arg("room.generateKotlin", "true")

        // Export Room schemas to source control so future migration changes
        // can be tested against an immutable historical schema.
        arg(
            "room.schemaLocation",
            file("$projectDir/schemas").path
        )
    }

    sourceSets {
        getByName("androidTest").assets.srcDir(
            "$projectDir/schemas"
        )
    }

    testOptions {
        animationsDisabled = true

        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.coil.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    /*
     * RELEASE BLOCKER retained from Phase 9.
     * Resolve iText AGPL/commercial licensing or replace the PDF engine
     * before a closed-source commercial release.
     */
    implementation(libs.itextpdf)

    implementation(libs.opencv)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.datastore.preferences)
    implementation(libs.play.billing)

    // ---------------------------
    // Local/JVM tests
    // ---------------------------
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // ---------------------------
    // Instrumented/device tests
    // ---------------------------
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}