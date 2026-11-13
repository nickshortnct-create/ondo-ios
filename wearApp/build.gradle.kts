import com.android.build.api.dsl.ApplicationExtension
import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.composeCompiler)
}

val isTaskRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

// Same signing identity as the phone app (see androidApp/build.gradle.kts):
// the Wear Data Layer only connects the phone and watch apps when both the
// package name and the signing certificate match.
val localProperties = Properties().apply {
	val file = rootProject.file("local.properties")
	if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSigningProp(name: String): String? =
	System.getenv(name) ?: localProperties.getProperty(name)

val ondoReleaseStoreFile = releaseSigningProp("ONDO_RELEASE_STORE_FILE")
	?: rootProject.file("androidApp/keystore/ondo-release.keystore").takeIf { it.exists() }?.path
val hasOndoReleaseSigning = !ondoReleaseStoreFile.isNullOrBlank() && rootProject.file(ondoReleaseStoreFile).isFile
if (isTaskRelease && !hasOndoReleaseSigning) throw GradleException("Ondo release builds require the configured release keystore")

extensions.configure<ApplicationExtension> {
	namespace = "com.nickshortnct.ondo.wear"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "com.nickshortnct.ondo"
		minSdk = 30
		targetSdk = 36
		versionCode = 962
		versionName = "1.0.62"
	}

	buildFeatures {
		compose = true
	}

	signingConfigs {
		create("release") {
			if (hasOndoReleaseSigning) {
				storeFile = file(ondoReleaseStoreFile!!)
				storePassword = releaseSigningProp("ONDO_RELEASE_STORE_PASSWORD") ?: "android"
				keyAlias = releaseSigningProp("ONDO_RELEASE_KEY_ALIAS") ?: "androiddebugkey"
				keyPassword = releaseSigningProp("ONDO_RELEASE_KEY_PASSWORD") ?: "android"
			}
		}
	}

	buildTypes {
		getByName("release") {
			isMinifyEnabled = true
			isShrinkResources = true
			signingConfig = if (hasOndoReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
		getByName("debug") {
			// Debug builds talk to the debug phone app (com.nickshortnct.ondo.debug).
			applicationIdSuffix = ".debug"
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}

	dependenciesInfo {
		includeInApk = false
		includeInBundle = false
	}
}

dependencies {
	implementation(libs.wear.compose.material3)
	implementation(libs.wear.compose.foundation)
	implementation(libs.wear.compose.navigation)
	implementation(libs.androidx.wear)
	implementation(libs.androidx.wear.ongoing)
	implementation(libs.androidx.wear.input)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose.android)
	implementation(libs.bundles.media3)
	implementation(libs.play.services.wearable)
	implementation(libs.kotlinx.coroutines.play.services)
	implementation(libs.coil.compose)
	implementation("io.coil-kt.coil3:coil-network-okhttp:${libs.versions.coil.get()}")
	debugImplementation(libs.wear.compose.uiTooling)
}
