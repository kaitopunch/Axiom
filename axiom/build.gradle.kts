plugins {
    alias(libs.plugins.android.library)
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
        // The lowest the dependencies allow (work-runtime 2.12's own minSdk), not the demo's: a
        // library's minSdk is a floor every consumer app inherits.
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
        // AGP 9 would stamp this library's own compileSdk (36) into the AAR as the consumer's floor.
        // Axiom uses no API above 35, and 35 is what work-runtime 2.12 already demands.
        aarMetadata {
            minCompileSdk = 35
        }
    }

    buildFeatures {
        buildConfig = false
    }

    // Java 11 bytecode: the lowest the dependencies allow (work-runtime 2.12's inline builders are JVM 11).
    // A consumer cannot inline Axiom's reified `task<T>` builder into a module with a lower jvmTarget
    // than Axiom's own, so this stays at that floor rather than 17 — building still happens on JDK 17.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
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
        // Same crash as in every other module of this repo: lifecycle's detector against AGP's lint.
        // Nothing here uses LiveData.
        disable += "NullSafeMutableLiveData"
    }
}

kotlin {
    // Otherwise the POM depends on kotlin-stdlib 2.4.x (the compiler's own), and a Kotlin 2.2 consumer
    // cannot read 2.4 metadata. 2.3.20 is what koin-core 4.2 pulls in regardless.
    coreLibrariesVersion = "2.3.20"
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        // Axiom's class metadata is written for Kotlin 2.2, so a consumer app compiling with Kotlin 2.2+
        // can read it (a compiler reads metadata at most one version ahead of itself). 2.2 is also the
        // floor the dependencies already set — koin-core 4.2 is built against kotlin-stdlib 2.3.
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
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
