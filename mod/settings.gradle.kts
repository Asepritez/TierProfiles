pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}

rootProject.name = "tierprofiles"

// `core` is plain Java with no Minecraft on its classpath, so a Minecraft import
// there simply can't compile. Everything testable lives in it.
include("core")
