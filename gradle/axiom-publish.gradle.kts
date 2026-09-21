/**
 * Publishing for the Axiom module, applied with `apply(from = "$rootDir/gradle/axiom-publish.gradle.kts")`.
 *
 * Kept as a separate script rather than inlined in `axiom/build.gradle.kts` so the coordinates and the
 * repository wiring stay in one place if the SDK is ever split again or a second artifact is added.
 *
 * Coordinates come from the root `gradle.properties` (AXIOM_GROUP / AXIOM_VERSION / AXIOM_GITHUB_REPO).
 * Credentials come from `~/.gradle/gradle.properties` (`gpr.user`, `gpr.key`) or the environment
 * (GITHUB_ACTOR, GITHUB_TOKEN) — never from a file in this repo.
 *
 *   ./gradlew :axiom:publish
 *   ./gradlew :axiom:publishToMavenLocal   # try a consumer against ~/.m2 before pushing
 *
 * The artifactId is the module's own name (`axiom`).
 */
val axiomGroup: String = providers.gradleProperty("AXIOM_GROUP").get()
val axiomVersion: String = providers.gradleProperty("AXIOM_VERSION").get()
val axiomRepo: String = providers.gradleProperty("AXIOM_GITHUB_REPO").get()

group = axiomGroup
version = axiomVersion

plugins.withId("maven-publish") {
    configure<PublishingExtension> {
        publications {
            register<MavenPublication>("release") {
                groupId = axiomGroup
                artifactId = project.name
                version = axiomVersion
                afterEvaluate { from(components["release"]) }
                pom {
                    name.set(project.name)
                    description.set("Axiom — declarative API sync with a Room single source of truth and WorkManager scheduling")
                    url.set("https://github.com/$axiomRepo")
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/$axiomRepo")
                credentials {
                    username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                    password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }
}
