package br.com.lume.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** System-protected package-local event. No network, permission grants or Home-setting writes. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        UpdateInstaller.reconcile(context, protectedReplace = true) { pending.finish() }
    }
}
