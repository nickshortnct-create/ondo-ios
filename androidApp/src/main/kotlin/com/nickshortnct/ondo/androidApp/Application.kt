package com.nickshortnct.ondo.androidApp

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import coil3.SingletonImageLoader
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import com.nickshortnct.ondo.androidApp.di.AndroidResourceProvider
import com.nickshortnct.ondo.androidApp.wear.WearAccountPublisher
import com.nickshortnct.ondo.androidApp.wear.WearStatePublisher
import com.nickshortnct.ondo.shared.PlaybackServiceExtensions
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.nickshortnct.ondo.di.ActivityProvider
import com.nickshortnct.ondo.di.ResourceProvider
import com.nickshortnct.ondo.di.initKoin
import com.nickshortnct.ondo.di.initializeSingletonImageLoader
import com.nickshortnct.ondo.util.Logger
import kotlin.system.exitProcess

class Application : android.app.Application() {
	// Independent of the Koin-provided ActivityProvider on purpose: this runs
	// inside an uncaught-exception handler, so it can't risk a DI lookup that
	// might itself throw (or race Koin's init) while already handling a crash.
	@Volatile
	private var hasResumedActivity = false

	override fun onCreate() {
		super.onCreate()

		if (isCrashProcess()) {
			return
		}

		// Before anything else can log -- including Koin's own androidLogger()
		// and every Logger.* call this session makes, in the foreground or
		// headless in a car.
		Logger.install(this)

		registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
			override fun onActivityResumed(activity: Activity) {
				hasResumedActivity = true
			}

			override fun onActivityPaused(activity: Activity) {
				hasResumedActivity = false
			}

			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
			override fun onActivityStarted(activity: Activity) {}
			override fun onActivityStopped(activity: Activity) {}
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
			override fun onActivityDestroyed(activity: Activity) {}
		})

		val platformDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()

		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			// Written before either branch below so a headless crash -- which
			// now defers to the platform handler and may kill the process with
			// no CrashActivity ever shown -- still leaves a durable record.
			Logger.e("UncaughtException", "uncaught on ${thread.name}", throwable)
			Logger.flushBlocking()

			// PlaybackService runs headless (no Activity) whenever Android Auto
			// binds it directly, or when playback just continues in the
			// background -- launching a full-screen CrashActivity from there is
			// unreliable (background activity-start restrictions) and the
			// exitProcess(1) below would still kill the bound service instantly
			// either way, which tears the MediaSession down out from under the
			// car mid-connection. Auto has no recovery for that beyond "<app>
			// doesn't seem to be working right now" and losing the Now Playing
			// screen. Defer to the platform's own crash handling instead, which
			// lets the service die and get cleaned up the normal way.
			if (!hasResumedActivity) {
				platformDefaultHandler?.uncaughtException(thread, throwable)
				return@setDefaultUncaughtExceptionHandler
			}

			try {
				val intent = Intent(this, CrashActivity::class.java).apply {
					putExtra("stacktrace", Log.getStackTraceString(throwable))
					flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
				}
				startActivity(intent)
			} catch (e: Exception) {
				Log.e("Application", "failed to start CrashActivity", e)
			} finally {
				exitProcess(1)
			}
		}

		initKoin {
			modules(module(createdAtStart = true) {
				singleOf(::ActivityProvider)
				single<ResourceProvider> {
					AndroidResourceProvider()
				}
			})
			androidContext(this@Application)
			androidLogger()
		}

		// Must not wait for App(): a headless start (Android Auto, the playback service, a
		// widget) that loads an image first would otherwise lock in Coil's default loader,
		// with no saved covers, for the whole process.
		SingletonImageLoader.setSafe { initializeSingletonImageLoader(it) }

		// Watch support: mirror Now Playing while the playback service runs, and
		// keep the watch's copy of the music-server login current.
		PlaybackServiceExtensions.factories += { context, session -> WearStatePublisher(context, session) }
		MainScope().launch {
			runCatching { WearAccountPublisher.observe(this@Application) }
				.onFailure { Logger.e("Application", "watch login sync stopped", it) }
		}
	}

	private fun isCrashProcess(): Boolean {
		val processName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			getProcessName()
		} else {
			val pid = android.os.Process.myPid()
			val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
			am.runningAppProcesses?.find { it.pid == pid }?.processName
		}
		return processName?.endsWith(":crash") == true
	}
}
