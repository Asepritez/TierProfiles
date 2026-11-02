pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
}

plugins {
    // One source tree, built for every Minecraft version. Where the game differs, the
    // code carries small `//? if` blocks. See docs/PORTING.md.
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks the right Loom for each version: 26.x ships unobfuscated, older ones don't.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
}

stonecutter {
    create(rootProject) {
        versions("1.21.11", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "tierprofiles"

// `core` is plain Java with no Minecraft on its classpath, so a Minecraft import
// there simply can't compile. Everything testable lives in it, and every version
// shares it.
include("core")
