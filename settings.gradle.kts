pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
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
