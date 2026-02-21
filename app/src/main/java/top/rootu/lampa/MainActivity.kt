package top.rootu.lampa

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.DialogInterface.BUTTON_NEGATIVE
import android.content.DialogInterface.BUTTON_NEUTRAL
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.gotev.speech.GoogleVoiceTypingDisabledException
import net.gotev.speech.Logger
import net.gotev.speech.Speech
import net.gotev.speech.SpeechDelegate
import net.gotev.speech.SpeechRecognitionNotAvailable
import net.gotev.speech.SpeechUtil
import net.gotev.speech.ui.SpeechProgressView
import org.json.JSONException
import org.json.JSONObject
import top.rootu.lampa.browser.Browser
import top.rootu.lampa.browser.SysView
import top.rootu.lampa.channels.ChannelManager.getChannelDisplayName
import top.rootu.lampa.channels.WatchNext
import top.rootu.lampa.content.LampaProvider
import top.rootu.lampa.helpers.Backup
import top.rootu.lampa.helpers.Backup.backupSettings
import top.rootu.lampa.helpers.Backup.loadFromBackup
import top.rootu.lampa.helpers.Backup.validateStorageBackup
import top.rootu.lampa.helpers.Helpers
import top.rootu.lampa.helpers.Helpers.debugLogIntentData
import top.rootu.lampa.helpers.Helpers.dp2px
import top.rootu.lampa.helpers.Helpers.getJson
import top.rootu.lampa.helpers.Helpers.isAndroidTV
import top.rootu.lampa.helpers.Helpers.isTvContentProviderAvailable
import top.rootu.lampa.helpers.Helpers.isValidJson
import top.rootu.lampa.helpers.PermHelpers
import top.rootu.lampa.helpers.PermHelpers.hasMicPermissions
import top.rootu.lampa.helpers.PermHelpers.verifyMicPermissions
import top.rootu.lampa.helpers.Prefs
import top.rootu.lampa.helpers.Prefs.FAV
import top.rootu.lampa.helpers.Prefs.addUrlHistory
import top.rootu.lampa.helpers.Prefs.appBrowser
import top.rootu.lampa.helpers.Prefs.appLang
import top.rootu.lampa.helpers.Prefs.appPlayer
import top.rootu.lampa.helpers.Prefs.appPrefs
import top.rootu.lampa.helpers.Prefs.appUrl
import top.rootu.lampa.helpers.Prefs.bookToRemove
import top.rootu.lampa.helpers.Prefs.clearPending
import top.rootu.lampa.helpers.Prefs.contToRemove
import top.rootu.lampa.helpers.Prefs.defPrefs
import top.rootu.lampa.helpers.Prefs.firstRun
import top.rootu.lampa.helpers.Prefs.histToRemove
import top.rootu.lampa.helpers.Prefs.lampaSource
import top.rootu.lampa.helpers.Prefs.lastPlayedPrefs
import top.rootu.lampa.helpers.Prefs.likeToRemove
import top.rootu.lampa.helpers.Prefs.lookToRemove
import top.rootu.lampa.helpers.Prefs.migrate
import top.rootu.lampa.helpers.Prefs.schdToRemove
import top.rootu.lampa.helpers.Prefs.thrwToRemove
import top.rootu.lampa.helpers.Prefs.tvPlayer
import top.rootu.lampa.helpers.Prefs.urlHistory
import top.rootu.lampa.helpers.Prefs.viewToRemove
import top.rootu.lampa.helpers.Prefs.wathToAdd
import top.rootu.lampa.helpers.Prefs.wathToRemove
import top.rootu.lampa.helpers.getAppVersion
import top.rootu.lampa.helpers.hideSystemUI
import top.rootu.lampa.helpers.isAmazonDev
import top.rootu.lampa.helpers.isSafeForUse
import top.rootu.lampa.helpers.isTvBox
import top.rootu.lampa.models.LAMPA_CARD_KEY
import top.rootu.lampa.models.LampaCard
import top.rootu.lampa.net.HttpHelper
import top.rootu.lampa.sched.Scheduler
import java.util.Locale
import java.util.regex.Pattern


class MainActivity : BaseActivity(), Browser.Listener {
    // Local properties
    private var browser: Browser? = null
    private var browserInitComplete = false
    private var isMenuVisible = false
    private lateinit var loaderView: LottieAnimationView
    private lateinit var resultLauncher: ActivityResultLauncher<Intent>
    private lateinit var speechLauncher: ActivityResultLauncher<Intent>
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var playerStateManager: PlayerStateManager

    // Data class for menu items
    private data class MenuItem(
        val title: String,
        val action: String,
        val icon: Int
    )

    // Class for URL history
    private class UrlAdapter(context: Context) :
        ArrayAdapter<String>(
            context,
            R.layout.lampa_dropdown_item, // Custom dropdown layout
            android.R.id.text1, // ID of the TextView in the custom layout
            context.urlHistory.toMutableList() // Load URL history
        )

