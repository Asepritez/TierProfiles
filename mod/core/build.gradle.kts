plugins {
    `java-library`
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

// Java 17 bytecode, so this same core can ship in builds for Minecraft 1.18 and up.

repositories {
    mavenCentral()
}

dependencies {
    // The game already ships Gson, so this is compile-only and isn't bundled.
    compileOnly("com.google.code.gson:gson:2.13.2")

    testImplementation("com.google.code.gson:gson:2.13.2")
    testImplementation(platform("org.junit:junit-bom:6.0.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release = 25
}

// Only what ships has to run on old Java. The tests stay on 25.
tasks.compileJava {
    options.release = 17
}

tasks.test {
    useJUnitPlatform()
}
