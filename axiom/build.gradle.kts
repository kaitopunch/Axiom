plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.google.devtools.ksp)
    `maven-publish`
}

apply(from = "$rootDir/gradle/axiom-publish.gradle.kts")

/**
 * Where Room writes Axiom's exported schema. Committed for the same reason `:data` commits its own:
 * a schema JSON per version is what makes the next migration writable and verifiable. Axiom is at
 * version 1; a consumer's device carries this database, so changing `AxiomDatabase` means a migration
 * in the same commit — there is no destructive fallback configured.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.axiom"
    compileSdk = 36

    defaultConfig {
        minSdk = 28
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        // Same crash as in every other module of this repo: lifecycle 2.8.7's detector against AGP 8.9's
        // lint. Nothing here uses LiveData.
        disable += "NullSafeMutableLiveData"
    }
}

dependencies {
    // `api`, not `implementation`, for everything that appears in Axiom's public surface: a consumer
    // declares a Retrofit interface (retrofit), may add an Interceptor (okhttp), reads Flow<…>
    // (coroutines), sets WorkManager Constraints on a task (work), and the reified `task<T>` builder
    // inlines a Gson TypeToken into the call site (gson).
    api(libs.kotlinx.coroutines.core)
    api(libs.retrofit)
    api(platform(libs.okhttp.bom))
    api(libs.okhttp3.okhttp)
    api(libs.gson)
    api(libs.androidx.work.runtime.ktx)

    // The two integrations that used to be `:axiom-paging` and `:axiom-koin`, folded in so a consumer
    // adds one artifact. Both are `api` because they appear in the public surface: `asPagingData()`
    // returns `Flow<PagingData<T>>` (paging-common) and `axiomModule` is a Koin `Module` (koin-core).
    api(libs.androidx.paging.common)
    api(libs.koin.core)

    implementation(libs.converter.gson)
    implementation(libs.okhttp3.logging.interceptor)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.paging.testing)
}