    companion object {
        // Constants
        const val VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE = 96
        private const val TAG = "APP_MAIN"
        private const val JS_SUCCESS = "SUCCESS"
        private const val JS_FAILURE = "FAILED"
        private const val IP4_DIG = "([01]?\\d?\\d|2[0-4]\\d|25[0-5])"
        private const val IP4_REGEX = "(${IP4_DIG}\\.){3}${IP4_DIG}"
        private const val IP6_DIG = "[0-9A-Fa-f]{1,4}"
        private const val IP6_REGEX =
            "((${IP6_DIG}:){7}${IP6_DIG}|(${IP6_DIG}:){1,7}:|:(:${IP6_DIG}){1,7}|(${IP6_DIG}::?){1,6}${IP6_DIG})"
        private const val DOMAIN_REGEX = "([-A-Za-z\\d]+\\.)+[-A-Za-z]{2,}"
        private const val URL_REGEX = "^https?://" + // Mandatory protocol
                "(\\[${IP6_REGEX}]|${IP4_REGEX}|${DOMAIN_REGEX})" +  // IPv6, IPv4, or domain
                "(:\\d+)?" +                      // Optional port
                "(/[-\\w@:%._+~#=&]*(/[-\\w@:%._+~#=&]*)*)?" + // Optional path
                "(\\?[\\w@:%._+~#=&-]*)?" +       // Optional query string
                "(#[\\w-]*)?" +                   // Optional fragment
                "$"

        // Player Packages
        private val MX_PACKAGES = setOf(
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro",
            "com.mxtech.videoplayer.beta",
        )
        private val UPLAYER_PACKAGES = setOf(
            "com.uapplication.uplayer",
            "com.uapplication.uplayer.beta",
        )
        private val VIMU_PACKAGES = setOf(
            "net.gtvbox.videoplayer",
            "net.gtvbox.vimuhd",
            "net.gtvbox.vimu",
        )
        private val DDD_PLAYER_PACKAGES = setOf(
            "top.rootu.dddplayer"
        )
        private val EXO_PLAYER_PACKAGES = setOf(
            "com.google.android.exoplayer2.demo",
            "androidx.media3.demo.main",
        )
        private val PLAYERS_BLACKLIST = setOf(
            "com.android.gallery3d",
            "com.android.tv.frameworkpackagestubs",
            "com.estrongs.android.pop",
            "com.estrongs.android.pop.pro",
            "com.ghisler.android.totalcommander",
            "com.google.android.apps.photos",
            "com.google.android.tv.frameworkpackagestubs",
            "com.instantbits.cast.webvideo",
            "com.lonelycatgames.xplore",
            "com.mitv.videoplayer",
            "com.mixplorer.silver",
            "com.opera.browser",
            "com.tcl.browser",
            "nextapp.fx",
            "org.droidtv.contentexplorer",
            "pl.solidexplorer2",
        )
        private val URL_PATTERN = Pattern.compile(URL_REGEX)
        private val listenerMutex = Mutex()

        // Properties
        var LAMPA_URL: String = ""
        var SELECTED_PLAYER: String? = ""
        var SELECTED_BROWSER: String? = "SysView"
        var delayedVoidJsFunc = mutableListOf<List<String>>()
        var playerTimeCode: String = "continue"
        var playerAutoNext: Boolean = true
        var proxyTmdbEnabled: Boolean = false
        var lampaActivity: String = "{}" // JSON
        lateinit var urlAdapter: ArrayAdapter<String>
    }

