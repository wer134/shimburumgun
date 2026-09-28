import java.io.ByteArrayInputStream

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

tasks.named<JavaExec>("run") {
    // Gradle의 JavaExec는 기본적으로 표준입력을 빈 스트림으로 둔다. 그대로 두면
    // 선택지를 입력할 수 없고 모든 프롬프트가 EOF로 즉시 지나가 버린다.
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
}

/**
 * 스토리 JSON만 검증한다. CI와 작가 작업용.
 *
 * 테스트를 전부 돌리지 않고 오타·끊어진 goto·선언 안 된 플래그만 빨리 보고 싶을 때 쓴다.
 */
tasks.register<JavaExec>("validateStory") {
    group = "verification"
    description = "스토리 JSON을 검증한다 (플레이하지 않음)"
    mainClass = "errand.Main"
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--validate")
    jvmArgs = listOf("-Dfile.encoding=UTF-8")
}

/**
 * 첫 선택지만 골라 끝까지 자동 진행한다. 스모크 테스트.
 *
 * 스토리를 고친 뒤 "적어도 막히지는 않는가"를 10초 안에 확인하는 용도다.
 */
tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "자동으로 끝까지 진행해 막히는 곳이 없는지 본다"
    mainClass = "errand.Main"
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("--auto", "--seed=1")
    jvmArgs = listOf("-Dfile.encoding=UTF-8")
    standardInput = ByteArrayInputStream(ByteArray(0))
}

tasks.check {
    dependsOn(tasks.named("validateStory"), tasks.named("smoke"))
}
