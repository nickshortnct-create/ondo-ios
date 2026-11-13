import com.google.devtools.ksp.gradle.KspAATask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.util.Base64
import java.util.Properties

plugins {
	alias(libs.plugins.kotlinMultiplatform)
	alias(libs.plugins.kotlinMultiplatformLibrary)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.composeMultiplatform)
	alias(libs.plugins.composeCompiler)
	alias(libs.plugins.valkyrie)
	alias(libs.plugins.ksp)
	alias(libs.plugins.androidx.room3)
}

// remove material 2
configurations.all {
	exclude(group = "org.jetbrains.compose.material", module = "material")
	exclude(group = "androidx.compose.material", module = "material")
}

valkyrie {
	packageName = "com.nickshortnct.ondo.icons"
	generateAtSync = true
	outputDirectory = layout.buildDirectory.dir("generated/sources/valkyrie")

	iconPack {
		name = "Icons"
		targetSourceSet = "commonMain"

		nested {
			name = "Brand"
			sourceFolder = "brand"
		}

		nested {
			name = "Outlined"
			sourceFolder = "outlined"
		}

		nested {
			name = "Filled"
			sourceFolder = "filled"
		}
	}
}

val buildInfoSourceDir = layout.buildDirectory.dir("generated/buildInfo/commonMain/kotlin")

val generateBuildInfo = tasks.register("generateBuildInfo") {
	description = "Generate BuildInfo.kt"
	// Keep task inputs local to the task configuration block.  Capturing a
	// script-level provider from doLast breaks Gradle's configuration cache.
	val output = buildInfoSourceDir.map {
		it.file("com/nickshortnct/ondo/generated/BuildInfo.kt")
	}
	outputs.file(output)

	// A Sync task cannot materialize a TextResource as a source file, which
	// made this task report NO-SOURCE after a clean build.  Write the declared
	// output explicitly so both local and F-Droid builds get the source file.
	val fdroid = providers.environmentVariable("FDROID")
		.map { it == "true" }
		.orElse(providers.gradleProperty("fdroid").map { it.toBoolean() })
		.getOrElse(false)
	inputs.property("fdroid", fdroid)
	doLast {
		val outputFile = output.get().asFile
		outputFile.parentFile.mkdirs()
		outputFile.writeText(
			"""
			package com.nickshortnct.ondo.generated

			object BuildInfo {
				const val FDROID = $fdroid
			}
			""".trimIndent()
		)
	}
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
	dependsOn("generateValkyrieImageVector")
	dependsOn(generateBuildInfo)
}

// no idea why ksp tasks depend on valkyrie
tasks.withType<KspAATask>().configureEach {
	dependsOn("generateValkyrieImageVector")
	dependsOn(generateBuildInfo)
}

kotlin.sourceSets.commonMain {
	kotlin.srcDir(buildInfoSourceDir)
}

tasks.matching { it.name.startsWith("compileKotlinIos") }.configureEach {
	// “truly horrifying workaround” for a crash in SearchScreen.kt
	// https://youtrack.jetbrains.com/issue/KT-84055/Reference-to-lambda-in-lambda-in-function-TextField-can-not-be-evaluated#focus=Comments-27-13188532.0-0
	val tmp = layout.buildDirectory.dir("generated/iosWorkaround/commonMain/kotlin").get()
	kotlin.sourceSets["commonMain"].kotlin.srcDir(tmp)

	doFirst {
		tmp.asFile.mkdirs()
		tmp.file("TextFieldDecorator.kt").asFile.writeText(
			"""
package androidx.compose.foundation.text.input

import androidx.compose.runtime.Composable

public fun interface TextFieldDecorator {
    @Suppress("ComposableLambdaParameterNaming")
    @Composable
    public fun Decoration(innerTextField: @Composable () -> Unit)
}
"""
		)
	}
	doLast {
		tmp.asFile.deleteRecursively()
	}
}

kotlin {
	// iPhone build for TestFlight / App Store (built on a Mac via iosApp/).
	listOf(
		iosArm64(),
		iosSimulatorArm64()
	).forEach { target ->
		target.binaries.framework {
			baseName = "ComposeApp"
			isStatic = true
		}
	}

	android {
		namespace = "com.nickshortnct.ondo"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()

		androidResources.enable = true
		withHostTestBuilder {}

		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
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
		}

		buildToolsVersion = "37.0.0"
	}

	sourceSets {
		commonMain.dependencies {
			implementation(libs.bundles.cmp)
			implementation(libs.bundles.ktor)
			implementation(libs.ktor.client.websockets)
			implementation(libs.bundles.coil)
			implementation(libs.bundles.cmpThirdParty)
			implementation(libs.bundles.androidx.lifecycle)
			implementation(libs.bundles.room)
			implementation(libs.bundles.koin)

			implementation(libs.androidx.navigation3.ui)
			implementation(libs.kotlinx.datetime)
			implementation(libs.kotlinx.serialization.json)
			implementation(libs.kotlinx.collections.immutable)
			implementation(libs.androidx.datastore.preferences)
			implementation(libs.coil.gif)

			implementation(libs.subsonicKotlin)
		}

		androidMain.dependencies {
			implementation("androidx.exifinterface:exifinterface:1.4.2")
			implementation(libs.bundles.ktor.android)
			implementation(libs.bundles.androidx.android)
			implementation(libs.bundles.media3)
		}

		iosMain.dependencies {
			implementation(libs.bundles.ktor.ios)
		}


		getByName("androidHostTest").dependencies {
			implementation("androidx.sqlite:sqlite-bundled-jvm:2.7.1")
		}

		commonTest.dependencies {
			implementation(kotlin("test"))
			implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
		}
	}

	compilerOptions {
		freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xexplicit-backing-fields")
	}
}

