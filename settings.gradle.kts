// Google's mirror of Maven Central, tried before Maven Central itself, only in Claude Code cloud sessions
// (CLAUDE_CODE_REMOTE=true): there Maven Central answers "429 Too Many Requests" to the parallel downloads
// of a cold build. CI and local builds use Maven Central directly; -PmavenCentralMirror=true forces the
// mirror elsewhere. pluginManagement is evaluated before the rest of this file, hence the code twice.
pluginManagement {
  val useMavenCentralMirror = System.getenv("CLAUDE_CODE_REMOTE") == "true" ||
    providers.gradleProperty("mavenCentralMirror").orNull == "true"
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    if (useMavenCentralMirror) {
      maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    } else {
      mavenCentral()
    }
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

val useMavenCentralMirror = System.getenv("CLAUDE_CODE_REMOTE") == "true" ||
  providers.gradleProperty("mavenCentralMirror").orNull == "true"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    if (useMavenCentralMirror) {
      maven("https://maven-central.storage-download.googleapis.com/maven2/") { name = "MavenCentralMirror" }
    }
    mavenCentral()
  }
}

rootProject.name = "Info Opérateur"

// Détection automatique pour Termux : active l'AAPT2 local si le binaire existe
val termuxAapt2 = file("/data/data/com.termux/files/usr/bin/aapt2")
if (termuxAapt2.exists()) {
  gradle.startParameter.projectProperties["android.aapt2FromMavenOverride"] = termuxAapt2.absolutePath
}

include(":app")
