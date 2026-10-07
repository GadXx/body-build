pluginManagement {
    repositories {
        google()
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

rootProject.name = "BodyBuild"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:designsystem",
    ":domain:catalog",
    ":domain:generator",
    ":domain:validation",
    ":feature:onboarding",
    ":feature:preferences",
    ":feature:plan",
    ":feature:exercise",
    ":feature:workout",
)
