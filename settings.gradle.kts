pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DemoAxiom"

include(":app")

// Axiom — the "call API → Room SSOT → WorkManager" SDK this app demonstrates. Included as a source
// module (a verbatim copy of the SDK's `axiom/` folder) so the demo is self-contained. A consumer that
// takes the published artifact instead drops this line and writes `implementation("com.axiom:axiom:<v>")`
// against the GitHub Packages repository — see axiom/README.md §1.
include(":axiom")
