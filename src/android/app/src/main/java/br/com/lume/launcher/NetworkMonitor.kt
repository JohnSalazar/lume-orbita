package br.com.lume.launcher

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import br.com.lume.launcher.core.NetworkStatus

class NetworkMonitor(private val context: Context, private val onUpdate: (NetworkStatus) -> Unit) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var callback: ConnectivityManager.NetworkCallback? = null

    fun start() {
        if (Looper.myLooper() != main.looper) {
            main.post { start() }
            return
        }
        if (callback != null) return
        val connectivity = manager
        if (connectivity == null) {
            onUpdate(NetworkStatus(context.getString(R.string.common_network), context.getString(R.string.common_unavailable)))
            return
        }
        var current: Network? = null
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (callback !== this) return
                current = network
                onUpdate(NetworkStatus(context.getString(R.string.common_network), context.getString(R.string.common_checking_internet)))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (callback === this && network == current) onUpdate(description(capabilities))
            }

            override fun onLost(network: Network) {
                if (callback !== this || current != network) return
                current = null
                onUpdate(NetworkStatus(context.getString(R.string.common_no_network), context.getString(R.string.common_connect_tv)))
            }

            override fun onUnavailable() {
                if (callback !== this) return
                current = null
                onUpdate(NetworkStatus(context.getString(R.string.common_no_network), context.getString(R.string.common_connect_tv)))
            }
        }
        callback = listener
        try {
            connectivity.registerDefaultNetworkCallback(listener, main)
        } catch (_: RuntimeException) {
            callback = null
            onUpdate(NetworkStatus(context.getString(R.string.common_network), context.getString(R.string.common_unavailable)))
            return
        }
        try {
            current = connectivity.activeNetwork
            val capabilities = current?.let { connectivity.getNetworkCapabilities(it) }
            onUpdate(when {
                current == null -> NetworkStatus(context.getString(R.string.common_no_network), context.getString(R.string.common_connect_tv))
                capabilities == null -> NetworkStatus(context.getString(R.string.common_network), context.getString(R.string.common_unknown))
                else -> description(capabilities)
            })
        } catch (_: SecurityException) {
            onUpdate(NetworkStatus(context.getString(R.string.common_network), context.getString(R.string.common_unavailable)))
        }
    }

    fun stop() {
        if (Looper.myLooper() != main.looper) {
            main.post { stop() }
            return
        }
        val listener = callback ?: return
        callback = null
        try {
            manager?.unregisterNetworkCallback(listener)
        } catch (_: IllegalArgumentException) {
            // The system may have already released this callback.
        }
    }

    private fun description(capabilities: NetworkCapabilities): NetworkStatus {
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> context.getString(R.string.common_wifi)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> context.getString(R.string.common_ethernet)
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> context.getString(R.string.common_cellular)
            else -> context.getString(R.string.common_network)
        }
        val network = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) context.getString(R.string.common_vpn, transport) else transport
        val captive = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        val online = !captive && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val label = when {
            captive -> R.string.common_login_required
            online -> R.string.common_internet_connected
            else -> R.string.common_internet_unverified
        }
        return NetworkStatus(network, context.getString(label), online)
    }
}
