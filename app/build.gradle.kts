plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.duylt.demo.axiom"
    // 37: core-ktx 1.19 requires it (its AAR metadata sets minCompileSdk 37).
    compileSdk = 37

    defaultConfig {
        applicationId = "com.duylt.demo.axiom"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // The public API the demo syncs from. A parameter of the app, not of the SDK or of any data
        // code — axiom/README.md §2 step 4: a library reading its own BuildConfig cannot be pointed at
        // staging by the app that ships it.
        buildConfigField("String", "DUMMYJSON_BASE_URL", "\"https://dummyjson.com/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    lint {
        // lifecycle's detector crashes against AGP's lint (same as in the SDK's home repo).
        disable += "NullSafeMutableLiveData"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // The SDK. Retrofit, OkHttp, Gson, WorkManager, coroutines, paging-common and koin-core come with
    // it as `api` dependencies — they are not declared again here (axiom/README.md §1).
    //
    // AXIOM_SOURCE (gradle.properties, or -PAXIOM_SOURCE=… on the command line) picks where it comes
    // from: `project` is the source module under axiom/, `jitpack` is the published artifact — the
    // same coordinate an external consumer writes, resolved from https://jitpack.io (settings.gradle.kts).
    when (val axiomSource = providers.gradleProperty("AXIOM_SOURCE").getOrElse("project")) {
        "project" -> implementation(project(":axiom"))
        "jitpack" -> {
            val (owner, repo) = providers.gradleProperty("AXIOM_GITHUB_REPO").get().split("/")
            implementation("com.github.$owner:$repo:${providers.gradleProperty("AXIOM_VERSION").get()}")
        }
        else -> error("AXIOM_SOURCE must be 'project' or 'jitpack', got '$axiomSource'")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Paging 3 on Compose: `collectAsLazyPagingItems()` over the Flow<PagingData> that
    // `com.axiom.paging.asPagingData()` produces.
    implementation(libs.androidx.paging.compose)

    // Koin on Android + Compose. `koin-core` itself is transitive from :axiom.
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)
    // Coil 3 loads network URLs only through a network fetcher; this one reuses the OkHttp Axiom brings.
    implementation(libs.coil.network.okhttp)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.koin.test)
    testImplementation(libs.okhttp3.mockwebserver)
}
