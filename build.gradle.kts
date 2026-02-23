import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN
import java.util.Properties

plugins {
  alias(libs.plugins.detekt)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kover)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.serialization)
  alias(libs.plugins.shadow)
  alias(libs.plugins.versions)

  application
}

group = "ch.srgssr.pillarbox"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(24)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  // Dependencies
  implementation(libs.bundles.koin)
  implementation(libs.bundles.kotlinx)
  implementation(libs.bundles.ktor.server)
  implementation(libs.logback.classic)

  // Test Dependencies
  testImplementation(libs.kotest.assertions.ktor)
  testImplementation(libs.kotest.extensions.koin) {
    exclude(group = "io.insert-koin", module = "koin-core")
    exclude(group = "io.insert-koin", module = "koin-test")
  }
  testImplementation(libs.kotest.runner.junit5)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.ktor.server.test.host)
  testImplementation(libs.mockk)
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

detekt {
  toolVersion = libs.versions.detekt.get()
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom("$projectDir/detekt.yml")
}

ktlint {
  version.set(
    libs.versions.ktlint.cli
      .get(),
  )
  debug.set(false)
  android.set(false)
  outputToConsole.set(true)
  ignoreFailures.set(false)
  enableExperimentalRules.set(true)
  reporters {
    reporter(PLAIN)
  }
}

application {
  mainClass.set("$group.backend.ApplicationKt")
}

tasks.shadowJar {
  archiveFileName = "${archiveBaseName.get()}.${archiveExtension.get()}"
  manifest { attributes["Main-Class"] = application.mainClass.get() }
}

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy("koverXmlReport")
}

val updateVersion by tasks.registering {
  doLast {
    val version = project.findProperty("version")?.toString()
    val propertiesFile = file("gradle.properties")
    val properties = Properties()

    propertiesFile.inputStream().use { properties.load(it) }

    if (properties["version"] != version) {
      properties.setProperty("version", version)
      propertiesFile.outputStream().use { properties.store(it, null) }

      println("Version updated to $version in gradle.properties")
    }
  }
}

tasks.register("release") {
  dependsOn("build", updateVersion)
}

configurations
  .matching { it.name.contains("detekt", ignoreCase = true) }
  .configureEach {
    resolutionStrategy.eachDependency {
      if (requested.group == "org.jetbrains.kotlin") {
        useVersion(
          dev.detekt.gradle.plugin
            .getSupportedKotlinVersion(),
        )
      }
    }
  }