room3 {
	schemaDirectory("$projectDir/schemas")
}

// Same override precedence as the release-signing props below: env var first
// (CI), then local.properties (gitignored, for local builds), then the
// hardcoded fallback URL where one exists.
val integrationLocalProperties = Properties().apply {
	val file = rootProject.file("local.properties")
	if (file.exists()) file.inputStream().use { load(it) }
}
fun integrationProp(name: String): String? =
	System.getenv(name) ?: integrationLocalProperties.getProperty(name)

val generatedIntegrationDefaults = layout.buildDirectory.dir("generated/ondoDefaults/androidMain/kotlin")
val generateIntegrationDefaults = tasks.register("generateIntegrationDefaults") {
	val output = generatedIntegrationDefaults.map {
		it.file("com/nickshortnct/ondo/ondo/IntegrationDefaults.generated.kt")
	}
	// Must be a local val here, not a script-level (top-level) val: a
	// top-level val in a Gradle Kotlin DSL script compiles to a property on
	// the synthetic script class, so referencing it from doLast still
	// captures a reference to the script instance, which the configuration
	// cache refuses to serialize ("Gradle script object references"). A
	// local val captured by doLast's closure is a plain serializable value
	// with no script-object involvement.
	// Only URLs are baked in. API keys are deliberately NOT: base64 in a public APK is not
	// a secret. Keys are entered per device in Integrations (stored encrypted) or replaced by
	// a server-side gateway; see SecretStore and ServiceConfig.authMode.
	val resolvedProps = listOf(
		"ONDO_LIDARR_URL", "ONDO_SOULSYNC_URL",
		"ONDO_FALLBACK_URL", "ONDO_AUDIOMUSE_URL",
		"ONDO_NAVIDROME_URL"
	).associateWith { integrationProp(it) ?: "" }
	inputs.properties(resolvedProps)
	outputs.file(output)
	doLast {
		fun value(name: String, fallback: String = "") = resolvedProps[name]?.takeIf { it.isNotBlank() } ?: fallback
		fun encoded(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
		fun config(url: String) = "config(\"${encoded(url)}\")"
		output.get().asFile.apply {
			parentFile.mkdirs()
			writeText("""
				package com.nickshortnct.ondo.ondo

				import java.util.Base64

				private fun decoded(value: String) = Base64.getDecoder().decode(value).toString(Charsets.UTF_8)
				// No API keys are bundled: the config is a URL only and stays disabled until a key is entered.
				private fun config(url: String): ServiceConfig = ServiceConfig(url = decoded(url))

				actual fun bundledIntegrationConfig(name: String): ServiceConfig? = when (name) {
					"Lidarr" -> ${config(value("ONDO_LIDARR_URL", "https://decknas.duckdns.org/lidarr"))}
					"SoulSync" -> ${config(value("ONDO_SOULSYNC_URL", "https://decknas.duckdns.org/soulsync"))}
					"Squid", "YouTube" -> ${config(value("ONDO_FALLBACK_URL", "https://decknas.duckdns.org/ytfallback"))}
					"AudioMuse" -> ${config(value("ONDO_AUDIOMUSE_URL"))}
					else -> null
				}

				actual fun bundledNavidromeUrl(): String = decoded("${encoded(value("ONDO_NAVIDROME_URL", "https://decknas.duckdns.org/navidrome"))}")
			""".trimIndent())
		}
	}
}

extensions.getByType<KotlinMultiplatformExtension>()
	.sourceSets
	.getByName("androidMain")
	.kotlin
	.srcDir(generatedIntegrationDefaults)
tasks.withType<KotlinCompilationTask<*>>().configureEach {
	dependsOn(generateIntegrationDefaults)
}
tasks.withType<KspAATask>().configureEach {
	dependsOn(generateIntegrationDefaults)
}

dependencies {
	add("kspAndroid", libs.androidx.room3.compiler)

	add("kspCommonMainMetadata", libs.androidx.room3.compiler)
	add("kspIosArm64", libs.androidx.room3.compiler)
	add("kspIosSimulatorArm64", libs.androidx.room3.compiler)
}
