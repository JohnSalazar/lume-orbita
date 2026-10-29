package br.com.lume.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import br.com.lume.launcher.core.InstallResolution
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Adapter contracts only: synthetic journals, real installed identity, no receiver or real navigation. */
class UpdateReturnInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }

    private class IsolatedContext(base: Context, private val isolated: SharedPreferences,
                                  private val fixtureCache: File) : ContextWrapper(base) {
        val launches = CopyOnWriteArrayList<Intent>()
        @Volatile var beforeSourceRead: (() -> Unit)? = null
        override fun getApplicationContext(): Context = this
        override fun getCacheDir(): File = fixtureCache
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            check(name == "lume_updater") { "Unexpected preference namespace in isolated adapter" }
            return isolated
        }
        override fun getApplicationInfo(): ApplicationInfo {
            beforeSourceRead?.invoke()
            return super.getApplicationInfo()
        }
        override fun startActivity(intent: Intent) { launches.add(Intent(intent)) }
        override fun startActivity(intent: Intent, options: Bundle?) { launches.add(Intent(intent)) }
    }

    private class Pending {
        val done = CountDownLatch(1)
        val calls = AtomicInteger()
        val value = AtomicReference<InstallResolution?>()
    }

    private fun copyState(prefs: SharedPreferences): Map<String, Any?> = prefs.all.mapValues { (_, value) ->
        if (value is Set<*>) value.toSet() else value
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun oppositeHash(value: String): String = (if (value.first() == '0') "1" else "0") + value.drop(1)
    @Suppress("DEPRECATION")
    override fun onStart() {
        val report = JSONObject().put("method", "isolated preference adapter; real installed APK identity; no broadcast sent")
        val cases = JSONArray(); report.put("cases", cases)
        val pending = mutableListOf<Pending>()
        var failure: Throwable? = null
        var isolatedName: String? = null
        var isolatedContext: IsolatedContext? = null
        var fixtureCache: File? = null
        var cacheParent: File? = null
        var fixtureLink: File? = null
        var fixtureAliasRoot: File? = null
        var orbitaBefore: JSONObject? = null
        var actualUpdaterBefore: Map<String, Any?>? = null
        var sessionsBefore: Set<Int>? = null
        var homeBefore: String? = null
        var accessBefore: Boolean? = null
        fun failed(error: Throwable) {
            if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
        }
        try {
            orbitaBefore = PreferenceStateSnapshot.read(targetContext)
            homeBefore = UpdateInstaller.currentHome(targetContext)
            check(homeBefore != null) { "Home baseline is unresolved" }
            actualUpdaterBefore = copyState(targetContext.getSharedPreferences("lume_updater", Context.MODE_PRIVATE))
            sessionsBefore = targetContext.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet()
            check(sessionsBefore.isEmpty()) { "Cleanup contracts require no active installer sessions" }
            accessBefore = UpdateInstaller.canInstall(targetContext)

            val info = targetContext.packageManager.getPackageInfo(targetContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val code = info.longVersionCode
            check(code > 1)
            val signers = requireNotNull(info.signingInfo).apkContentsSigners.map {
                hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
            }.toSet()
            check(signers.isNotEmpty())
            val digest = MessageDigest.getInstance("SHA-256")
            File(targetContext.applicationInfo.sourceDir).inputStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) { val size = input.read(buffer); if (size < 0) break; digest.update(buffer, 0, size) }
            }
            val installedHash = hex(digest.digest())
            val ownHome = ComponentName(targetContext, LauncherActivity::class.java).flattenToString()
            check(homeBefore == ownHome) { "This contract requires Lume as the unchanged default Home" }
            var absentSession = Int.MAX_VALUE
            while (targetContext.packageManager.packageInstaller.getSessionInfo(absentSession) != null) {
                check(absentSession > Int.MAX_VALUE - 100); absentSession--
            }
            isolatedName = "qa_update_return_" + UUID.randomUUID().toString().replace("-", "")
            val prefs = targetContext.getSharedPreferences(isolatedName, Context.MODE_PRIVATE)
            check(prefs.all.isEmpty())
            val parent = targetContext.cacheDir.canonicalFile; cacheParent = parent
            val cache = File(parent, "qa_update_return_" + UUID.randomUUID().toString().replace("-", ""))
            check(!Files.isSymbolicLink(cache.toPath()) && cache.canonicalFile.parentFile == parent &&
                !cache.exists() && cache.mkdir()) { "Cannot create exclusive test cache" }
            fixtureCache = cache
            val updates = File(cache, "updates").apply { check(mkdir()) }
            val firstOrphan = File(updates, "lume-update-first.apk").apply { writeBytes(byteArrayOf(1, 2)) }
            val secondOrphan = File(updates, "lume-update-second.apk").apply { writeBytes(byteArrayOf(3, 4)) }
            val sentinel = File(updates, "keep.txt").apply { writeText("owned-sentinel") }
            val link = File(updates, "lume-update-link.apk"); fixtureLink = link
            // A real POSIX link, not a mocked path. Failure to create it fails this contract; no skip.
            Files.createSymbolicLink(link.toPath(), sentinel.toPath())
            val context = IsolatedContext(targetContext, prefs, cache); isolatedContext = context
            check(context.applicationContext === context)
            check(context.cacheDir === cache)
            check(runCatching { context.getSharedPreferences("orbita", 0) }.isFailure)

            fun seed(owner: String = UUID.randomUUID().toString(), expectedHash: String = installedHash,
                     expectedCode: Long = code, expectedSigners: Set<String> = signers, legacy: Boolean = false): String {
                val edit = prefs.edit().clear().putString("owner", owner).putInt("session", absentSession)
                    .putString("token", UUID.randomUUID().toString()).putLong("before_code", code - 1)
                    .putString("home_owner", owner).putString("before_home", ownHome)
                if (!legacy) edit.putLong("expected_code", expectedCode).putString("expected_hash", expectedHash)
                    .putStringSet("expected_signers", expectedSigners).putBoolean("commit_requested", true)
                    .putLong("interactive_at", System.currentTimeMillis())
                check(edit.commit())
                context.launches.clear()
                return owner
            }
            fun begin(protectedPass: Boolean = false): Pending = Pending().also { request ->
                pending.add(request)
                UpdateInstaller.reconcile(context, protectedPass) { result ->
                    request.value.set(result); request.calls.incrementAndGet(); request.done.countDown()
                }
            }
            fun await(request: Pending): InstallResolution {
                check(request.done.await(20, TimeUnit.SECONDS)) { "Adapter callback did not finish within the test budget" }
                waitForIdleSync()
                check(request.calls.get() == 1) { "Adapter callback was delivered more than once" }
                pending.remove(request)
                return requireNotNull(request.value.get())
            }
            fun passed(name: String) { cases.put(JSONObject().put("case", name).put("passed", true)) }

            // The protected flag is a direct adapter seam, not an actual system broadcast.
            // Any proposed Home return is intercepted by IsolatedContext.startActivity.
            val exactOwner = seed()
            check(await(begin(true)) == InstallResolution.VERIFIED)
            check(!firstOrphan.exists() && !secondOrphan.exists()) { "Successful reconciliation left owned APK fixtures" }
            check(Files.isSymbolicLink(link.toPath()) && Files.readSymbolicLink(link.toPath()) == sentinel.toPath())
            check(sentinel.readText() == "owned-sentinel" &&
                updates.list()!!.toSet() == setOf("keep.txt", "lume-update-link.apk"))
            Files.delete(link.toPath()) // Unlink the entry explicitly; never follow it or delete its target.
            check(Files.notExists(link.toPath(), LinkOption.NOFOLLOW_LINKS) && sentinel.readText() == "owned-sentinel")
            check(UpdateInstaller.resolvedOwner(context, exactOwner))
            check(!UpdateInstaller.resolvedOwner(context, "not-this-owner"))
            check(!prefs.contains("owner") && !prefs.contains("session") && !prefs.contains("token"))
            check(UpdateInstaller.consumeInstallSuccess(context))
            check(!UpdateInstaller.consumeInstallSuccess(context))
            check(!UpdateInstaller.consumeHomeWarning(context))
            check(context.launches.size == 1)
            val proposed = context.launches.single()
            check(proposed.component == ComponentName(targetContext, LauncherActivity::class.java))
            check(proposed.action == Intent.ACTION_MAIN && proposed.hasCategory(Intent.CATEGORY_HOME))
            check(proposed.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            val retryOrphan = File(updates, "lume-update-retry.apk").apply { writeBytes(byteArrayOf(5, 6)) }
            check(await(begin()) == InstallResolution.NEUTRAL)
            check(!retryOrphan.exists() && sentinel.readText() == "owned-sentinel" &&
                updates.list()!!.toSet() == setOf("keep.txt")) { "Idempotent reconciliation did not retry safe cleanup" }
            check(context.launches.size == 1 && !UpdateInstaller.consumeInstallSuccess(context))
            passed("exact_identity_idempotent_result_and_single_notification")

            val directoryTarget = File(updates, "lume-update-directory-target.apk")
            val targetBytes = byteArrayOf(7, 8, 9)
            directoryTarget.writeBytes(targetBytes)
            val alias = File(cache, "alias")
            check(!Files.isSymbolicLink(alias.toPath()) && alias.canonicalFile.parentFile == cache.canonicalFile &&
                !alias.exists() && alias.mkdir()) { "Cannot create owned alias fixture" }
            fixtureAliasRoot = alias
            val aliasUpdates = File(alias, "updates")
            Files.createSymbolicLink(aliasUpdates.toPath(), updates.toPath())
            val aliasContext = IsolatedContext(targetContext, prefs, alias)
            check(aliasContext.applicationContext === aliasContext && aliasContext.cacheDir === alias)
            check(targetContext.packageManager.packageInstaller.mySessions.isEmpty())
            check(GitHubUpdater.cleanupDownloads(aliasContext) == 0) { "Cleanup followed a symlinked updates directory" }
            check(Files.isSymbolicLink(aliasUpdates.toPath()) && Files.readSymbolicLink(aliasUpdates.toPath()) == updates.toPath())
            check(directoryTarget.readBytes().contentEquals(targetBytes) && sentinel.readText() == "owned-sentinel" &&
                updates.list()!!.toSet() == setOf("keep.txt", "lume-update-directory-target.apk"))
            Files.delete(aliasUpdates.toPath()) // Remove the alias itself, never traverse its target.
            check(Files.notExists(aliasUpdates.toPath(), LinkOption.NOFOLLOW_LINKS) && alias.delete())
            check(!Files.isSymbolicLink(directoryTarget.toPath()) &&
                directoryTarget.canonicalFile.parentFile == updates.canonicalFile && directoryTarget.delete())
            passed("directory_symlink_cleanup_is_no_op_and_preserves_target")

            val badHashOwner = seed(expectedHash = oppositeHash(installedHash))
            check(await(begin()) == InstallResolution.NEUTRAL)
            check(!UpdateInstaller.resolvedOwner(context, badHashOwner) && !UpdateInstaller.consumeInstallSuccess(context))
            check(context.launches.isEmpty())
            passed("hash_mismatch_never_reports_success")

            val badCodeOwner = seed(expectedCode = code + 1)
            check(await(begin()) == InstallResolution.NEUTRAL)
            check(!UpdateInstaller.resolvedOwner(context, badCodeOwner) && !UpdateInstaller.consumeInstallSuccess(context))
            check(context.launches.isEmpty())
            passed("code_mismatch_never_reports_success")

            val badSignerOwner = seed(expectedSigners = setOf(oppositeHash(signers.first())))
            check(await(begin()) == InstallResolution.NEUTRAL)
            check(!UpdateInstaller.resolvedOwner(context, badSignerOwner) && !UpdateInstaller.consumeInstallSuccess(context))
            check(context.launches.isEmpty())
            passed("signer_mismatch_never_reports_success")

            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val oldOwner = seed()
            context.beforeSourceRead = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) { "Test did not release the source-read barrier" }
            }
            val stale = begin()
            val replacement: Map<String, Any?>
            val newOwner: String
            try {
                check(entered.await(5, TimeUnit.SECONDS)) { "Source-read barrier was not reached" }
                newOwner = seed()
                replacement = copyState(prefs)
            } finally { context.beforeSourceRead = null; release.countDown() }
            check(await(stale) == InstallResolution.NEUTRAL)
            check(copyState(prefs) == replacement) { "An obsolete result modified the replacement attempt" }
            check(!UpdateInstaller.resolvedOwner(context, oldOwner) && !UpdateInstaller.resolvedOwner(context, newOwner))
            check(!UpdateInstaller.consumeInstallSuccess(context) && context.launches.isEmpty())
            passed("stale_owner_cannot_clear_or_complete_replacement_attempt")

            val legacyOwner = seed(legacy = true)
            val legacyBefore = copyState(prefs)
            check(await(begin()) == InstallResolution.NEUTRAL)
            check(copyState(prefs) == legacyBefore)
            check(!UpdateInstaller.resolvedOwner(context, legacyOwner) && !UpdateInstaller.consumeInstallSuccess(context))
            check(!UpdateInstaller.consumeHomeWarning(context) && context.launches.isEmpty())
            passed("legacy_without_protected_event_remains_neutral")
            check(cases.length() == 7)
            report.put("checks_passed", true).put("contract_count", cases.length()).put("home_return_intercepted_only", true)
                .put("orphan_fixture_cleanup", true).put("orphan_fixture_retry", true).put("non_apk_sentinel_preserved", true)
                .put("posix_file_symlink_preserved", true)
                .put("posix_directory_symlink_preserved", true)
        } catch (error: Throwable) { failed(error) }
        finally {
            isolatedContext?.beforeSourceRead = null
            var drained = true
            pending.forEach { if (!it.done.await(10, TimeUnit.SECONDS)) drained = false }
            try {
                check(drained) { "Adapter worker is still pending; preserve isolated data for diagnosis" }
                isolatedName?.let { check(targetContext.deleteSharedPreferences(it)) { "Cannot remove owned test preferences" } }
                report.put("isolated_preferences_removed", true)
            } catch (error: Throwable) { failed(error) }
            try {
                check(drained) { "Adapter worker is still pending; preserve the owned cache fixture" }
                fixtureCache?.let { cache ->
                    val parent = requireNotNull(cacheParent)
                    check(cache.name.startsWith("qa_update_return_") && !Files.isSymbolicLink(cache.toPath()) &&
                        cache.canonicalFile.parentFile == parent && cache.isDirectory) { "Unsafe fixture cache location" }
                    val updates = File(cache, "updates")
                    check(!Files.isSymbolicLink(updates.toPath()) && updates.canonicalFile.parentFile == cache.canonicalFile &&
                        updates.isDirectory) { "Unsafe fixture updates location" }
                    fixtureAliasRoot?.let { alias ->
                        check(alias.name == "alias" && alias.parentFile.canonicalFile == cache.canonicalFile)
                        if (Files.exists(alias.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                            check(!Files.isSymbolicLink(alias.toPath()) && alias.canonicalFile.parentFile == cache.canonicalFile &&
                                alias.isDirectory) { "Unsafe fixture alias location" }
                            val aliasUpdates = File(alias, "updates")
                            check(aliasUpdates.parentFile.canonicalFile == alias.canonicalFile)
                            if (Files.isSymbolicLink(aliasUpdates.toPath())) Files.delete(aliasUpdates.toPath())
                            else check(Files.notExists(aliasUpdates.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Fixture directory link was replaced" }
                            check(alias.delete()) { "Cannot remove owned alias fixture" }
                        }
                    }
                    fixtureLink?.let { link ->
                        check(link.name == "lume-update-link.apk" && link.parentFile.canonicalFile == updates.canonicalFile)
                        if (Files.isSymbolicLink(link.toPath())) Files.delete(link.toPath())
                        else check(Files.notExists(link.toPath(), LinkOption.NOFOLLOW_LINKS)) { "Fixture link was replaced" }
                    }
                    requireNotNull(updates.listFiles()).forEach { file ->
                        check(!Files.isSymbolicLink(file.toPath()) && file.canonicalFile.parentFile == updates.canonicalFile &&
                            file.isFile && file.delete()) { "Cannot safely remove an owned fixture file" }
                    }
                    check(updates.delete() && cache.delete()) { "Cannot remove owned fixture directories" }
                }
                report.put("isolated_cache_removed", true)
            } catch (error: Throwable) { failed(error) }
            try {
                orbitaBefore?.let { check(PreferenceStateSnapshot.matches(targetContext, it)) { "Real orbita preferences changed" } }
                actualUpdaterBefore?.let { check(copyState(targetContext.getSharedPreferences("lume_updater", 0)) == it) { "Real updater namespace changed" } }
                sessionsBefore?.let { check(targetContext.packageManager.packageInstaller.mySessions.map { s -> s.sessionId }.toSet() == it) { "Installer sessions changed" } }
                homeBefore?.let { check(UpdateInstaller.currentHome(targetContext) == it) { "Default Home changed" } }
                accessBefore?.let { check(UpdateInstaller.canInstall(targetContext) == it) { "Installation access changed" } }
                check(orbitaBefore != null && actualUpdaterBefore != null && homeBefore != null && accessBefore != null)
                report.put("orbita_unchanged", true).put("updater_namespace_unchanged", true)
                    .put("installer_sessions_unchanged", true).put("home_unchanged", true).put("install_access_unchanged", true)
            } catch (error: Throwable) { failed(error) }
            report.put("status", if (failure == null) "passed" else "failed")
            failure?.let { report.put("error", it.javaClass.simpleName + ": " + it.message) }
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("result", if (failure == null) "update-return:ok" else "update-return:error")
            putString("report", report.toString())
            failure?.let { putString("error", it.javaClass.simpleName + ": " + it.message) }
        })
    }
}
