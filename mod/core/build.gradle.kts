plugins {
    `java-library`
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

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

tasks.test {
    useJUnitPlatform()
}
