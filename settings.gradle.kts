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
        // Axiom's public channel. Scoped to its group so it is only asked for
        // `com.github.kaitopunch:Axiom` (AXIOM_SOURCE=jitpack in gradle.properties); everything else
        // keeps resolving from google()/mavenCentral() alone.
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroup("com.github." + providers.gradleProperty("AXIOM_GITHUB_REPO").get().substringBefore("/"))
            }
        }
    }
}

rootProject.name = "DemoAxiom"

include(":app")

// Axiom — the "call API → Room SSOT → WorkManager" SDK this app demonstrates. Included as a source
// module (a verbatim copy of the SDK's `axiom/` folder) so the demo is self-contained, and because
// JitPack builds the release from it (`jitpack.yml` runs `:axiom:publishToMavenLocal`). Whether :app
// consumes this module or the published `com.github.kaitopunch:Axiom:<v>` is AXIOM_SOURCE's call —
// gradle.properties / app/build.gradle.kts. A project of your own needs only the JitPack repository
// above and the dependency line — see README.md, first section.
include(":axiom")
