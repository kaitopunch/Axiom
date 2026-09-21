plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.duylt.demo.axiom"
    compileSdk = 36

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
    kotlinOptions {
        jvmTarget = "17"
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
        // lifecycle 2.8.7's detector crashes against AGP 8.9's lint (same as in the SDK's home repo).
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // The SDK. Retrofit, OkHttp, Gson, WorkManager, coroutines, paging-common and koin-core come with
    // it as `api` dependencies — they are not declared again here (axiom/README.md §1).
    implementation(project(":axiom"))

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
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.koin.test)
    testImplementation(libs.okhttp3.mockwebserver)
}
