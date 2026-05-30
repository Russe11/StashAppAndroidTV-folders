package com.github.damontecres.stashapp

import android.app.Application
import android.content.res.Resources
import android.graphics.Typeface
import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.annotation.FontRes
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.preference.PreferenceManager
import androidx.room.Room
import com.github.damontecres.stashapp.data.room.AppDatabase
import com.github.damontecres.stashapp.data.room.MIGRATION_4_TO_5
import com.github.damontecres.stashapp.folders.data.MIGRATION_5_TO_6
import com.github.damontecres.stashapp.folders.data.MIGRATION_6_TO_7
import com.github.damontecres.stashapp.folders.data.MIGRATION_7_TO_8
import com.github.damontecres.stashapp.folders.data.MIGRATION_8_TO_9
import com.github.damontecres.stashapp.folders.data.MIGRATION_9_TO_10
import com.github.damontecres.stashapp.folders.data.MIGRATION_10_TO_11
import com.github.damontecres.stashapp.navigation.NavigationManager
import com.github.damontecres.stashapp.util.AppUpgradeHandler
import com.github.damontecres.stashapp.util.QueryEngine
import com.github.damontecres.stashapp.util.StashServer
import com.github.damontecres.stashapp.util.Version
import dev.b3nedikt.restring.Restring
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.dialog
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import timber.log.Timber

class StashApplication : Application() {
    @OptIn(ExperimentalComposeRuntimeApi::class)
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy
                    .Builder()
                    .detectNetwork()
                    .penaltyLog()
                    .penaltyDeathOnNetwork()
                    .build(),
            )
