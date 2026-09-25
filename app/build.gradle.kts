import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.sentry)
  id("org.owasp.dependencycheck")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  // Versioning dynamique :
  // Développements locaux : 1.0.0-dev (code 1)
  // CI Branches (Debug) : 1.0.<run_number>-<branch>.<sha> (code <run_number>)
  // CI Main (Release) : 1.0.<run_number> (code <run_number>)
  val baseVersion = "1.0"
  val envRunNumber = System.getenv("VERSION_CODE")
    ?: System.getenv("BUILD_NUMBER")
    ?: System.getenv("GITHUB_RUN_NUMBER")
    ?: project.findProperty("versionCode")?.toString()
  val resolvedVersionCode = envRunNumber?.toIntOrNull() ?: 1

  val explicitVersionName = System.getenv("VERSION_NAME") ?: project.findProperty("versionName")?.toString()
  val envBranch = (System.getenv("GITHUB_HEAD_REF") ?: System.getenv("GITHUB_REF_NAME") ?: "").trim()
  val isReleaseBranch = envBranch == "main" || envBranch == "master" || System.getenv("IS_RELEASE") == "true"
  val isCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
  val rawSha = System.getenv("GITHUB_SHA") ?: ""
  val shortSha = if (rawSha.length >= 7) rawSha.substring(0, 7) else rawSha

  val resolvedVersionName = when {
    !explicitVersionName.isNullOrBlank() -> explicitVersionName
    isCi && isReleaseBranch -> "$baseVersion.$resolvedVersionCode"
    isCi && !isReleaseBranch -> {
      val sanitizedBranch = envBranch.replace(Regex("[^a-zA-Z0-9.-]"), "-").take(20).ifEmpty { "dev" }
      val shaSuffix = if (shortSha.isNotBlank()) ".$shortSha" else ""
      "$baseVersion.$resolvedVersionCode-$sanitizedBranch$shaSuffix"
    }
    else -> "$baseVersion.0-dev"
  }

  defaultConfig {
    applicationId = "com.aistudio.operatorlookup.wkvqmt"
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    // DSN Sentry ou GlitchTip injecté via variable d'environnement au moment du build
    val sentryDsnEnv = System.getenv("SENTRY_DSN") ?: ""
    buildConfigField("String", "SENTRY_DSN", "\"$sentryDsnEnv\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Relative KEYSTORE_PATH is resolved from the repo root (where CI writes it), not from app/.
  val releaseKeystore = rootProject.file(System.getenv("KEYSTORE_PATH") ?: "my-upload-key.jks")

  signingConfigs {
    create("release") {
      storeFile = releaseKeystore
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    val customDebugKeystore = file("${rootDir}/debug.keystore")
    if (customDebugKeystore.exists()) {
      create("debugConfig") {
        storeFile = customDebugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isDebuggable = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      val customDebugKeystore = file("${rootDir}/debug.keystore")
      if (customDebugKeystore.exists()) {
        signingConfig = signingConfigs.getByName("debugConfig")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

dependencyCheck {
  failBuildOnCVSS = 7.0f
  formats = listOf("HTML", "SARIF")
  scanConfigurations = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
  suppressionFiles = listOf("$rootDir/config/dependency-check-suppressions.xml")
  // Keep the NVD H2 database in Gradle User Home so CI can cache it between runs.
  data.directory.set(File(gradle.gradleUserHomeDir, "dependency-check-data").absolutePath)
  nvd.apiKey.set(providers.environmentVariable("NVD_API_KEY_RAW").orElse(""))
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("SENTRY_DSN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

sentry {
  // Désactive l'upload des mappings pour les builds locaux / compatibilité Termux
  autoUploadProguardMapping.set(false)
  tracingInstrumentation {
    enabled.set(true)
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.kotlin.bom))
  implementation(libs.kotlin.stdlib.jdk7)
  implementation(libs.kotlin.stdlib.jdk8)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.sentry.android)
  // implementation(libs.coil.compose)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
