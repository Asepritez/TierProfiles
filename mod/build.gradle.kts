plugins {
    // Applies the right Loom for this Minecraft version.
    id("dev.kikugie.loom-back-compat")
}

// The jar carries core's classes, so core must be set up first.
evaluationDependsOn(":core")

val modVersion = sc.properties.get<String>("mod.version")
val minecraftVersion = sc.current.version
val javaVersion = sc.properties.get<Long>("mod.java").toInt()

group = "dev.tierprofiles"
version = "$modVersion+mc$minecraftVersion"
base.archivesName = "tierprofiles"

// One JDK builds everything. Older versions get older bytecode.
java.toolchain.languageVersion = JavaLanguageVersion.of(25)

repositories {
    mavenCentral()
    maven("https://maven.terraformersmc.com/releases/")
}

// Real in-game tests. They run inside an actual game window with the offline dev
// account, so they're never part of `build` and must be started by hand with
// `./gradlew :<version>:runClientGameTest`.
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
    // Mojang's own names on versions that are still obfuscated.
    loomx.applyMojangMappings()
    modImplementation("net.fabricmc:fabric-loader:${sc.properties.get<String>("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")
    implementation(project(":core"))

    // Reads WebP, because SubTiers publishes nothing else.
    // Pure Java, BSD licensed, and only used on downloaded images.
    for (library in listOf(
        "imageio:imageio-webp", "imageio:imageio-core", "imageio:imageio-metadata",
        "common:common-lang", "common:common-io", "common:common-image",
    )) {
        val (group, artifact) = library.split(":")
        val coordinates = "com.twelvemonkeys.$group:$artifact:3.15.3"
        implementation(coordinates)
        include(coordinates)
    }

    // Optional: the mod runs fine without Mod Menu.
    modCompileOnly("com.terraformersmc:modmenu:${sc.properties.get<String>("deps.modmenu")}")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = javaVersion
}

tasks.processResources {
    val props = mapOf(
        "version" to modVersion,
        "minecraft" to sc.properties.get<String>("mod.mc_compat"),
        "java" to javaVersion.toString(),
        "loader" to sc.properties.get<String>("deps.fabric_loader"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") {
        expand(props)
    }
    filesMatching("*.mixins.json") {
        expand(props)
    }
}

tasks.withType<Jar> {
    if (name == "jar" || name == "remapJar") {
        from(project(":core").extensions.getByType<SourceSetContainer>()["main"].output)
        from(rootProject.file("../LICENSE"))
        from(rootProject.file("../THIRD_PARTY_NOTICES.txt"))
    }
}

loom {
    runs.named("clientGameTest") {
        runDir("run/gametest")
        // `-Pgallery` takes the draft gallery shots instead of the tests, as Hoseinplays so
        // your own card has your public skin. No token, so it's still an offline account.
        if (project.hasProperty("gallery")) {
            runDir("run/gallery")
            vmArg("-Dtierprofiles.gallery=true")
            programArgs("--username", "Hoseinplays", "--uuid", "835b345e23d8408da9313adee61ca1d4")
        }
    }
}

// Every version's finished jar, collected in one place.
tasks.register<Copy>("collectJar") {
    group = "build"
    from(loomx.modJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.dir("libs"))
}
