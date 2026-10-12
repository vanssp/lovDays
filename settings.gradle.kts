pluginManagement {
    repositories {
        maven { url = uri("gradle/jetifier-maven") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("gradle/jetifier-maven") }
        google()
        mavenCentral()
    }
}

rootProject.name = "lovelyDays"
include(":app")
