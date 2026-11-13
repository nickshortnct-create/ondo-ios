package com.nickshortnct.ondo.ui.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kyant.capsule.ContinuousCapsule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import ondo.composeapp.generated.resources.Res
import ondo.composeapp.generated.resources.action_update_app
import ondo.composeapp.generated.resources.info_update
import ondo.composeapp.generated.resources.title_update
import org.jetbrains.compose.resources.stringResource
import com.nickshortnct.ondo.di.LocalPlatformContext
import com.nickshortnct.ondo.ui.components.common.Markdown
import com.nickshortnct.ondo.ui.theme.defaultFont
import com.nickshortnct.ondo.util.Logger
import com.nickshortnct.ondo.di.AppUpdater
import com.nickshortnct.ondo.di.UpdateInstallResult
import com.nickshortnct.ondo.di.compareUpdateVersions
import com.nickshortnct.ondo.di.releaseBuildNumber
import kotlinx.coroutines.CancellationException

@Serializable
data class GitHubRelease(
	@SerialName("tag_name") val tag: String,
	@SerialName("html_url") val url: String,
	@SerialName("body") val body: String,
	@SerialName("prerelease") val prerelease: Boolean = false,
	@SerialName("assets") val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
	@SerialName("name") val name: String,
	@SerialName("browser_download_url") val downloadUrl: String,
	val size: Long = 0
)

enum class UpdateDownloadState { IDLE, DOWNLOADING, READY, PERMISSION_REQUIRED, FAILED }
enum class UpdateCheckState { IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, FAILED }

