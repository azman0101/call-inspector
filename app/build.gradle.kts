import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import com.android.build.api.variant.BuildConfigField
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  // KSP had no annotation processing left to do (no Room entity, no Moshi adapter); add it back with them.
  // alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.sentry)
  id("org.owasp.dependencycheck")
}

val localVersionCode = 1
val localVersionName = "1.0.0-dev"

android {
  namespace = "net.slashetc.callinspector"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "net.slashetc.callinspector"
    minSdk = 24
    targetSdk = 36
    // Local builds; CI sets VERSION_CODE and VERSION_NAME, applied in androidComponents below.
    versionCode = localVersionCode
    versionName = localVersionName

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
      // Installs next to the release app instead of clashing with its signature and data.
      applicationIdSuffix = ".debug"
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
  // legal/CGU.md at the repository root is the single source of the terms shown in the app.
  sourceSets { getByName("main") { assets.srcDir("../legal") } }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      // Robolectric downloads its android-all jars from Maven Central on first run: in Claude Code cloud
      // sessions, use the same mirror as settings.gradle.kts to avoid "429 Too Many Requests".
      val useMavenCentralMirror = providers.environmentVariable("CLAUDE_CODE_REMOTE").orNull == "true" ||
        providers.gradleProperty("mavenCentralMirror").orNull == "true"
      if (useMavenCentralMirror) {
        all { it.systemProperty("robolectric.dependency.repo.url", "https://maven-central.storage-download.googleapis.com/maven2/") }
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Values that change on every CI run (version, Sentry DSN) are read when tasks run, not while Gradle
// configures the build: reading them with System.getenv() here made each run miss the configuration cache.
//  - Local builds: 1.0.0-dev (code 1), or -PversionCode / -PversionName.
//  - CI (.github/workflows/build_apk.yml computes both): 1.0.<run_number> on main,
//    1.0.<run_number>-<branch>.<sha> on branches, code <run_number>.
androidComponents {
  onVariants { variant ->
    val versionCode = providers.environmentVariable("VERSION_CODE")
      .orElse(providers.environmentVariable("BUILD_NUMBER"))
      .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER"))
      .orElse(providers.gradleProperty("versionCode"))
      .map { it.trim().toInt() }
    val versionName = providers.environmentVariable("VERSION_NAME")
      .orElse(providers.gradleProperty("versionName"))
      .map { it.trim() }
      .filter { it.isNotEmpty() }
    variant.outputs.forEach { output ->
      output.versionCode.set(versionCode.orElse(localVersionCode))
      output.versionName.set(versionName.orElse(localVersionName))
    }
    // Sentry or GlitchTip DSN, injected at build time.
    variant.buildConfigFields?.put(
      "SENTRY_DSN",
      providers.environmentVariable("SENTRY_DSN").orElse("").map { BuildConfigField("String", "\"$it\"", null) },
    )
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
  // The plugin reports its own usage to sentry.io on every build; blocked in sandboxes, and not needed.
  telemetry.set(false)
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
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.sqlite)
  implementation(libs.sqlcipher.android)
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
  testImplementation(libs.androidx.sqlite.framework)
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
  // "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}
