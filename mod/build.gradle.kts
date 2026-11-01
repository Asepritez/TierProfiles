plugins {
    id("net.fabricmc.fabric-loom") version "1.19.3"
}

val modVersion = property("mod_version") as String
val minecraftVersion = property("minecraft_version") as String

group = "dev.tierprofiles"
version = "$modVersion+mc$minecraftVersion"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    maven("https://maven.terraformersmc.com/releases/")
}

// Real in-game tests. They run inside an actual game window with the offline dev
// account, so they're never part of `build` and must be started by hand with
// `./gradlew runClientGameTest`.
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "tierprofiles-test"
        enableGameTests = false
        enableClientGameTests = true
        eula = true
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    implementation(project(":core"))

    // Optional: the mod runs fine without Mod Menu.
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.processResources {
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

tasks.jar {
    from(project(":core").sourceSets.main.get().output)
    from(rootDir.resolve("../LICENSE"))
    from(rootDir.resolve("../THIRD_PARTY_NOTICES.txt"))
}

loom {
    runs.named("clientGameTest") {
        runDir("run/gametest")
    }
}
