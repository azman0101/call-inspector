// Google's mirror of Maven Central, tried before Maven Central itself: Maven Central answers
// "429 Too Many Requests" to the parallel downloads of a cold build (Claude Code cloud sessions).
// pluginManagement is evaluated before the rest of this file, hence the URL declared twice.
pluginManagement {
  val mavenCentralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    maven(mavenCentralMirror) { name = "MavenCentralMirror" }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

val mavenCentralMirror = "https://maven-central.storage-download.googleapis.com/maven2/"

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    maven(mavenCentralMirror) { name = "MavenCentralMirror" }
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
