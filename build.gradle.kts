import java.io.File
import java.net.URI
import java.util.jar.JarFile
import groovy.json.JsonSlurper
import org.gradle.jvm.tasks.Jar

plugins {
    java
    id("me.champeau.jmh") version "0.7.2"  // JMH plugin
    id("jacoco")
}

group = "com.customcontentengine"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// ========== SOURCE SETS ==========

val integrationTest by sourceSets.creating {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

val spike by sourceSets.creating {
    java.srcDir("src/spike/java")
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

configurations[integrationTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[integrationTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    add("integrationTestImplementation", platform("org.junit:junit-bom:5.11.4"))
    add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter")
    add("integrationTestRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine")

    testImplementation("org.jacoco:org.jacoco.core:0.8.12")
    add("integrationTestImplementation", "org.jacoco:org.jacoco.core:0.8.12")
}

tasks.named<ProcessResources>("processIntegrationTestResources") {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// ========== PAPER SERVER DOWNLOAD ==========

val paperVersion = "1.21.1"
val paperBuild = "133"
val paperJarName = "paper-$paperVersion-$paperBuild.jar"
val paperServerJar = layout.buildDirectory.file("paperIntegration/$paperJarName")

val downloadPaperServer by tasks.registering {
    description = "Downloads the Paper server jar using the Fill v3 API"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    outputs.file(paperServerJar)

    doLast {
        val target = paperServerJar.get().asFile
        if (target.exists() && isValidJar(target)) {
            println("Paper jar already exists and is valid: ${target.absolutePath}")
            return@doLast
        }

        if (target.exists()) {
            println("Existing Paper jar is invalid or corrupted; deleting: ${target.absolutePath}")
            target.delete()
        }

        target.parentFile.mkdirs()

        val apiUrl = "https://fill.papermc.io/v3/projects/paper/versions/$paperVersion/builds/$paperBuild"
        val connection = URI(apiUrl).toURL().openConnection()
        connection.setRequestProperty("User-Agent", "CustomContentEngine/0.1.0 (https://github.com/MaiconJh/customcontent-engine)")

        val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }
        val downloadUrl = extractDownloadUrl(jsonResponse)

        println("Downloading Paper from: $downloadUrl")
        URI(downloadUrl).toURL().openStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        println("Downloaded Paper to: ${target.absolutePath}")
    }
}

fun isValidJar(file: File): Boolean {
    return try {
        JarFile(file).use { true }
    } catch (exception: Exception) {
        false
    }
}

fun extractDownloadUrl(json: String): String {
    val slurper = JsonSlurper()
    val parsed = slurper.parseText(json) as Map<*, *>
    val downloads = parsed["downloads"] as Map<*, *>
    val serverDefault = downloads["server:default"] as Map<*, *>
    return serverDefault["url"] as String
}

// ========== COMPILATION ==========

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

// ========== TEST TASKS ==========

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("integrationTest") {
    description = "Runs the basic Paper integration test. Execute with ./gradlew integrationTest --no-daemon."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    dependsOn(tasks.named("integrationTestPluginJar"), downloadPaperServer)
    useJUnitPlatform()
    maxParallelForks = 1

    doFirst {
        systemProperty("customcontent.pluginJar", tasks.named<Jar>("integrationTestPluginJar").get().archiveFile.get().asFile.absolutePath)
        systemProperty("customcontent.paperJar", paperServerJar.get().asFile.absolutePath)
    }
}

tasks.register<Test>("integrationTestSmoke") {
    description = "Runs the critical-path Paper integration tests (Phases 0-1). Execute with ./gradlew integrationTestSmoke --no-daemon."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    dependsOn(tasks.named("integrationTestPluginJar"), downloadPaperServer)
    useJUnitPlatform {
        includeTags("smoke", "mining", "mechanic")
    }
    maxParallelForks = 1

    doFirst {
        systemProperty("customcontent.pluginJar", tasks.named<Jar>("integrationTestPluginJar").get().archiveFile.get().asFile.absolutePath)
        systemProperty("customcontent.paperJar", paperServerJar.get().asFile.absolutePath)
    }
}

val integrationTestPluginJar by tasks.registering(Jar::class) {
    archiveClassifier.set("integration-test")
    from(project.sourceSets.main.get().output) {
        exclude("plugin.yml")
    }
    from(integrationTest.output)
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    dependsOn(tasks.named<Jar>("jar"))
}

// ========== SPIKE TASKS ==========

tasks.register<JavaExec>("binaryPdcSpike") {
    description = "Runs Spike 1 binary PDC codec performance measurements."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = spike.runtimeClasspath
    mainClass.set("com.customcontentengine.spike.BinaryPdcPerformanceSpike")
    dependsOn(tasks.named(spike.classesTaskName))
    args(layout.buildDirectory.file("reports/spikes/001-binary-pdc-performance-results.md").get().asFile.absolutePath)
}

tasks.register<JavaExec>("veinMinerSpike") {
    description = "Runs Spike 5 vein miner BFS feasibility measurements."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = spike.runtimeClasspath
    mainClass.set("com.customcontentengine.spike.VeinMinerFeasibilitySpike")
    dependsOn(tasks.named(spike.classesTaskName))
    standardOutput = System.out
    errorOutput = System.err
    args(layout.buildDirectory.file("reports/spikes/005-vein-miner-feasibility-results.md").get().asFile.absolutePath)
}

// ========== JMH CONFIGURATION ==========

jmh {
    // JMH will automatically create a 'jmh' source set – no need to define it manually.
    // The following options are supported:
    warmupIterations.set(5)          // Number of warmup iterations
    iterations.set(10)               // Number of measurement iterations
    fork.set(2)                      // Number of JVM forks
    timeOnIteration.set("1s")        // Time per iteration
    // timeUnit is not a direct property; we set it via @OutputTimeUnit in the benchmark class.
    resultFormat.set("JSON")         // Output format (CSV, JSON, TEXT)
    resultsFile.set(layout.buildDirectory.file("reports/jmh/results.json"))
    includeTests.set(false)          // Don't run tests as benchmarks
    jmhVersion.set("1.37")           // JMH version (optional, uses latest if omitted)
}

// Optional: task to run JMH benchmarks explicitly (already provided by the plugin)
// You can run: ./gradlew jmh

// ========== JACOCO COVERAGE ==========

tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
}

tasks.named<Test>("integrationTest") {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test, tasks.named<Test>("integrationTest"))
    executionData.setFrom(fileTree(layout.buildDirectory).include("jacoco/*.exec"))
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.check {
    dependsOn(tasks.named("jacocoTestReport"))
}

// ========== CONVENIENCE TASK ==========

tasks.register("checkAll") {
    dependsOn(tasks.named("test"), tasks.named("integrationTest"), tasks.named("jmh"))
    description = "Runs all tests, integration tests, and JMH benchmarks."
}