//            StrictMode.setVmPolicy(
//                StrictMode.VmPolicy
//                    .Builder()
//                    .detectAll()
//                    .penaltyLog()
//                    .build(),
//            )
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        application = this

        val pkgInfo = packageManager.getPackageInfo(packageName, 0)
        val versionNameStr = pkgInfo.versionName ?: "Unknown version"

        Restring.init(this)
        Composer.setDiagnosticStackTraceEnabled(BuildConfig.DEBUG)

        initAcra {
            buildConfigClass = BuildConfig::class.java
            reportFormat = StringFormat.JSON
            excludeMatchingSharedPreferencesKeys =
                listOf(
                    "^stashApiKey$",
                    "^stashUrl$",
                    "^server_.*",
                    "^apikey_.*",
                    "^pinCode$",
                    "^readOnlyMode\\.pinCode$",
                )
            reportContent =
                listOf(
                    ReportField.ANDROID_VERSION,
                    ReportField.APP_VERSION_CODE,
                    ReportField.APP_VERSION_NAME,
                    ReportField.BRAND,
                    // ReportField.BUILD_CONFIG,
                    // ReportField.BUILD,
                    ReportField.CUSTOM_DATA,
                    ReportField.LOGCAT,
                    ReportField.PHONE_MODEL,
                    ReportField.PRODUCT,
                    ReportField.REPORT_ID,
                    ReportField.SHARED_PREFERENCES,
                    ReportField.STACK_TRACE,
                    ReportField.USER_COMMENT,
                    ReportField.USER_CRASH_DATE,
                )
            dialog {
                text =
                    "StashAppAndroidTV ($versionNameStr) has crashed! Would you like to attempt to " +
                    "send a crash report to your Stash server?" +
                    "\n\nThis will only work if you have already installed the companion plugin."
                title = "StashAppAndroidTV Crash Report"
                positiveButtonText = "Send"
                negativeButtonText = "Do not send"
            }
            reportSendFailureToast = "Crash report failed to send"
            reportSendSuccessToast = "Attempted to send crash report!"
        }
        ACRA.errorReporter.putCustomData("SDK_INT", Build.VERSION.SDK_INT.toString())

        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleObserverImpl())

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val currentVersion = prefs.getString(VERSION_NAME_CURRENT_KEY, null)
        val currentVersionCode = prefs.getLong(VERSION_CODE_CURRENT_KEY, -1)

        val newVersionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                pkgInfo.versionCode.toLong()
            }
        if (pkgInfo.versionName != currentVersion || newVersionCode != currentVersionCode) {
            Log.i(
                TAG,
                "App installed: $currentVersion=>${pkgInfo.versionName} ($currentVersionCode=>$newVersionCode",
            )
            prefs.edit {
                putString(VERSION_NAME_PREVIOUS_KEY, currentVersion)
                putLong(VERSION_CODE_PREVIOUS_KEY, currentVersionCode)
                putString(VERSION_NAME_CURRENT_KEY, pkgInfo.versionName)
                putLong(VERSION_CODE_CURRENT_KEY, newVersionCode)
            }
            if (currentVersion != null) {
                try {
                    AppUpgradeHandler(
                        this@StashApplication,
                        Version.fromString(currentVersion),
                        Version.fromString(pkgInfo.versionName!!),
                    ).run()
                } catch (ex: Exception) {
                    Log.e(TAG, "Exception during app upgrade", ex)
                }
            }
        }

        // Move any pre-existing plaintext server URL/API-key entries into the
        // encrypted store and strip them from plaintext default prefs.
        try {
            StashServer.migratePlaintextSecrets(this)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to migrate plaintext secrets to encrypted store", ex)
        }

        setupDB()
        // Folders destination: install the indexer host so its UI bridge can
        // observe sync progress and dispatch force-resync as soon as the user
        // navigates to the Folders pane. Kicking off the actual delta sync is
        // deferred until a server is picked (see `currentServer.observeForever`
        // wiring below if/when needed).
        com.github.damontecres.stashapp.folders.sync.LibraryIndexerHost.install(
            daoProvider = { database.folderDao() },
        )
        // NG live-refresh: install the host so it can run the `entityChanged` subscription while
        // the app is foreground. Capability-gated inside the repository — a no-op on a server
        // that doesn't advertise `entityChanged`, leaving the existing poll/delta-sync in charge.
        com.github.damontecres.stashapp.util.realtime.LiveRefreshHost.install(
            daoProvider = { database.folderDao() },
        )
        // NG deviceBus presence: install the host so it can register this device + report presence
        // while foreground. Double-gated (capability `deviceBus` AND the opt-in toggle, default
        // OFF) inside the repository — until the user opts in this device never registers and is
        // invisible to others.
        com.github.damontecres.stashapp.util.realtime.DeviceBusHost.install(this)
    }

    override fun getResources(): Resources = Restring.wrapResources(applicationContext, super.getResources())

    private fun setupDB() {
        val dbName = getString(R.string.app_name)
        database =
            Room
                .databaseBuilder(this, AppDatabase::class.java, dbName)
                .addMigrations(
                    MIGRATION_4_TO_5,
                    MIGRATION_5_TO_6,
                    MIGRATION_6_TO_7,
                    MIGRATION_7_TO_8,
                    MIGRATION_8_TO_9,
                    MIGRATION_9_TO_10,
                    MIGRATION_10_TO_11,
                ).fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        Log.w(TAG, "onLowMemory")
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        Log.w(TAG, "onTrimMemory: level=$level")
        super.onTrimMemory(level)
    }

    inner class LifecycleObserverImpl : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            Log.v(TAG, "LifecycleObserverImpl.onStart")
            // App is foreground: start the live-refresh WS (capability-gated inside the host).
            com.github.damontecres.stashapp.util.realtime.LiveRefreshHost.onForeground()
            // And (re)start deviceBus presence (double-gated: capability + opt-in inside the host).
            com.github.damontecres.stashapp.util.realtime.DeviceBusHost.onForeground()
        }

        override fun onPause(owner: LifecycleOwner) {
            Log.v(TAG, "LifecycleObserverImpl.onPause")
            StashExoPlayer.releasePlayer()
        }

        override fun onStop(owner: LifecycleOwner) {
            Log.v(TAG, "LifecycleObserverImpl.onStop")
            StashExoPlayer.releasePlayer()
            // App is backgrounded: drop the live-refresh WS until the next foreground.
            com.github.damontecres.stashapp.util.realtime.LiveRefreshHost.onBackground()
            // And stop deviceBus presence (unregisters → OFFLINE; reconnects next foreground).
            com.github.damontecres.stashapp.util.realtime.DeviceBusHost.onBackground()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            Log.v(TAG, "LifecycleObserverImpl.onDestroy")
            StashExoPlayer.releasePlayer()
        }
    }

    companion object {
        private lateinit var application: StashApplication
        private lateinit var database: AppDatabase
        lateinit var navigationManager: NavigationManager
        var currentServer: StashServer? = null

        private val fontCache = mutableMapOf<Int, Typeface>()

        fun getApplication(): StashApplication = application

        fun requireCurrentServer(): StashServer = currentServer ?: throw QueryEngine.StashNotConfiguredException()

        fun getFont(
            @FontRes fontId: Int,
        ): Typeface {
            return fontCache.getOrPut(fontId) {
                return ResourcesCompat.getFont(getApplication(), fontId)!!
            }
        }

        fun getDatabase(): AppDatabase = database

        const val TAG = "StashApplication"
        const val VERSION_NAME_PREVIOUS_KEY = "VERSION_NAME_PREVIOUS_NAME"
        const val VERSION_CODE_PREVIOUS_KEY = "VERSION_CODE_PREVIOUS_NAME"
        const val VERSION_NAME_CURRENT_KEY = "VERSION_CURRENT_KEY"
        const val VERSION_CODE_CURRENT_KEY = "VERSION_CODE_CURRENT_KEY"
    }
}
