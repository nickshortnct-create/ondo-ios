package com.nickshortnct.ondo.domain.manager

import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.domain.repositories.DbRepository
import com.nickshortnct.ondo.ondo.OndoConnect
import com.nickshortnct.ondo.ondo.bundledNavidromeUrl
import com.nickshortnct.ondo.ui.core.LoginUiState
import com.nickshortnct.ondo.util.Logger

class LoginManager(
    private val repository: DbRepository,
    private val sessionManager: SessionManager,
    private val connect: OndoConnect,
    private val syncManager: SyncManager,
    private val player: com.nickshortnct.ondo.shared.MediaPlayerViewModel,
    private val downloadManager: DownloadManager,
    private val sleepTimerManager: SleepTimerManager,
    private val preferences: PreferenceManager
) {
	val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
	private var logoutJob: kotlinx.coroutines.Job? = null

	val loginState: StateFlow<LoginUiState>
		field = MutableStateFlow<LoginUiState>(LoginUiState.Idle)

	// Pre-filled from the build-time-bundled server URL (see OndoApi.kt/
	// build.gradle.kts) so the login screen never has to ask for it.
	// hasBundledInstance tells the UI whether to hide that field entirely;
	// if it's blank (e.g. a dev build with no ONDO_NAVIDROME_URL set) the
	// field stays visible as a fallback.
	val hasBundledInstance = bundledNavidromeUrl().isNotBlank()
	val instanceState = TextFieldState(bundledNavidromeUrl())
	val usernameState = TextFieldState()
	val passwordState = TextFieldState()

	var instanceError by mutableStateOf(false)
		private set
	var usernameError by mutableStateOf(false)
		private set
	var passwordError by mutableStateOf(false)
		private set

	fun validateInstance() {
		instanceError = instanceState.text.isBlank()
	}

	fun validateUsername() {
		usernameError = usernameState.text.isBlank()
	}

	fun validatePassword() {
		passwordError = passwordState.text.isBlank()
	}

	fun validateStuff(): Boolean {
		validateInstance()
		validateUsername()
		validatePassword()
		return !instanceError && !usernameError && !passwordError
	}

	init {
		loadUser()
	}

	fun loadUser() {
		scope.launch {
			if (sessionManager.isLoggedIn.value) {
				loginState.value = LoginUiState.Success
			} else {
				loginState.value = LoginUiState.Idle
			}
		}
	}

	fun login(): Boolean {
		if (logoutJob?.isActive == true) return false
		if (!validateStuff()) return false

		scope.launch {
			loginState.value = LoginUiState.Loading

			try {
				val rawUrl = instanceState.text.toString().trim().removeSuffix("/")
				val url = if (rawUrl.startsWith("https://") || rawUrl.startsWith("http://")) {
					rawUrl
				} else {
					"https://$rawUrl"
				}

				val username = usernameState.text.toString()
				val password = passwordState.text.toString()

				// The background sync loop waits until this login sync is done.
				syncManager.beginInitialSync()
				sessionManager.login(url, username, password)

				// Fire-and-forget: sign into Ondo Connect with the same
				// credentials the user just used, so it's never a separate
				// step. signInWithMainAccount() swallows its own failures
				// (Connect is a bonus feature, must never block login).
				scope.launch {
					try {
						connect.signInWithMainAccount(username, password)
					} catch (e: CancellationException) {
						throw e
					} catch (e: Exception) {
						Logger.w("LoginManager", "Connect auto sign-in failed", e)
					}
				}

				syncManager.runInitialSync { progress, message ->
					loginState.value = LoginUiState.Syncing(progress, message)
				}.onSuccess {
					loginState.value = LoginUiState.Success
					passwordState.clearText()
				}.onFailure { e ->
					loginState.value = LoginUiState.Error(e as? Exception ?: Exception(e))
				}

			} catch (e: CancellationException) { throw e
			} catch (e: Exception) {
				loginState.value = LoginUiState.Error(e)
			} finally {
				syncManager.endInitialSync()
			}
		}

		return true
	}

	fun logout(deleteOfflineFiles: Boolean = false): kotlinx.coroutines.Job {
		logoutJob?.takeIf { it.isActive }?.let { return it }
		val existingWork = scope.coroutineContext[kotlinx.coroutines.Job]!!.children.toList()
		logoutJob = scope.launch {
			loginState.value = LoginUiState.Loading
			existingWork.forEach { it.cancel() }
			existingWork.forEach { it.join() }
			passwordState.clearText()
			usernameState.clearText()
			connect.logout()
			sleepTimerManager.stopTimer()
			sessionManager.logout()
			preferences.customHeaders = ""
			preferences.setSearchHistory(emptyList())
			try {
				player.clearSession()
				kotlinx.coroutines.withContext(Dispatchers.IO) {
					downloadManager.clearSession(deleteOfflineFiles)
					syncManager.clearSessionCache()
				}
				loginState.value = LoginUiState.Idle
			} catch (e: CancellationException) { throw e
			} catch (e: Exception) {
				Logger.e("LoginManager", "Session cleanup failed", e)
				loginState.value = LoginUiState.Error(e)
			}
		}
		return logoutJob!!
	}
}
