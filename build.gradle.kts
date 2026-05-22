import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN
import java.util.Properties

// ==============================================================================
// PLUGINS
// ==============================================================================

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

// ==============================================================================
// PROJECT CONFIGURATION
// ==============================================================================

group = "ch.srgssr.pillarbox"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(24)
  }
}

// ==============================================================================
// REPOSITORIES
// ==============================================================================

repositories {
  mavenCentral()
}

// ==============================================================================
// DEPENDENCIES
// ==============================================================================

dependencies {
  // --- Runtime ---
  implementation(libs.bundles.exposed)
  implementation(libs.bundles.flyway)
  implementation(libs.bundles.koin)
  implementation(libs.bundles.kotlinx)
  implementation(libs.bundles.ktor.client)
  implementation(libs.bundles.ktor.server)
  implementation(libs.hikaricp)
  implementation(libs.logback.classic)
  implementation(libs.postgresql)

  // --- Test ---
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.bundles.ktor.test)
  testImplementation(libs.h2)
  testImplementation(libs.json.schema.validator)
  testImplementation(libs.jsoup)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mock.oauth2.server)
  testImplementation(libs.mockk)
}

// ==============================================================================
// KOTLIN COMPILER
// ==============================================================================

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

// ==============================================================================
// APPLICATION
// ==============================================================================

application {
  mainClass.set("$group.backend.ApplicationKt")
}

// ==============================================================================
// CODE QUALITY
// ==============================================================================

// --- Detekt ---
detekt {
  toolVersion = libs.versions.detekt.get()
  buildUponDefaultConfig = true
  allRules = false
  config.setFrom("$projectDir/detekt.yml")
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

// --- Ktlint ---
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

// ==============================================================================
// TASKS — FRONTEND BUILD
// ==============================================================================

val npmInstall by tasks.registering(Exec::class) {
  description = "Install frontend dependencies"
  commandLine("npm", "ci")
  inputs.file("package.json")
  inputs.file("package-lock.json")
  outputs.dir("node_modules")
}

val buildFrontend by tasks.registering(Exec::class) {
  dependsOn(npmInstall)
  dependsOn("processResources")
  commandLine("npm", "run", "build")
  inputs.dir("src/main/resources/static/js")
  inputs.dir("src/main/resources/static/css")
  outputs.dir("build/resources/main/static/js")
  outputs.dir("build/resources/main/static/css")
}

tasks.named("classes") {
  dependsOn(buildFrontend)
}

// ==============================================================================
// TASKS — PACKAGING
// ==============================================================================

tasks.shadowJar {
  mergeServiceFiles {
    include("META-INF/services/**")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }
  archiveFileName = "${archiveBaseName.get()}.${archiveExtension.get()}"
  manifest { attributes["Main-Class"] = application.mainClass.get() }
}

// ==============================================================================
// TASKS — TEST
// ==============================================================================

tasks.withType<Test> {
  useJUnitPlatform()
  finalizedBy("koverXmlReport")
}

// ==============================================================================
// TASKS — RUN (LOCAL DEVELOPMENT)
// ==============================================================================

tasks.named<JavaExec>("run") {
  val envFile = rootProject.file(".env")
  val localEnv =
    Properties().apply {
      if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
      }
    }

  fun getEnv(
    key: String,
    default: String,
  ): String = System.getenv(key) ?: localEnv.getProperty(key) ?: default

  val isDev = getEnv("DEVELOPMENT", "true").toBoolean()

  // --- Ktor ---
  systemProperty("io.ktor.development", isDev)
  environment("ENABLE_FORWARDED_HEADERS", getEnv("ENABLE_FORWARDED_HEADERS", "false"))

  // --- Database ---
  environment("DATABASE_URL", getEnv("DATABASE_URL", "jdbc:postgresql://localhost:5432/pillarbox"))
  environment("DATABASE_USER", getEnv("DATABASE_USER", "dev_user"))
  environment("DATABASE_PASSWORD", getEnv("DATABASE_PASSWORD", "dev_password"))

  // --- Auth ---
  environment("AUTH_ISSUER", getEnv("AUTH_ISSUER", "http://localhost:8081/realms/pillarbox"))
  environment("AUTH_DISCOVERY_PATH", getEnv("AUTH_DISCOVERY_PATH", ".well-known/openid-configuration"))
  environment("AUTH_CLIENT_ID", getEnv("AUTH_CLIENT_ID", "pillarbox-api"))
  environment("AUTH_CLIENT_SECRET", getEnv("AUTH_CLIENT_SECRET", ""))
  environment("AUTH_SCOPES", getEnv("AUTH_SCOPES", "openid,profile,email"))

  // --- Session ---
  environment("SESSION_COOKIE_SECRET", getEnv("SESSION_COOKIE_SECRET", "dev-secret-at-least-32-chars-long-!!!"))
  environment("SESSION_SECURE", getEnv("SESSION_SECURE", "false"))
  environment("SESSION_TIMEOUT", getEnv("SESSION_TIMEOUT", "28800"))
  environment("SESSION_VALIDATION_INTERVAL", getEnv("SESSION_VALIDATION_INTERVAL", "600"))
}

// ==============================================================================
// TASKS — RELEASE
// ==============================================================================

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
