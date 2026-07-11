import java.net.URI
import java.security.MessageDigest
import org.gradle.jvm.tasks.Jar
import java.io.BufferedReader
import java.io.InputStreamReader

plugins {
    java
}

group = "com.customcontentengine"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

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

// ==================== PAPER FILL V3 API ====================
val paperVersion = "1.21.1"
val paperServerJar = layout.buildDirectory.file("paperIntegration/paper-$paperVersion.jar")

val downloadPaperServer by tasks.registering {
    description = "Downloads the Paper server jar using the Fill v3 API"
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    outputs.file(paperServerJar)

    doLast {
        val target = paperServerJar.get().asFile
        if (!target.exists()) {
            target.parentFile.mkdirs()

            // 1. Consulta a API v3 para obter os dados do build mais recente
            val apiUrl = "https://fill.papermc.io/v3/projects/paper/versions/$paperVersion/builds/latest"
            val connection = URI(apiUrl).toURL().openConnection()
            // A nova API exige um User-Agent válido e não genérico
            connection.setRequestProperty("User-Agent", "CustomContentEngine/0.1.0 (https://github.com/MaiconJh/customcontent-engine)")

            val jsonResponse = connection.inputStream.bufferedReader().use { it.readText() }

            // 2. Extrai a URL de download do JSON (usando um parser simples)
            val downloadUrl = extractDownloadUrl(jsonResponse)

            println("Downloading Paper from: $downloadUrl")
            URI(downloadUrl).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            println("Downloaded Paper to: ${target.absolutePath}")
        } else {
            println("Paper jar already exists: ${target.absolutePath}")
        }
    }
}

// Função auxiliar para extrair a URL de download do JSON
fun extractDownloadUrl(json: String): String {
    // Procura pelo campo "downloadUrl" na resposta JSON
    val regex = "\"downloadUrl\"\\s*:\\s*\"([^\"]+)\"".toRegex()
    return regex.find(json)?.groupValues?.get(1)
        ?: throw GradleException("Could not find download URL in API response: $json")
}
// ==========================================================

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("integrationTest") {
    description = "Runs the basic Paper integration test. Execute with ./gradlew integrationTest --no-daemon."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    shouldRunAfter(tasks.test)
    dependsOn(tasks.named<Jar>("jar"), downloadPaperServer)
    useJUnitPlatform()

    doFirst {
        systemProperty("customcontent.pluginJar", tasks.named<Jar>("jar").get().archiveFile.get().asFile.absolutePath)
        systemProperty("customcontent.paperJar", paperServerJar.get().asFile.absolutePath)
    }
}

tasks.register<JavaExec>("binaryPdcSpike") {
    description = "Runs Spike 1 binary PDC codec performance measurements."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classpath = spike.runtimeClasspath
    mainClass.set("com.customcontentengine.spikSe.BinaryPdcPerformanceSpike")
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

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}