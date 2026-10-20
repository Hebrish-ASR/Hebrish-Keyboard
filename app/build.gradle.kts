import com.android.build.api.variant.ApplicationVariant
import java.net.URI

plugins {
    id("com.android.application")
    kotlin("plugin.serialization") version "2.4.0"
    kotlin("plugin.compose") version "2.4.0"
}

// Hebrish: sherpa-onnx ships its Kotlin API and native libs as one AAR on GitHub releases (not
// Maven), downloaded at build time like in Hebrish-App. Keep the version in step with
// hebrish/android/app/build.gradle.kts.
val sherpaVersion = "1.13.8"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-$sherpaVersion.aar")
val fetchSherpaAar = tasks.register("fetchSherpaAar") {
    // Locals only: the configuration cache can't serialize references to script-level vals.
    val f = sherpaAar.asFile
    val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-$sherpaVersion.aar"
    outputs.file(f)
    doLast {
        if (!f.exists()) {
            f.parentFile.mkdirs()
            logger.lifecycle("Downloading $url")
            URI(url).toURL().openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
        }
    }
}
val hebrishCatalogueDir = layout.buildDirectory.dir("generated/hebrish-catalogue").get().asFile
val copyHebrishCatalogue = tasks.register<Copy>("copyHebrishCatalogue") {
    from(rootProject.file("hebrish/shared/models.json"))
    into(hebrishCatalogueDir)
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchSherpaAar, copyHebrishCatalogue) }

android {
    compileSdk = 37

    defaultConfig {
        applicationId = "app.hebrish.keyboard"
        minSdk = 21
        targetSdk = 37
        versionCode = 4201
        versionName = "4.2"
        ndk {
            abiFilters.clear()
            // Hebrish: arm64 only. sherpa-onnx's native libs are ~32 MB per ABI; all four would
            // add ~120 MB, and every phone the model runs well on is arm64.
            abiFilters.addAll(listOf("arm64-v8a"))
        }
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
        }
        create("nouserlib") { // same as release, but does not allow the user to provide a library
            isMinifyEnabled = true
            isShrinkResources = false
            isDebuggable = false
            isJniDebuggable = false
        }
        debug {
            // "normal" debug has minify for smaller APK to fit the GitHub 25 MB limit when zipped
            // and for better performance in case users want to install a debug APK
            isMinifyEnabled = true
            isJniDebuggable = false
            applicationIdSuffix = ".debug"
        }
        create("runTests") { // build variant for running tests on CI that skips tests known to fail
            isMinifyEnabled = false
            isJniDebuggable = false
        }
        create("debugNoMinify") { // for faster builds in IDE
            isDebuggable = true
            isMinifyEnabled = false
            isJniDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            isDefault = true
        }

        androidComponents.onVariants { variant: ApplicationVariant ->
            if (variant.buildType == "debug") {
                // got a little too big for GitHub after some dependency upgrades, so we remove the largest dictionary
                variant.androidResources.ignoreAssetsPatterns = listOf("main_ro.dict")
                variant.proguardFiles = emptyList()
                //noinspection ProguardAndroidTxtUsage we intentionally use the "normal" file here
                variant.proguardFiles.add(project.layout.buildDirectory.file(project.buildFile.parent + "/dontoptimize.pro"))
                variant.proguardFiles.add(project.layout.buildDirectory.file(project.buildFile.parent + "/proguard-rules.pro"))
            }
            variant.outputs.forEach { output ->
                if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                    output.outputFileName = "HebrishKeyboard_${defaultConfig.versionName}-${variant.buildType}.apk"
                }
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/jni/Android.mk")
        }
    }
    ndkVersion = "28.0.13004108"

    packaging {
        jniLibs {
            // shrinks APK by 3 MB, zipped size unchanged
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // see https://github.com/HeliBorg/HeliBoard/issues/477
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    namespace = "helium314.keyboard.latin"

    // Hebrish: the dictation engine (app.hebrish.core) is compiled from the Hebrish-App
    // submodule's source folder, and its model catalogue is bundled as an asset. See docs/upstream.md.
    sourceSets["main"].kotlin.srcDir("../hebrish/android/core/src/main/java")
    sourceSets["main"].assets.srcDir(hebrishCatalogueDir)
    lint {
        abortOnError = true
    }
}

dependencies {
    implementation(files(sherpaAar)) // Hebrish

    // androidx
    implementation("androidx.core:core-ktx:1.17.0") // 1.18.0 requires minSdk 23
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // compose
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.11.01")) // newer requires minSdk 23
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    "debugNoMinifyImplementation"("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("sh.calvin.reorderable:reorderable:3.1.0") // for easier re-ordering
    implementation("com.github.skydoves:colorpicker-compose:1.1.3") // for user-defined colors, newer requires minSdk 23

    // test
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:runner:1.7.0")
    testImplementation("androidx.test:core:1.7.0")
}

// ---- Hebrish: everything below is ours. Kept at the end of the file, apart from upstream's
// blocks, so HeliBoard merges never conflict on it (docs/upstream.md). ----

// Version: upstream's versionCode/versionName above stay as HeliBoard sets them; the APK gets
// "<versionName>-hebrish.<revision>" and versionCode * 100 + revision (4201 -> 420101).
// (Read by hand: in a build script `java` resolves to the Java extension, so java.util.Properties
// would need an import at the top of the file, an upstream-owned line.)
val hebrishRevision = rootProject.file("hebrish.properties").readLines()
    .first { it.startsWith("revision=") }.substringAfter("=").trim().toInt()
androidComponents.onVariants { variant ->
    val upstreamCode = android.defaultConfig.versionCode!!
    val name = "${android.defaultConfig.versionName}-hebrish.$hebrishRevision"
    variant.outputs.forEach { output ->
        output.versionCode.set(upstreamCode * 100 + hebrishRevision)
        output.versionName.set(name)
    }
}

// Signing: CI writes the keystore from secrets (same key as the Hebrish app). Without
// RELEASE_KEYSTORE the release APK is unsigned, as in upstream.
android {
    System.getenv("RELEASE_KEYSTORE")?.let { ks ->
        signingConfigs.create("hebrishRelease") {
            storeFile = file(ks)
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("hebrishRelease")
    }
}
