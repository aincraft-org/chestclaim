import com.github.spotbugs.snom.SpotBugsTask
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.Pmd
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
    checkstyle
    pmd
    id("com.diffplug.spotless") version "8.10.0"
    id("com.github.spotbugs") version "6.5.10"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "org.aincraft"

val calverDate = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
version = providers.gradleProperty("buildVersion")
    .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER").map { "$calverDate.$it" })
    .orElse("$calverDate-SNAPSHOT")
    .get()
description = "Durable automatic rewards and protected-chest returns for Paper servers"

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
    mavenCentral()
}

val shade = configurations.create("shade") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("com.zaxxer:HikariCP:7.0.2")
    add(shade.name, "com.zaxxer:HikariCP:7.0.2") {
        isTransitive = false
    }

    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("com.zaxxer:HikariCP:7.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testRuntimeOnly("org.xerial:sqlite-jdbc:3.53.2.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.named<ProcessResources>("processResources") {
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

checkstyle {
    toolVersion = "13.11.0"
    config = resources.text.fromUri(
        "https://raw.githubusercontent.com/checkstyle/checkstyle/checkstyle-13.11.0/src/main/resources/google_checks.xml",
    )
}

pmd {
    toolVersion = "7.26.0"
    isIgnoreFailures = false
}

spotbugs {
    toolVersion.set("4.9.7")
    ignoreFailures.set(false)
}

spotless {
    java {
        googleJavaFormat("1.36.1")
    }
}

tasks.withType<Checkstyle>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<Pmd>().configureEach {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<SpotBugsTask>().configureEach {
    reports {
        create("xml") { required.set(true) }
        create("html") { required.set(true) }
    }
}

tasks.named("check") {
    dependsOn(tasks.withType<Checkstyle>())
    dependsOn(tasks.withType<Pmd>())
    dependsOn(tasks.withType<SpotBugsTask>())
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(shade.map { file -> if (file.isDirectory) file else zipTree(file) })
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}

tasks {
    runServer {
        minecraftVersion("26.2")
        // Pinned Modrinth version ID for Bolt 1.2.22, compatible with Paper 26.2.
        downloadPlugins {
            modrinth("bolt", "j3QcPcdy")
        }
        val eula = layout.projectDirectory.file("run/eula.txt")
        doFirst {
            // run-paper does not manage the EULA; a fresh server directory refuses to boot until accepted.
            eula.asFile.apply {
                parentFile.mkdirs()
                if (!exists()) {
                    writeText("#By changing the setting below to TRUE you are indicating your agreement to our EULA (https://aka.ms/MinecraftEULA).\neula=true\n")
                } else if (!readText().contains("eula=true")) {
                    appendText("\neula=true\n")
                }
            }
            // Optional dev port override for parallel servers: ./gradlew runServer -PserverPort=25566
            val portOverride = providers.gradleProperty("serverPort").orNull
            if (portOverride != null) {
                val serverProperties = layout.projectDirectory.file("run/server.properties")
                val lines = serverProperties.asFile.run {
                    if (exists()) readLines().toMutableList() else mutableListOf()
                }
                val index = lines.indexOfFirst { it.startsWith("server-port=") }
                if (index >= 0) lines[index] = "server-port=$portOverride" else lines.add("server-port=$portOverride")
                serverProperties.asFile.writeText(lines.joinToString("\n") + "\n")
            }
        }
    }
}