class ChangelogViewModel(
	private val updater: AppUpdater
) : ViewModel() {
	val release: StateFlow<GitHubRelease?>
		field = MutableStateFlow(null)
	val downloadState: StateFlow<UpdateDownloadState>
		field = MutableStateFlow(UpdateDownloadState.IDLE)
	val checkState: StateFlow<UpdateCheckState>
		field = MutableStateFlow(UpdateCheckState.IDLE)
	val checkError: StateFlow<String?>
		field = MutableStateFlow(null)

	private val updateClient = HttpClient {
		install(ContentNegotiation) {
			json(Json { ignoreUnknownKeys = true })
		}
	}

	fun checkForUpdates(currentVersion: String) {
		if (checkState.value == UpdateCheckState.CHECKING) return
		viewModelScope.launch {
			checkState.value = UpdateCheckState.CHECKING
			checkError.value = null
			release.value = null
			downloadState.value = UpdateDownloadState.IDLE
			try {
				val releases: List<GitHubRelease> = updateClient.get(
					"https://api.github.com/repos/nickshortnct-create/ondo-apk/releases?per_page=100"
				).body()
				val newestAndroidRelease = releases
					.filter { !it.prerelease && it.assets.any { asset -> asset.name == "Ondo.apk" } && releaseBuildNumber(it.tag) != null }
					.maxWithOrNull { left, right -> compareUpdateVersions(left.tag, right.tag) }
					?: run {
						checkState.value = UpdateCheckState.UP_TO_DATE
						return@launch
					}
				if (compareUpdateVersions(newestAndroidRelease.tag, currentVersion.substringBefore("+") + "+" + updater.installedVersionCode) > 0) {
					release.value = newestAndroidRelease
					checkState.value = UpdateCheckState.UPDATE_AVAILABLE
				} else checkState.value = UpdateCheckState.UP_TO_DATE
			} catch (e: CancellationException) { throw e
			} catch (e: Exception) {
				Logger.e("ChangelogViewModel", "couldn't check for updates", e)
				checkError.value = e.message
				checkState.value = UpdateCheckState.FAILED
			}
		}
	}

	private fun downloadUpdate(update: GitHubRelease) {
		if (downloadState.value == UpdateDownloadState.DOWNLOADING) return
		val apkUrl = update.assets.firstOrNull { it.name == "Ondo.apk" }?.downloadUrl ?: return
		viewModelScope.launch {
			downloadState.value = UpdateDownloadState.DOWNLOADING
			downloadState.value = try {
				if (updater.download(apkUrl, releaseBuildNumber(update.tag) ?: error("Release has no build number"))) UpdateDownloadState.READY else UpdateDownloadState.FAILED
			} catch (e: CancellationException) { throw e
			} catch (e: Exception) {
				Logger.e("ChangelogViewModel", "couldn't download app update", e)
				UpdateDownloadState.FAILED
			}
		}
	}

	fun installOrRetryUpdate() {
		if (downloadState.value == UpdateDownloadState.DOWNLOADING) return
		val update = release.value ?: return
		if (downloadState.value in setOf(UpdateDownloadState.READY, UpdateDownloadState.PERMISSION_REQUIRED)) {
			try {
				downloadState.value = when (updater.install(releaseBuildNumber(update.tag) ?: return)) {
					UpdateInstallResult.Launched -> UpdateDownloadState.READY
					UpdateInstallResult.PermissionRequired -> UpdateDownloadState.PERMISSION_REQUIRED
					UpdateInstallResult.Unavailable -> UpdateDownloadState.FAILED
				}
			} catch (e: Exception) { checkError.value = e.message; downloadState.value = UpdateDownloadState.FAILED }
		} else downloadUpdate(update)
	}

	override fun onCleared() { updateClient.close(); super.onCleared() }

	fun clearRelease() {
		release.value = null
		downloadState.value = UpdateDownloadState.IDLE
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogSheet(viewModel: ChangelogViewModel) {
		val release by viewModel.release.collectAsStateWithLifecycle()
	val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()

	release?.let { release ->
		ModalBottomSheet(
			onDismissRequest = { viewModel.clearRelease() },
			sheetState = rememberBottomSheetState(
				initialValue = SheetValue.Hidden,
				enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
			)
		) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp)
			) {
				Column(
					modifier = Modifier.fillMaxWidth(),
					horizontalAlignment = Alignment.CenterHorizontally
				) {
					Text(
						text = stringResource(Res.string.title_update),
						style = MaterialTheme.typography.titleLarge,
						fontFamily = defaultFont(round = 100f)
					)
					Text(
						stringResource(Res.string.info_update, release.tag),
						style = MaterialTheme.typography.bodyMedium
					)
				}

				Spacer(Modifier.height(8.dp))

				Markdown(
					text = release.body,
					modifier = Modifier
						.heightIn(max = 400.dp)
						.fillMaxWidth()
						.clip(MaterialTheme.shapes.large)
						.background(MaterialTheme.colorScheme.surfaceContainerHigh)
						.verticalScroll(rememberScrollState())
						.padding(10.dp)
				)

				Spacer(Modifier.height(8.dp))
				when (downloadState) {
					UpdateDownloadState.DOWNLOADING -> Text("Downloading update…", style = MaterialTheme.typography.bodySmall)
					UpdateDownloadState.READY -> Text("Update downloaded. Android will ask you to confirm installation.", style = MaterialTheme.typography.bodySmall)
					UpdateDownloadState.PERMISSION_REQUIRED -> Text("Allow installs from Ondo, then tap Update App again.", style = MaterialTheme.typography.bodySmall)
					UpdateDownloadState.FAILED -> Text(viewModel.checkError.collectAsStateWithLifecycle().value ?: "Download failed. Tap Update App to retry.", style = MaterialTheme.typography.bodySmall)
					UpdateDownloadState.IDLE -> Text("Download size: ${release.assets.firstOrNull { it.name == "Ondo.apk" }?.size?.div(1024 * 1024)} MB".replace("null MB", "unknown"), style = MaterialTheme.typography.bodySmall)
				}
				Spacer(Modifier.weight(1f))

				Button(
					onClick = { viewModel.installOrRetryUpdate() },
					modifier = Modifier.fillMaxWidth(),
					shape = ContinuousCapsule,
					enabled = downloadState != UpdateDownloadState.DOWNLOADING
				) {
					Text(
						text = stringResource(Res.string.action_update_app),
						fontFamily = defaultFont(100)
					)
				}

			}
		}
	}

}
