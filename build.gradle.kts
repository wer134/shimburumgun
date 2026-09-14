plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

application {
    mainClass = "errand.Main"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}
