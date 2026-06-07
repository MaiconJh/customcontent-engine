import java.net.URI
import java.security.MessageDigest
import org.gradle.jvm.tasks.Jar

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

val paperVersion = "1.21.1"
val paperBuild = "133"
val paperJarName = "paper-$paperVersion-$paperBuild.jar"
val paperSha256 = "39bd8c00b9e18de91dcabd3cc3dcfa5328685a53b7187a2f63280c22e2d287b9"
val paperServerJar = layout.buildDirectory.file("paperIntegration/$paperJarName")

val downloadPaperServer by tasks.registering {
    description = "Downloads the Paper server jar used by the basic Paper integration test."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    outputs.file(paperServerJar)

    doLast {
        val target = paperServerJar.get().asFile
        if (!target.exists() || target.sha256() != paperSha256) {
            target.parentFile.mkdirs()
            val url = "https://api.papermc.io/v2/projects/paper/versions/$paperVersion/builds/$paperBuild/downloads/$paperJarName"
            URI(url).toURL().openStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actualSha256 = target.sha256()
        if (actualSha256 != paperSha256) {
            throw GradleException("Downloaded Paper jar checksum mismatch: expected $paperSha256 but was $actualSha256")
        }
    }
}

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
    mainClass.set("com.customcontentengine.spike.BinaryPdcPerformanceSpike")
    dependsOn(tasks.named(spike.classesTaskName))
    args(layout.buildDirectory.file("reports/spikes/001-binary-pdc-performance-results.md").get().asFile.absolutePath)
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