    inline fun <reified T> T.logDebug(message: String) {
        if (BuildConfig.DEBUG)
            Log.d(T::class.simpleName, message)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LAMPA_URL = appUrl
        SELECTED_PLAYER = appPlayer
        logDebug("onCreate LAMPA_URL: $LAMPA_URL")
        playerStateManager = PlayerStateManager(this).apply {
            purgeOldStates()
        }

        setupActivity()
        setupBrowser()
        setupUI()
        setupIntents()

        if (firstRun) {
            CoroutineScope(Dispatchers.Default).launch {
                logDebug("First run scheduleUpdate(sync: true)")
                Scheduler.scheduleUpdate(true)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (!isTvBox) setupFab()
        if (browserInitComplete)
            browser?.resumeTimers()
        if (browser.isSafeForUse()) {
            lifecycleScope.launch {
                syncBookmarks()
            }
        }
    }

    override fun onPause() {
        if (browserInitComplete)
            browser?.pauseTimers()
        super.onPause()
    }

    override fun onDestroy() {
        if (browserInitComplete) {
            browser?.apply {
                if (!isDestroyed) {
                    destroy()
                }
            }
        }
        try {
            Speech.getInstance()?.shutdown()
        } catch (_: Exception) {
        }
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        if (browserInitComplete)
            browser?.apply {
                pauseTimers()
                clearCache(true)
            }
        super.onUserLeaveHint()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lifecycleScope.launch {
            delay(300)
            hideSystemUI()
            showFab(true)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU
            || keyCode == KeyEvent.KEYCODE_TV_CONTENTS_MENU
            || keyCode == KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU
        ) {
            showMenuDialog()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showMenuDialog()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onBrowserInitCompleted() {
        browserInitComplete = true
        HttpHelper.userAgent = browser?.getUserAgentString() + " lampa_client"
        browser?.apply {
            setUserAgentString(HttpHelper.userAgent)
            setBackgroundColor(ContextCompat.getColor(baseContext, R.color.lampa_background))
            addJavascriptInterface(AndroidJS(this@MainActivity, this), "AndroidJS")
        }
        if (LAMPA_URL.isEmpty()) {
            showUrlInputDialog()
        } else {
            browser?.loadUrl(LAMPA_URL)
        }
    }

    override fun onBrowserPageFinished(view: ViewGroup, url: String) {
        if (migrate) {
            migrateSettings()
        }
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.VISIBLE
        }
        loaderView.visibility = View.GONE

        lifecycleScope.launch {
            syncLanguage()
        }
        if (url.trimEnd('/').equals(LAMPA_URL, true)) {
            val waitDelay = 1000L
            processIntent(intent, waitDelay)
            lifecycleScope.launch {
                listenerCleanupAndSetup()
                delay(waitDelay)
                syncStorage()
                syncBookmarks()
                val itemsToProcess = delayedVoidJsFunc.toList()
                delayedVoidJsFunc.clear()
                for (item in itemsToProcess) {
                    runVoidJsFunc(item[0], item[1])
                }
                withContext(Dispatchers.Default) {
                    delay(waitDelay)
                    Scheduler.scheduleUpdate(false)
                }
            }
        }
    }

    private fun isAfterEndCreditsPosition(positionMillis: Long, duration: Long): Boolean {
        return duration > 0 && positionMillis >= duration * VIDEO_COMPLETED_DURATION_MAX_PERCENTAGE / 100
    }

    private fun setupActivity() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        enableEdgeToEdge()
        if (VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        onBackPressedDispatcher.addCallback {
            if (browser?.canGoBack() == true) {
                runVoidJsFunc("window.history.back", "")
            }
        }
    }

    private fun setupBrowser() {
        appBrowser = "SysView"
        SELECTED_BROWSER = "SysView"
        useSystemWebView()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun useSystemWebView() {
        setContentView(R.layout.activity_webview)
        loaderView = findViewById(R.id.loaderView)
        browser = SysView(this, R.id.webView)
        browser?.initialize()
    }

    private fun handleSpeechResult(result: androidx.activity.result.ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val spokenText: String? =
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.let { results ->
                    results[0]
                }
            if (spokenText != null) {
                runVoidJsFunc("window.voiceResult", "'" + spokenText.replace("'", "\\'") + "'")
            }
        }
    }

    private fun handlePlayerResult(result: androidx.activity.result.ActivityResult) {
        val data: Intent? = result.data
        val videoUrl: String = data?.data?.toString() ?: "null"
        val resultCode = result.resultCode

        data?.let { intent ->
            when (intent.action) {
                "com.mxtech.intent.result.VIEW" -> handleMxPlayerResult(intent, resultCode, videoUrl)
                "org.videolan.vlc.player.result" -> handleVlcPlayerResult(intent, resultCode, videoUrl)
                "is.xyz.mpv.MPVActivity.result" -> handleMpvPlayerResult(intent, resultCode, videoUrl)
                "com.uapplication.uplayer.result", "com.uapplication.uplayer.beta.result" -> handleUPlayerResult(intent, resultCode, videoUrl)
                "net.gtvbox.videoplayer.result", "net.gtvbox.vimuhd.result" -> handleViMuPlayerResult(intent, resultCode, videoUrl)
                "top.rootu.dddplayer.intent.result.VIEW" -> handleDddPlayerResult(intent, resultCode, videoUrl)
                else -> handleGenericPlayerResult(intent, resultCode, videoUrl)
            }
        }
    }

    private fun handleDddPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            val pos = intent.getLongExtra("position", 0L)
            val dur = intent.getLongExtra("duration", 0L)
            if (pos > 0 && dur > 0) {
                val ended = isAfterEndCreditsPosition(pos, dur)
                resultPlayer(videoUrl, pos.toInt(), dur.toInt(), ended)
            } else if (pos == 0L && dur == 0L) {
                resultPlayer(videoUrl, 0, 0, true)
            }
        }
    }
    private fun handleMxPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            when (intent.getStringExtra("end_by")) {
                "playback_completion" -> resultPlayer(videoUrl, 0, 0, true)
                "user" -> {
                    val pos = intent.getIntExtra("position", 0)
                    val dur = intent.getIntExtra("duration", 0)
                    if (pos > 0 && dur > 0) {
                        val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                        resultPlayer(videoUrl, pos, dur, ended)
                    }
                }
            }
        }
    }

    private fun handleVlcPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            val pos = intent.getLongExtra("extra_position", 0L)
            val dur = intent.getLongExtra("extra_duration", 0L)
            val url = if (videoUrl.isEmpty() || videoUrl == "null") intent.getStringExtra("extra_uri") ?: videoUrl else videoUrl
            if (pos > 0L && dur > 0L) {
                val ended = isAfterEndCreditsPosition(pos, dur)
                resultPlayer(url, pos.toInt(), dur.toInt(), ended)
            } else if (pos > 0L) {
                resultPlayer(url, pos.toInt(), pos.toInt(), true)
            }
        }
    }

    private fun handleMpvPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            val pos = intent.getIntExtra("position", 0)
            val dur = intent.getIntExtra("duration", 0)
            if (dur > 0) {
                val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                resultPlayer(videoUrl, pos, dur, ended)
            } else if (dur == 0 && pos == 0) {
                resultPlayer(videoUrl, 0, 0, true)
            }
        }
    }

    private fun handleUPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            val pos = intent.getLongExtra("position", 0L)
            val dur = intent.getLongExtra("duration", 0L)
            if (pos > 0L && dur > 0L) {
                val ended = intent.getBooleanExtra("isEnded", pos == dur) || isAfterEndCreditsPosition(pos, dur)
                resultPlayer(videoUrl, pos.toInt(), dur.toInt(), ended)
            }
        }
    }

    private fun handleViMuPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK || resultCode == 2 || resultCode == 3) {
            val pos = intent.getIntExtra("position", 0)
            val dur = intent.getIntExtra("duration", 0)
            if (pos > 0 && dur > 0) {
                val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
                resultPlayer(videoUrl, pos, dur, ended)
            }
        }
    }

    private fun handleGenericPlayerResult(intent: Intent, resultCode: Int, videoUrl: String) {
        if (resultCode == RESULT_OK) {
            val pos = intent.getIntExtra("position", 0)
            val dur = intent.getIntExtra("duration", 0)
            val ended = isAfterEndCreditsPosition(pos.toLong(), dur.toLong())
            resultPlayer(videoUrl, pos, dur, ended)
        }
    }

    private fun setupIntents() {
        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                handlePlayerResult(result)
            }
        speechLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                handleSpeechResult(result)
            }
    }

    private fun setupUI() {
        hideSystemUI()
    }

    private fun migrateSettings() {
        lifecycleScope.launch {
            restoreStorage { callback ->
                if (callback.contains(JS_SUCCESS, true)) {
                    recreate()
                } else {
                    App.toast(R.string.settings_rest_fail)
                }
            }
            this@MainActivity.migrate = false
        }
    }

    suspend fun listenerCleanupAndSetup() {
        listenerMutex.withLock {
            cleanupListener()
            delay(1000)
            setupListener()
        }
    }

    private fun setupListener() {
        browser?.evaluateJavascript(
            """
            (function() {
                if (typeof window._androidStorageListener === 'undefined') {
                    window._androidStorageListener = function(o) {
                        AndroidJS.storageChange(JSON.stringify(o));
                    };
                    if (Lampa && Lampa.Storage && Lampa.Storage.listener) {
                        if (Lampa.Storage.listener.follow) {
                            Lampa.Storage.listener.follow('change', window._androidStorageListener);
                        } else {
                            Lampa.Storage.listener.add('change', window._androidStorageListener);
                        }
                        return 'LISTENER_ADDED';
                    }
                }
                return 'LISTENER_EXISTS';
            })()
            """.trimIndent()
        ) { }
    }

    fun cleanupListener() {
        browser?.evaluateJavascript(
            """
            (function() {
                if (typeof window._androidStorageListener !== 'undefined') {
                    if (Lampa && Lampa.Storage && Lampa.Storage.listener) {
                        Lampa.Storage.listener.remove('change', window._androidStorageListener);
                    }
                    delete window._androidStorageListener;
                    return 'LISTENER_REMOVED';
                }
                return 'NO_LISTENER';
            })()
            """.trimIndent()
        ) { }
    }

    private fun syncLanguage() {
        runJsStorageChangeField("language")
    }

    private fun syncStorage() {
        runJsStorageChangeField("activity", "{}")
        runJsStorageChangeField("player_timecode")
        runJsStorageChangeField("playlist_next")
        runJsStorageChangeField("source")
        runJsStorageChangeField("account_use")
        runJsStorageChangeField("recomends_list", "[]")
        runJsStorageChangeField("proxy_tmdb")
    }

    fun getLampaTmdbUrls() {
        lifecycleScope.launch {
            browser?.evaluateJavascript(
                """
                (function() {
                    if(window.appready) {
                        AndroidJS.storageChange(JSON.stringify({name: 'baseUrlApiTMDB', value: Lampa.TMDB.api('')}))
                        AndroidJS.storageChange(JSON.stringify({name: 'baseUrlImageTMDB', value: Lampa.TMDB.image('')}))
                    } else {
                        Lampa.Listener.follow('app', function (e) {
                        if(e.type =='ready')
                            AndroidJS.storageChange(JSON.stringify({name: 'baseUrlApiTMDB', value: Lampa.TMDB.api('')}))
                            AndroidJS.storageChange(JSON.stringify({name: 'baseUrlImageTMDB', value: Lampa.TMDB.image('')}))
                        })
                    }
                    return '${JS_SUCCESS}';
                })()
                """.trimIndent()
            ) { }
        }
    }

    private suspend fun syncBookmarks() = withContext(Dispatchers.Default) {
        if (!isTvContentProviderAvailable) return@withContext
        withContext(Dispatchers.Main) {
            runVoidJsFunc("Lampa.Favorite.init", "")
        }
        App.context.wathToAdd.forEach { item ->
            val lampaCard = App.context.FAV?.card?.find { it.id == item.id } ?: item.card
            lampaCard?.let { card ->
                card.fixCard()
                val id = card.id?.toIntOrNull()
                id?.let {
                    val params = if (card.type == "tv") "name: '${card.name}'" else "title: '${card.title}'"
                    withContext(Dispatchers.Main) {
                        runVoidJsFunc("Lampa.Favorite.add", "'${LampaProvider.LATE}', {id: $id, type: '${card.type}', source: '${card.source}', img: '${card.img}', $params}")
                    }
                }
            }
            delay(500)
        }
        listOf(
            LampaProvider.LATE to App.context.wathToRemove,
            LampaProvider.BOOK to App.context.bookToRemove,
            LampaProvider.LIKE to App.context.likeToRemove,
            LampaProvider.HIST to App.context.histToRemove,
            LampaProvider.LOOK to App.context.lookToRemove,
            LampaProvider.VIEW to App.context.viewToRemove,
            LampaProvider.SCHD to App.context.schdToRemove,
            LampaProvider.CONT to App.context.contToRemove,
            LampaProvider.THRW to App.context.thrwToRemove
        ).forEach { (category, items) ->
            items.forEach { id ->
                withContext(Dispatchers.Main) {
                    runVoidJsFunc("Lampa.Favorite.remove", "'$category', {id: $id}")
                }
                delay(500)
            }
        }
        App.context.clearPending()
    }

    private fun dumpStorage(callback: (String) -> Unit) {
        browser?.evaluateJavascript(
            """
            (function() {
                try {
                    AndroidJS.clear();
                    let count = 0;
                    for (let i = 0; i < localStorage.length; i++) {
                        const key = localStorage.key(i);
                        AndroidJS.set(key, localStorage.getItem(key));
                        count++;
                    }
                    return '${JS_SUCCESS}.' + count;
                } catch (error) {
                    return '${JS_FAILURE}: ' + error.message;
                }
            })()
            """.trimIndent()
        ) { result -> callback(result) }
    }

    private fun restoreStorage(callback: (String) -> Unit) {
        browser?.evaluateJavascript(
            """
            (function() {
                try {
                    AndroidJS.dump();
                    var len = AndroidJS.size();
                    for (i = 0; i < len; i++) {
                        var key = AndroidJS.key(i);
                        localStorage.setItem(key, AndroidJS.get(key));
                    }
                    return '${JS_SUCCESS}.' + len;
                } catch (error) {
                    return '${JS_FAILURE}: ' + error.message;
                }
            })()
            """.trimIndent()
        ) { result -> callback(result) }
    }

    private fun clearStorage() {
        browser?.evaluateJavascript("localStorage.clear()") { }
    }

    private fun processIntent(intent: Intent?, delay: Long = 0) {
        intent ?: return
        val sid = intent.getStringExtra("id") ?: intent.getIntExtra("id", -1).toString()
        val mediaType = intent.getStringExtra("media") ?: ""
        val source = intent.getStringExtra("source") ?: lampaSource.ifEmpty { "tmdb" }
        intent.data?.let { uri -> parseUriData(intent, uri, delay) }
        if (intent.getBooleanExtra("continueWatch", false)) {
            handleContinueWatch(intent, delay)
        } else if (sid != "-1" && mediaType.isNotEmpty()) {
            handleOpenCard(intent, sid, mediaType, source, delay)
        }
        intent.getStringExtra("cmd")?.let { cmd ->
            if (cmd == "open_settings") showMenuDialog()
        }
        browser?.setFocus()
    }

    private fun parseUriData(intent: Intent, uri: Uri, delay: Long = 0L) {
        if (uri.host?.contains("themoviedb.org") == true && uri.pathSegments.size >= 2) {
            val videoType = uri.pathSegments[0]
            val sid = "\\d+".toRegex().find(uri.pathSegments[1])?.value
            if (videoType in listOf("movie", "tv") && sid?.toIntOrNull() != null) {
                handleTmdbIntent(intent, videoType, sid, delay)
            }
        }
        when (intent.action) {
            "GLOBALSEARCH" -> handleGlobalSearch(intent, uri, delay)
            else -> handleChannelIntent(uri, delay)
        }
    }

    private fun handleTmdbIntent(intent: Intent, videoType: String, sid: String, delay: Long = 0) {
        val source = intent.getStringExtra("source") ?: "tmdb"
        val card = "{id: '$sid', source: 'tmdb'}"
        lifecycleScope.launch {
            openLampaContent("{id: '$sid', method: '$videoType', source: '$source', component: 'full', card: $card}", delay)
        }
    }

    private fun handleGlobalSearch(intent: Intent, uri: Uri, delay: Long = 0) {
        val sid = uri.lastPathSegment
        val videoType = intent.extras?.getString(SearchManager.EXTRA_DATA_KEY) ?: ""
        if (videoType in listOf("movie", "tv") && sid?.toIntOrNull() != null)
            handleTmdbIntent(intent, videoType, sid, delay)
    }

    private fun handleChannelIntent(uri: Uri, delay: Long = 0) {
        if (uri.encodedPath?.contains("update_channel") == true) {
            val channel = uri.encodedPath?.substringAfterLast("/") ?: ""
            val params = when (channel) {
                LampaProvider.RECS -> "{title: '${getString(R.string.title_main)} - ${lampaSource.uppercase(Locale.getDefault())}', component: 'main', source: '$lampaSource', url: ''}"
                LampaProvider.LIKE, LampaProvider.BOOK, LampaProvider.HIST -> "{title: '${getChannelDisplayName(channel)}', component: '${if (channel == "book") "bookmarks" else "favorite"}', type: '$channel', url: '', page: 1}"
                else -> ""
            }
            if (params.isNotEmpty()) {
                lifecycleScope.launch { openLampaContent(params, delay) }
            }
        }
    }

    private fun handleContinueWatch(intent: Intent, delay: Long = 0) {
        lifecycleScope.launch {
            val activityJson = intent.getStringExtra("lampaActivity") ?: return@launch
            if (isValidJson(activityJson)) {
                openLampaContent(activityJson, delay)
                delay(delay)
                if (intent.getBooleanExtra("android.intent.extra.START_PLAYBACK", false)) {
                    val card = getCardFromActivity(activityJson) ?: return@launch
                    val state = playerStateManager.findStateByCard(card) ?: return@launch
                    if (state.currentItem != null) {
                        val currentItem = state.playlist.getOrNull(state.currentIndex)
                        val playJsonObj = playerStateManager.getStateJson(state).apply {
                            currentItem?.title?.takeIf { it.isNotEmpty() }?.let { title ->
                                if (!has("title")) put("title", title)
                            }
                            currentItem?.timeline?.let { timeline ->
                                put("timeline", JSONObject().apply {
                                    put("hash", timeline.hash)
                                    put("time", timeline.time)
                                    put("duration", timeline.duration)
                                    put("percent", timeline.percent)
                                    timeline.profile?.let { put("profile", it) }
                                })
                                put("position", timeline.time.toLong())
                            }
                            put("from_state", true)
                        }
                        runPlayer(playJsonObj, "", activityJson)
                    }
                }
            }
        }
    }

    private fun handleOpenCard(intent: Intent?, sid: String, mediaType: String, source: String, delay: Long = 0) {
        val cardJson = intent?.getStringExtra(LAMPA_CARD_KEY) ?: "{id: '$sid', source: '$source'}"
        lifecycleScope.launch {
            openLampaContent("{id: '$sid', method: '$mediaType', source: '$source', component: 'full', card: $cardJson}", delay)
        }
    }

    private suspend fun openLampaContent(json: String, delay: Long = 0) {
        runVoidJsFunc("window.start_deep_link = ", json)
        delay(delay)
        runVoidJsFunc("Lampa.Controller.toContent", "")
        runVoidJsFunc("Lampa.Activity.push", json)
    }

    private fun showMenuDialog() {
        val menuItems = mutableListOf(
            MenuItem(
                title = if (isTvContentProviderAvailable) getString(R.string.update_chan_title) else if (isAndroidTV) getString(R.string.update_home_title) else getString(R.string.close_menu_title),
                action = "updateOrClose",
                icon = if (isAndroidTV) R.drawable.round_refresh_24 else R.drawable.round_close_24
            ),
            MenuItem(
                title = getString(R.string.change_url_title),
                action = "showUrlInputDialog",
                icon = R.drawable.round_link_24
            ),
            MenuItem(
                title = getString(R.string.backup_restore_title),
                action = "showBackupDialog",
                icon = R.drawable.round_settings_backup_restore_24
            ),
            MenuItem(
                title = getString(R.string.exit),
                action = "appExit",
                icon = R.drawable.round_exit_to_app_24
            )
        )

        val adapter = ImgArrayAdapter(this, menuItems.map { it.title }.toList(), menuItems.map { it.icon }.toList())
        val dialog = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.menu_title))
            setAdapter(adapter) { dialog, which ->
                dialog.dismiss()
                when (menuItems[which].action) {
                    "updateOrClose" -> if (isAndroidTV) Scheduler.scheduleUpdate(false)
                    "showUrlInputDialog" -> showUrlInputDialog()
                    "showBackupDialog" -> showBackupDialog()
                    "appExit" -> appExit()
                }
            }
            setOnDismissListener {
                isMenuVisible = false
                showFab(true)
            }
        }.create()
        showFullScreenDialog(dialog)
        isMenuVisible = true
    }

    private fun backupAllSettings() {
        lifecycleScope.launch {
            dumpStorage { callback ->
                if (callback.contains(JS_SUCCESS, true)) {
                    val itemsCount = callback.substringAfter("${JS_SUCCESS}.") .toIntOrNull() ?: 0
                    if (backupSettings(Prefs.APP_PREFERENCES) && backupSettings(Prefs.STORAGE_PREFERENCES) && validateStorageBackup(itemsCount)) {
                        App.toast(getString(R.string.settings_saved_toast, Backup.DIR.toString()))
                    } else App.toast(R.string.settings_save_fail)
                } else App.toast(R.string.settings_save_fail)
            }
        }
    }

    private fun restoreAppSettings() {
        if (loadFromBackup(Prefs.APP_PREFERENCES)) {
            recreate()
        } else App.toast(R.string.settings_rest_fail)
    }

    private fun restoreLampaSettings() {
        lifecycleScope.launch {
            if (loadFromBackup(Prefs.STORAGE_PREFERENCES)) {
                restoreStorage { callback ->
                    if (callback.contains(JS_SUCCESS, true)) {
                        recreate()
                    } else App.toast(R.string.settings_rest_fail)
                }
            } else App.toast(R.string.settings_rest_fail)
        }
    }

    private fun restoreDefaultSettings() {
        clearStorage()
        appPrefs.edit { clear() }
        defPrefs.edit { clear() }
        lastPlayedPrefs.edit { clear() }
        recreate()
    }

    private fun showBackupDialog() {
        val menuItems = listOf(
            MenuItem(getString(R.string.backup_all_title), "backupAllSettings", R.drawable.round_settings_backup_restore_24),
            MenuItem(getString(R.string.restore_app_title), "restoreAppSettings", R.drawable.round_refresh_24),
            MenuItem(getString(R.string.restore_storage_title), "restoreLampaSettings", R.drawable.round_refresh_24),
            MenuItem(getString(R.string.default_setting_title), "restoreDefaultSettings", R.drawable.round_close_24)
        )
        val adapter = ImgArrayAdapter(this, menuItems.map { it.title }.toList(), menuItems.map { it.icon }.toList())
        val dialog = AlertDialog.Builder(this).apply {
            setTitle(getString(R.string.backup_restore_title))
            setAdapter(adapter) { dialog, which ->
                dialog.dismiss()
                when (menuItems[which].action) {
                    "backupAllSettings" -> backupAllSettings()
                    "restoreAppSettings" -> restoreAppSettings()
                    "restoreLampaSettings" -> restoreLampaSettings()
                    "restoreDefaultSettings" -> restoreDefaultSettings()
                }
            }
        }.create()
        showFullScreenDialog(dialog)
        adapter.setSelectedItem(0)
        if (!PermHelpers.hasStoragePermissions(this)) PermHelpers.verifyStoragePermissions(this)
    }

    fun showUrlInputDialog(msg: String = "") {
        urlAdapter = UrlAdapter(this)
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = layoutInflater.inflate(R.layout.dialog_input_url, null, false)
        val tilt = view.findViewById<TextInputLayout>(R.id.tiltLampaUrl)
        val input = view.findViewById<AutoCompleteTV>(R.id.etLampaUrl)

        val builder = AlertDialog.Builder(this).apply {
            setTitle(R.string.input_url_title)
            setView(view)
            setPositiveButton(R.string.save) { _, _ -> handleSaveButtonClick(input) }
            setNegativeButton(R.string.cancel) { di, _ -> handleCancelButtonClick(di) }
            setNeutralButton(R.string.migrate) { _, _ -> }
        }
        val dialog = builder.create().apply {
            window?.apply {
                attributes = attributes.apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    verticalMargin = 0.1F
                }
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            }
        }
        showFullScreenDialog(dialog)
        setupInputField(input, tilt, msg, dialog, inputManager)
        dialog.getButton(BUTTON_NEUTRAL).setOnClickListener { handleMigrateButtonClick(dialog) }
    }

    private fun setupInputField(input: AutoCompleteTV?, tilt: TextInputLayout?, msg: String, dialog: AlertDialog?, inputManager: InputMethodManager) {
        input?.apply {
            setText(LAMPA_URL.ifEmpty { "http://lampa.mx" })
            if (msg.isNotEmpty()) {
                tilt?.isErrorEnabled = true
                tilt?.error = msg
            }
            setAdapter(urlAdapter)
            onPopupVisibilityChanged = { isOverlay ->
                dialog?.apply {
                    val visibility = if (isOverlay) View.INVISIBLE else View.VISIBLE
                    getButton(BUTTON_NEUTRAL)?.visibility = visibility
                    getButton(BUTTON_NEGATIVE)?.visibility = visibility
                }
            }
            setOnItemClickListener { _, _, _, _ -> dialog?.getButton(BUTTON_POSITIVE)?.requestFocus() }
            setOnKeyListener { view, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    if (isPopupShowing) dismissDropDown()
                    inputManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    true
                } else if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (!isPopupShowing) showDropDown()
                    inputManager.hideSoftInputFromWindow(view.windowToken, 0)
                    true
                } else false
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    inputManager.hideSoftInputFromWindow(windowToken, 0)
                    dialog?.getButton(BUTTON_POSITIVE)?.requestFocus()
                    true
                } else false
            }
        }
    }

    private fun handleSaveButtonClick(input: AutoCompleteTV?) {
        LAMPA_URL = input?.text.toString()
        if (isValidUrl(LAMPA_URL)) {
            if (appUrl != LAMPA_URL) {
                appUrl = LAMPA_URL
                addUrlHistory(LAMPA_URL)
                browser?.loadUrl(LAMPA_URL)
            } else browser?.loadUrl(LAMPA_URL)
        } else showUrlInputDialog()
    }

    private fun handleCancelButtonClick(dialog: DialogInterface) {
        dialog.cancel()
        if (LAMPA_URL.isEmpty() && appUrl.isEmpty()) appExit() else LAMPA_URL = appUrl
    }

    private fun handleMigrateButtonClick(dialog: AlertDialog) {
        lifecycleScope.launch {
            dumpStorage { callback ->
                if (callback.contains(JS_SUCCESS, true)) {
                    this@MainActivity.migrate = true
                    handleSaveButtonClick(dialog.findViewById(R.id.etLampaUrl))
                    dialog.dismiss()
                } else if (loadFromBackup(Prefs.STORAGE_PREFERENCES)) {
                    this@MainActivity.migrate = true
                    handleSaveButtonClick(dialog.findViewById(R.id.etLampaUrl))
                    dialog.dismiss()
                } else App.toast(R.string.settings_migrate_fail)
            }
        }
    }

    private fun isValidUrl(url: String) = URL_PATTERN.matcher(url).matches()

    fun appExit() {
        browser?.apply { clearCache(true); destroy() }
        finishAffinity()
    }

    fun setPlayerPackage(packageName: String, isIPTV: Boolean) {
        SELECTED_PLAYER = packageName.lowercase(Locale.getDefault())
        if (isIPTV) tvPlayer = SELECTED_PLAYER!! else appPlayer = SELECTED_PLAYER!!
    }

    fun runPlayer(jsonObject: JSONObject) {
        runPlayer(jsonObject, "", lampaActivity)
    }

    fun displaySpeechRecognizer() {
        verifyMicPermissions(this)
        var dialog: AlertDialog? = null
        val view = layoutInflater.inflate(R.layout.dialog_search, null, false)
        val etSearch = view.findViewById<AppCompatEditText>(R.id.etSearchQuery)
        val btnVoice = view.findViewById<AppCompatImageButton>(R.id.btnVoiceSearch)
        val inputManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager

        etSearch?.apply {
            setOnClickListener { inputManager?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT) }
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        }?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                dialog?.getButton(BUTTON_POSITIVE)?.performClick()
                true
            } else false
        }

        btnVoice?.setOnClickListener {
            val dots = view.findViewById<LinearLayout>(R.id.searchDots)
            val progress = view.findViewById<SpeechProgressView>(R.id.progress)
            progress.setBarMaxHeightsInDp(intArrayOf(40, 56, 38, 55, 35))
            if (hasMicPermissions(this)) {
                etSearch?.hint = getString(R.string.search_voice_hint)
                btnVoice.visibility = View.GONE
                dots?.visibility = View.VISIBLE
                startSpeech(getString(R.string.search_voice_hint), progress) { result, final, success ->
                    etSearch?.hint = ""
                    etSearch?.setText(result)
                    if (final) {
                        btnVoice.visibility = View.VISIBLE
                        dots?.visibility = View.GONE
                        if (success) dialog?.getButton(BUTTON_POSITIVE)?.requestFocus()
                    }
                }
            } else App.toast(R.string.search_requires_record_audio)
        }

        dialog = AlertDialog.Builder(this).setView(view).setPositiveButton(android.R.string.ok) { _, _ ->
            val query = etSearch.text.toString()
            if (query.isNotEmpty()) runVoidJsFunc("window.voiceResult", "'" + query.replace("'", "\\'") + "'")
        }.create().apply {
            window?.attributes = window?.attributes?.apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                verticalMargin = 0.1F
            }
        }
        showFullScreenDialog(dialog)
        btnVoice?.performClick()
    }

    private fun showFullScreenDialog(dialog: AlertDialog?) {
        dialog?.apply {
            window?.setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            show()
            window?.decorView?.let { decorView ->
                if (VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    decorView.windowInsetsController?.apply {
                        hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                }
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
    }

    private fun startSpeech(msg: String, progress: SpeechProgressView, onSpeech: (result: String, final: Boolean, success: Boolean) -> Unit): Boolean {
        if (hasMicPermissions(this)) {
            try {
                Speech.init(this, packageName)?.apply {
                    setLocale(Locale.getDefault())
                    startListening(progress, object : SpeechDelegate {
                        private var success = true
                        override fun onStartOfSpeech() {}
                        override fun onSpeechRmsChanged(value: Float) {}
                        override fun onSpeechPartialResults(results: List<String>) {
                            onSpeech(results.joinToString(" ").trim(), false, success)
                        }
                        override fun onSpeechResult(res: String) {
                            if (res.isEmpty()) success = false
                            onSpeech(res, true, success)
                        }
                    })
                }
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    private fun showFab(show: Boolean = true) {
        val fab: FloatingActionButton? = findViewById(R.id.fab)
        if (show && !isTvBox) {
            fab?.show()
            lifecycleScope.launch { delay(15000); fab?.hide() }
        } else fab?.hide()
    }

    private fun setupFab() {
        val fab: FloatingActionButton? = findViewById(R.id.fab)
        fab?.apply {
            setImageDrawable(AppCompatResources.getDrawable(context, R.drawable.lampa_logo_round))
            setOnClickListener { showMenuDialog(); showFab(false) }
        }
        if (!isMenuVisible) showFab(true)
    }

    private fun runJsStorageChangeField(name: String, default: String = "undefined") {
        runVoidJsFunc("AndroidJS.storageChange", "JSON.stringify({name: '${name}', value: Lampa.Storage.get('${name}', '${if (default == "undefined") "" else default}')})")
    }

    fun runVoidJsFunc(funcName: String, params: String) {
        if (browserInitComplete && loaderView.isGone) {
            val js = "(function(){try { ${funcName}(${params}); return '${JS_SUCCESS}'; } catch (e) { return '${JS_FAILURE}: ' + e.message; }})();"
            browser?.evaluateJavascript(js) { }
        } else delayedVoidJsFunc.add(listOf(funcName, params))
    }

    fun runPlayer(jsonObject: JSONObject, launchPlayer: String = "", activity: String? = null) {
        try {
            val playActivity = activity?.takeIf { it.isNotEmpty() } ?: lampaActivity
            val videoUrl = jsonObject.optString("url").takeIf { it.isNotBlank() } ?: return
            val isIPTV = jsonObject.optBoolean("iptv", false)
            val selectedPlayer = launchPlayer.takeIf { it.isNotBlank() } ?: if (isIPTV) tvPlayer else appPlayer
            val videoTitle = jsonObject.optString("title", if (isIPTV) "LAMPA TV" else "LAMPA video")
            val card = getCardFromActivity(playActivity)
            var headers = prepareHeaders(jsonObject)

            val state = if (jsonObject.optBoolean("from_state", false) && card != null) {
                playerStateManager.findStateByCard(card)?.also { headers = (it.extras["headers_array"] as? List<*>)?.filterIsInstance<String>()?.toTypedArray() ?: headers }
            } else {
                val playlist = playerStateManager.convertJsonToPlaylist(jsonObject.optJSONArray("playlist") ?: return)
                val currentIndex = playlist.indexOfFirst { it.url == videoUrl }.coerceAtLeast(0)
                playerStateManager.saveState(playActivity, playlist, currentIndex, videoUrl, 0L, currentIndex, mutableMapOf("isIPTV" to isIPTV, "headers_array" to (headers?.toList() ?: emptyList<String>())), card)
            }
            state?.let {
                val intent = Intent(Intent.ACTION_VIEW).apply { data = it.currentItem?.url?.toUri(); setDataAndType(it.currentItem?.url?.toUri(), "video/*") }
                val availablePlayers = getAvailablePlayers(intent)
                if (selectedPlayer != null && availablePlayers.any { it.activityInfo.packageName.equals(selectedPlayer, true) }) {
                    configurePlayerIntent(intent, selectedPlayer, videoTitle, isIPTV, it, headers)
                    launchPlayer(intent)
                } else showPlayerSelectionDialog(availablePlayers, jsonObject, isIPTV)
            }
        } catch (_: Exception) {}
    }

    private fun getAvailablePlayers(intent: Intent) = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).filterNot { it.activityInfo.packageName.lowercase() in PLAYERS_BLACKLIST }

    private fun configurePlayerIntent(intent: Intent, playerPackage: String, videoTitle: String, isIPTV: Boolean, state: PlayerStateManager.PlaybackState, headers: Array<String>? = null) {
        val position = if (playerTimeCode == "continue") (state.currentItem?.timeline?.time?.times(1000))?.toLong() ?: 0L else 0L
        intent.setPackage(playerPackage)
        intent.putExtra(Intent.EXTRA_TITLE, videoTitle)
        headers?.let { intent.putExtra("headers", it) }
        if (position > 0) intent.putExtra("position", position.toInt())
    }

    private fun prepareHeaders(jsonObject: JSONObject): Array<String>? {
        val headers = mutableListOf<String>()
        jsonObject.optJSONObject("headers")?.let { h -> h.keys().forEach { k -> headers.add(k); headers.add(h.getString(k)) } }
        HttpHelper.userAgent?.let { if (!headers.contains("User-Agent")) { headers.add("User-Agent"); headers.add(it) } }
        return if (headers.isNotEmpty()) headers.toTypedArray() else null
    }

    private fun launchPlayer(intent: Intent) { try { resultLauncher.launch(intent) } catch (_: Exception) {} }

    private fun showPlayerSelectionDialog(players: List<ResolveInfo>, jsonObject: JSONObject, isIPTV: Boolean) {
        val listAdapter = AppListAdapter(this, players)
        val playerChooser = AlertDialog.Builder(this)
        val appTitleView = LayoutInflater.from(this).inflate(R.layout.app_list_title, null)
        val switch = appTitleView.findViewById<SwitchCompat>(R.id.useDefault)
        playerChooser.setCustomTitle(appTitleView)
        playerChooser.setAdapter(listAdapter) { dialog, which ->
            val selectedPlayer = listAdapter.getItemPackage(which)
            if (switch.isChecked) setPlayerPackage(selectedPlayer, isIPTV)
            dialog.dismiss()
            runPlayer(jsonObject, selectedPlayer)
        }
        showFullScreenDialog(playerChooser.create())
    }

    private fun resultPlayer(endedVideoUrl: String, positionMillis: Int, durationMillis: Int, ended: Boolean) {
        if (!ended && positionMillis == 0 && durationMillis == 0) return
        lifecycleScope.launch {
            val currentState = playerStateManager.getState(lampaActivity)
            val videoUrl = endedVideoUrl.takeUnless { it.isBlank() || it == "null" } ?: currentState.currentUrl ?: return@launch
            val updatedPlaylist = currentState.playlist.toMutableList()
            val foundIndex = updatedPlaylist.indexOfFirst { it.url == videoUrl }
            if (foundIndex < 0) return@launch
            val percent = if (durationMillis > 0) (positionMillis * 100 / durationMillis) else 100
            updatedPlaylist[foundIndex] = updatedPlaylist[foundIndex].copy(timeline = PlayerStateManager.PlaylistItem.Timeline(updatedPlaylist[foundIndex].timeline?.hash ?: "0", if (ended) 0.0 else positionMillis / 1000.0, if (ended) 0.0 else durationMillis / 1000.0, if (ended) 100 else percent))
            playerStateManager.saveState(lampaActivity, updatedPlaylist, foundIndex, videoUrl, positionMillis.toLong(), currentState.startIndex, currentState.extras)
            updatedPlaylist[foundIndex].timeline?.let { runVoidJsFunc("Lampa.Timeline.update", playerStateManager.convertTimelineToJsonString(it)) }
            if (ended) {
                withContext(Dispatchers.Default) { updatePlayNext(true) }
                playerStateManager.clearState(lampaActivity)
            } else withContext(Dispatchers.Default) { updatePlayNext(false) }
        }
    }

    private fun getCardFromActivity(activityJson: String?) = try { JSONObject(activityJson ?: "").optJSONObject("movie")?.let { getJson(it.toString(), LampaCard::class.java)?.apply { fixCard() } } } catch (_: Exception) { null }

    private suspend fun updatePlayNext(ended: Boolean) = withContext(Dispatchers.Default) {
        if (!isTvContentProviderAvailable) return@withContext
        val card = getCardFromActivity(lampaActivity) ?: return@withContext
        val state = playerStateManager.findStateByCard(card) ?: return@withContext
        if (ended) { WatchNext.removeContinueWatch(card); playerStateManager.clearState(lampaActivity) }
        else if (state.currentItem != null && !state.isEnded) WatchNext.addLastPlayed(card, lampaActivity)
    }
}
