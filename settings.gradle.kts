pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "MlPtApp"
include(":composeApp", ":shared", ":data", ":di", ":navigation", ":feature:training")
