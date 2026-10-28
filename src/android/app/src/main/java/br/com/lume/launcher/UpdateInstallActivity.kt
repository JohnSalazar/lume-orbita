package br.com.lume.launcher

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Private foreground host for the system confirmation; no background install UI or receiver. */
class UpdateInstallActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "lume-update-install").apply { isDaemon = true } }
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var body: LinearLayout
    private var prepared: PreparedUpdate? = null
    private var updater: GitHubUpdater? = null
    private var owner = ""
    @Volatile private var sessionId = -1
    private var committed = false
    private var completed = false
    private var finalStatus = PackageInstaller.STATUS_FAILURE
    private var homeWarning = false
    private var resumed = false
    private var confirmation: Intent? = null
    @Volatile private var canceled = false

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLocales.wrap(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        owner = savedInstanceState?.getString("owner") ?: intent.getStringExtra("owner").orEmpty()
        completed = savedInstanceState?.getBoolean("completed", false) ?: false
        if (!completed && !UpdateInstaller.attach(this, owner)) { finish(); return }
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            val padding = GlassUi.dp(context, 36); setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.rgb(8, 26, 41))
        }
        body.addView(GlassUi.text(this, applicationInfo.loadLabel(packageManager).toString(), 26f))
        status = GlassUi.text(this, getString(R.string.activity_update_installing), 18f)
        body.addView(status, LinearLayout.LayoutParams(-1, -2).apply { topMargin = GlassUi.dp(this@UpdateInstallActivity, 18) })
        body.addView(GlassUi.backButton(this, getString(R.string.activity_back), { finish() }))
        setContentView(body)
        sessionId = savedInstanceState?.getInt("session", -1) ?: -1
        committed = savedInstanceState?.getBoolean("committed", false) ?: false
        finalStatus = savedInstanceState?.getInt("finalStatus", PackageInstaller.STATUS_FAILURE) ?: PackageInstaller.STATUS_FAILURE
        homeWarning = savedInstanceState?.getBoolean("homeWarning", false) ?: false
        if (completed) showTerminal()
        else if (intent.action == UpdateInstaller.RESULT_ACTION) { if (!handleResult(intent)) finish() }
        else if (!committed) {
            val old = sessionId
            if (old >= 0) { fail(); worker.execute { runCatching { UpdateInstaller.abandonOwned(this, owner, old) } } }
            else stage()
        }
    }

    private fun stage() {
        val name = intent.getStringExtra("file").orEmpty()
        if (!name.matches(Regex("lume-update-[A-Za-z0-9-]+\\.apk"))) { fail(); return }
        val release = UpdateRelease(intent.getStringExtra("version").orEmpty(), intent.getStringExtra("tag").orEmpty(),
            intent.getStringExtra("page").orEmpty(), intent.getStringExtra("apk").orEmpty(),
            intent.getStringExtra("sums").orEmpty(), intent.getLongExtra("bytes", 0), intent.getBooleanExtra("prerelease", false))
        val update = PreparedUpdate(release, File(File(cacheDir, "updates"), name), intent.getLongExtra("code", 0), intent.getStringExtra("sha256").orEmpty())
        prepared = update
        val client = GitHubUpdater(applicationContext); updater = client
        if (!UpdateInstaller.canInstall(this)) { status.setText(R.string.activity_update_source_required); return }
        worker.execute {
            var staged = -1
            try {
                client.validatePrepared(update)
                if (canceled) return@execute
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(packageName); setSize(update.apk.length())
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                staged = installer.createSession(params)
                if (!UpdateInstaller.recordSession(this, owner, staged)) throw IllegalStateException()
                sessionId = staged
                installer.openSession(staged).use { session ->
                    session.openWrite("base.apk", 0, update.apk.length()).use { output ->
                        update.apk.inputStream().use { input ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                if (canceled || Thread.currentThread().isInterrupted) throw UpdateFailure(UpdateFailureReason.CANCELED)
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                        }
                        session.fsync(output)
                    }
                }
                client.discard(update)
                if (canceled) { UpdateInstaller.abandonOwned(this, owner, staged); return@execute }
                val id = staged
                handler.post {
                    if (canceled || isDestroyed) return@post
                    if (!resumed || isFinishing) {
                        worker.execute { runCatching { UpdateInstaller.abandonOwned(this, owner, id) } }; finish()
                    } else commit(id)
                }
            } catch (_: Exception) {
                // Only a just-created, not-yet-recorded id may be cleaned directly by this worker.
                if (staged >= 0 && sessionId < 0) runCatching { packageManager.packageInstaller.abandonSession(staged) }
                runCatching { UpdateInstaller.abandonOwned(this, owner, sessionId) }
                runCatching { client.discard(update) }
                handler.post { if (!isDestroyed && !canceled) fail() }
            }
        }
    }

    private fun commit(id: Int) {
        val token = UUID.randomUUID().toString()
        try {
            if (!UpdateInstaller.prepareCommit(this, owner, id, token)) throw IllegalStateException()
            val callback = Intent(this, UpdateInstallActivity::class.java).apply {
                action = UpdateInstaller.RESULT_ACTION
                data = android.net.Uri.parse("lume-update://result/$token")
                putExtra("token", token)
                putExtra("owner", owner)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getActivity(this, id, callback, flags)
            packageManager.packageInstaller.openSession(id).use { it.commit(result.intentSender) }
            committed = true
        } catch (_: Exception) {
            worker.execute { runCatching { UpdateInstaller.abandonOwned(this, owner, id) } }
            fail()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != UpdateInstaller.RESULT_ACTION || intent.getStringExtra("owner") != owner) return
        // A late callback cannot replace the initial Intent of a different staging attempt.
        if (handleResult(intent)) setIntent(intent)
    }

    private fun handleResult(result: Intent): Boolean {
        if (completed) return false
        val token = result.getStringExtra("token")
        val id = result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (result.action != UpdateInstaller.RESULT_ACTION || result.getStringExtra("owner") != owner ||
            result.data?.toString() != "lume-update://result/$token" || (sessionId >= 0 && sessionId != id) ||
            !UpdateInstaller.acceptsCallback(this, owner, id, token)) return false
        sessionId = id; committed = true
        when (result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val next = result.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (next == null) fail() else { confirmation = next; openConfirmation() }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                if (!UpdateInstaller.finishAttempt(this, owner, id, token, true)) return false
                completed = true
                finalStatus = PackageInstaller.STATUS_SUCCESS
                homeWarning = UpdateInstaller.consumeHomeWarning(this)
                showTerminal()
            }
            else -> {
                if (!UpdateInstaller.finishAttempt(this, owner, id, token, false)) return false
                completed = true
                finalStatus = result.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                showTerminal()
            }
        }
        return true
    }

    private fun showTerminal() {
        status.setText(when (finalStatus) {
            PackageInstaller.STATUS_SUCCESS -> R.string.activity_update_install_success
            PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.activity_update_install_canceled
            else -> R.string.activity_update_install_failed
        })
        if (homeWarning) {
            body.addView(GlassUi.text(this, getString(R.string.activity_update_home_changed), 16f))
            body.addView(GlassUi.button(this, getString(R.string.activity_update_home_review)) {
                if (!DeviceActions(this).requestHome()) android.widget.Toast.makeText(this, R.string.activity_home_selection_unavailable, android.widget.Toast.LENGTH_LONG).show()
            })
        }
    }

    private fun openConfirmation() {
        if (!resumed) return
        val next = confirmation ?: return
        confirmation = null
        try { startActivity(next) } catch (_: RuntimeException) { fail() }
    }
    private fun fail() { status.setText(R.string.activity_update_install_failed) }
    override fun onResume() { super.onResume(); resumed = true; openConfirmation() }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("session", sessionId); outState.putBoolean("committed", committed); outState.putBoolean("completed", completed)
        outState.putString("owner", owner)
        outState.putInt("finalStatus", finalStatus); outState.putBoolean("homeWarning", homeWarning)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        canceled = true; updater?.cancel(); handler.removeCallbacksAndMessages(null)
        worker.execute {
            if (!completed && (!committed || isFinishing)) runCatching { UpdateInstaller.abandonOwned(this, owner, sessionId) }
            prepared?.let { update -> runCatching { updater?.discard(update) } }
            UpdateInstaller.detach(owner)
        }
        worker.shutdown()
        super.onDestroy()
    }
}
