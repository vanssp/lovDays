pluginManagement {
    repositories {
        maven { url = uri("gradle/offline-maven") }
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("gradle/offline-maven") }
        mavenCentral()
        google()
    }
}

rootProject.name = "lovelyDays"
include(":app")
