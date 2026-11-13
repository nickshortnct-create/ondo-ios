import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.impl.VariantOutputImpl
import java.util.Properties

plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
}

val isTaskRelease = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }

val fdroid = System.getenv("FDROID") == "true" || providers.gradleProperty("fdroid")
	.map { it.toBoolean() }
	.getOrElse(false)

// Ondo's real Android signing identity is the debug.keystore its raw-gradlew
// local builds have always shipped with (Ondo moved off EAS cloud signing
// before this port; the phone's currently-installed build is debug-signed
// with this exact keystore). It's copied byte-for-byte (see ONDO_PORT_STATUS.md
// for the sha256) into androidApp/keystore/ondo-release.keystore, which is
// gitignored. Its store/key passwords are the standard Android SDK debug
// keystore placeholders ("android" / "androiddebugkey") — not a real secret —
// as read from the signingConfigs.debug block in
// /home/nicknas/wavio/apps/mobile/android/app/build.gradle.
// ONDO_RELEASE_* env vars / local.properties override all of this if a
// distinct production key ever needs to take over.
val localProperties = Properties().apply {
	val file = rootProject.file("local.properties")
	if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSigningProp(name: String): String? =
	System.getenv(name) ?: localProperties.getProperty(name)

val ondoKeystoreFile = rootProject.file("androidApp/keystore/ondo-release.keystore")

val ondoReleaseStoreFile = releaseSigningProp("ONDO_RELEASE_STORE_FILE")
	?: ondoKeystoreFile.takeIf { it.exists() }?.path
val ondoReleaseStorePassword = releaseSigningProp("ONDO_RELEASE_STORE_PASSWORD") ?: "android"
val ondoReleaseKeyAlias = releaseSigningProp("ONDO_RELEASE_KEY_ALIAS") ?: "androiddebugkey"
val ondoReleaseKeyPassword = releaseSigningProp("ONDO_RELEASE_KEY_PASSWORD") ?: "android"

val hasOndoReleaseSigning = !ondoReleaseStoreFile.isNullOrBlank() && rootProject.file(ondoReleaseStoreFile).isFile
if (isTaskRelease && !hasOndoReleaseSigning) throw GradleException("Ondo release builds require the configured release keystore")

extensions.configure<ApplicationExtension> {
	namespace = "com.nickshortnct.ondo.androidApp"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	buildFeatures {
		resValues = true
	}

	defaultConfig {
		applicationId = "com.nickshortnct.ondo"
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		versionCode = 982
		versionName = "1.0.82"

		ndk {
			abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
			if (!isTaskRelease) {
				abiFilters.add("x86_64")
			}
		}
	}

	signingConfigs {
		create("release") {
			if (hasOndoReleaseSigning) {
				storeFile = file(ondoReleaseStoreFile!!)
				storePassword = ondoReleaseStorePassword
				keyAlias = ondoReleaseKeyAlias
				keyPassword = ondoReleaseKeyPassword
			}
		}
	}

	buildTypes {
		getByName("release") {
			val enableR8 = System.getenv("ONDO_ENABLE_R8") == "1"
			isMinifyEnabled = enableR8
			isDebuggable = false
			isProfileable = false
			isJniDebuggable = false
			isShrinkResources = enableR8
			// A release invocation without this keystore is rejected above.
			signingConfig = if (hasOndoReleaseSigning) {
				signingConfigs.getByName("release")
			} else {
				signingConfigs.getByName("debug")
			}
			proguardFiles(
				getDefaultProguardFile("proguard-android-optimize.txt"),
				"proguard-rules.pro"
			)
		}

		getByName("debug") {
			applicationIdSuffix = ".debug"
			resValue("string", "app_name", "Ondo (Dev)")
		}
	}

	packaging {
		resources {
			excludes += "/okhttp3/**"
			excludes += "/*.properties"
			excludes += "/org/antlr/**"
			excludes += "/com/android/tools/smali/**"
			excludes += "/org/eclipse/jgit/**"
			excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
			excludes += "/org/bouncycastle/**"
			excludes += "/META-INF/{AL2.0,LGPL2.1}"
		}

		jniLibs {
			keepDebugSymbols.add("**/*.so")
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

extensions.configure<ApplicationAndroidComponentsExtension> {
	onVariants { variant ->
		variant.outputs.forEach { output ->
			if (output is VariantOutputImpl) {
				output.outputFileName = if (fdroid) {
					"Ondo.fdroid.apk"
				} else {
					"Ondo.apk"
				}
			}
		}
	}
	onVariants(selector().withBuildType("release")) {
		it.packaging.resources.excludes.apply {
			add("/**/*.version")
			add("/kotlin-tooling-metadata.json")
			add("/DebugProbesKt.bin")
			add("/**/*.kotlin_builtins")
		}
	}
}

dependencies {
	implementation(projects.composeApp)
	implementation(libs.androidx.activity.compose)
	implementation(libs.cmp.material3)
	implementation(libs.koin.android)
	implementation(libs.koin.core)
	implementation(libs.bundles.glance)
	implementation(libs.bundles.coil)
	implementation(libs.bundles.media3)
	implementation(libs.play.services.wearable)
	implementation(libs.kotlinx.coroutines.play.services)
	implementation(libs.multiplatformSettings)
}
