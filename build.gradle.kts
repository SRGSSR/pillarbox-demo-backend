import org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
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
    languageVersion = JavaLanguageVersion.of(26)
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
  testImplementation(libs.json.schema.validator)
  testImplementation(libs.jsoup)
  testImplementation(libs.konsist)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mock.oauth2.server)
  testImplementation(libs.mockk)
  testImplementation(libs.testcontainers.postgresql)
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
  mainClass.set("$group.backend.bootstrap.ApplicationKt")
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

val npmInstall =
  tasks
    .register<Exec>("npmInstall") {
      description = "Install frontend dependencies"
      commandLine("npm", "ci")
      inputs.file("package.json")
      inputs.file("package-lock.json")
      outputs.dir("node_modules")
    }.get()

// esbuild writes into build/frontend, a directory this task owns alone: no
// overlap with processResources, so a changed source or a deleted output always
// re-runs it.
val frontendDir = layout.buildDirectory.dir("frontend")

val buildFrontend =
  tasks
    .register<Exec>("buildFrontend") {
      dependsOn(npmInstall)
      commandLine("npm", "run", "build")
      inputs.dir("src/main/resources/static/js")
      inputs.dir("src/main/resources/static/css")
      inputs.dir("scripts")
      inputs.file("package.json")
      outputs.dir(frontendDir)
    }.get()

// The bundles join the main output (classpath of run and test, contents of the JAR).
sourceSets.main {
  output.dir(mapOf("builtBy" to buildFrontend), frontendDir)
}

// The raw CSS and JS sources are esbuild's input, not something to serve.
tasks.named<ProcessResources>("processResources") {
  exclude("static/css/**", "static/js/**")
}

tasks.named("classes") {
  dependsOn(buildFrontend)
}

// ==============================================================================
// TASKS — PACKAGING
// ==============================================================================

tasks.shadowJar {
  duplicatesStrategy = DuplicatesStrategy.INCLUDE

  mergeServiceFiles {
    include("META-INF/services/**")
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

  // Guava (via Testcontainers) calls sun.misc.Unsafe, and Netty loads a native
  // library. Both warn on JDK 24+.
  jvmArgs(
    "--sun-misc-unsafe-memory-access=allow",
    "--enable-native-access=ALL-UNNAMED",
  )
}

// ==============================================================================
// TASKS — RUN (LOCAL DEVELOPMENT)
// ==============================================================================

val devEnvironment: Map<String, String> =
  run {
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

    mapOf(
      "DEVELOPMENT" to getEnv("DEVELOPMENT", "true"),
      // --- Ktor ---
      "ENABLE_FORWARDED_HEADERS" to getEnv("ENABLE_FORWARDED_HEADERS", "false"),
      // --- Database ---
      "DATABASE_URL" to getEnv("DATABASE_URL", "jdbc:postgresql://localhost:5432/pillarbox"),
      "DATABASE_USER" to getEnv("DATABASE_USER", "dev_user"),
      "DATABASE_PASSWORD" to getEnv("DATABASE_PASSWORD", "dev_password"),
      "DATABASE_ENCRYPTION_KEY" to getEnv("DATABASE_ENCRYPTION_KEY", "dev-encryption-key-32-chars-long-!!!"),
      // --- Auth ---
      "AUTH_ISSUER" to getEnv("AUTH_ISSUER", "http://localhost:8081/realms/pillarbox"),
      "AUTH_DISCOVERY_PATH" to getEnv("AUTH_DISCOVERY_PATH", ".well-known/openid-configuration"),
      "AUTH_CLIENT_ID" to getEnv("AUTH_CLIENT_ID", "pillarbox-api"),
      "AUTH_CLIENT_SECRET" to getEnv("AUTH_CLIENT_SECRET", ""),
      "AUTH_SCOPES" to getEnv("AUTH_SCOPES", "openid,profile,email"),
      // --- Session ---
      "SESSION_COOKIE_SECRET" to getEnv("SESSION_COOKIE_SECRET", "dev-secret-at-least-32-chars-long-!!!"),
      "SESSION_SECURE" to getEnv("SESSION_SECURE", "false"),
      "SESSION_TIMEOUT" to getEnv("SESSION_TIMEOUT", "28800"),
      "SESSION_VALIDATION_INTERVAL" to getEnv("SESSION_VALIDATION_INTERVAL", "600"),
    )
  }

tasks.named<JavaExec>("run") {
  // Netty loads a native library, which warns on JDK 24+.
  jvmArgs("--enable-native-access=ALL-UNNAMED")
  systemProperty("io.ktor.development", devEnvironment.getValue("DEVELOPMENT").toBoolean())
  environment(devEnvironment)
}

// The command `scripts/dev.mjs` restarts the application with: the JVM, the
// arguments, the classpath and the environment of `run`, without a Gradle build
// held open for as long as the application runs.
tasks.register("devLauncher") {
  description = "Write build/dev/launch, the command the development loop starts the application with"
  dependsOn("classes")
  val launcher = layout.buildDirectory.file("dev/launch")
  outputs.file(launcher)
  outputs.upToDateWhen { false }
  doLast {
    val run = tasks.named<JavaExec>("run").get()

    fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
    val command =
      listOf(
        run.javaLauncher
          .get()
          .executablePath.asFile.path,
      ) +
        run.allJvmArgs +
        listOf("-cp", run.classpath.asPath, run.mainClass.get())
    val file = launcher.get().asFile
    file.writeText(
      buildString {
        appendLine("#!/bin/sh")
        appendLine("cd ${quote(projectDir.path)} || exit 1")
        devEnvironment.forEach { (key, value) -> appendLine("export $key=${quote(value)}") }
        appendLine("exec " + command.joinToString(" ") { quote(it) } + " \"\$@\"")
      },
    )
    // The environment holds the secrets of .env, so only the owner reads the file.
    Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rwx------"))
  }
}

// ==============================================================================
// TASKS — RELEASE
// ==============================================================================

val updateVersion =
  tasks
    .register("updateVersion") {
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
    }.get()

tasks.register("release") {
  dependsOn("build", updateVersion)
}
