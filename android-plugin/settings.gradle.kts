// The Godot Android plugin: platform calls build-13 made from its Activity and views, and
// build-13's music synthesizer. Built into an AAR that the Godot export (Gradle build) links.
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

rootProject.name = "pocket-arcade-android-plugin"
include(":plugin")
