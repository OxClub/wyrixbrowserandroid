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
        // Official Mozilla Maven: the only source for GeckoView binaries.
        maven {
            url = uri("https://maven.mozilla.org/maven2/")
            content { includeGroup("org.mozilla.geckoview") }
        }
    }
}
rootProject.name = "WYRIX-Browser"
include(":app")
