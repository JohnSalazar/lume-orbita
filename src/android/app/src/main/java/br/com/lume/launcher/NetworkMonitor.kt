package br.com.lume.launcher

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import br.com.lume.launcher.core.NetworkStatus

class NetworkMonitor(context: Context, private val onUpdate: (NetworkStatus) -> Unit) {
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
            onUpdate(NetworkStatus("Rede", "Estado indisponível"))
            return
        }
        var current: Network? = null
        val listener = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (callback !== this) return
                current = network
                onUpdate(NetworkStatus("Rede", "Verificando internet"))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (callback === this && network == current) onUpdate(description(capabilities))
            }

            override fun onLost(network: Network) {
                if (callback !== this || current != network) return
                current = null
                onUpdate(NetworkStatus("Sem rede", "Conecte sua TV"))
            }

            override fun onUnavailable() {
                if (callback !== this) return
                current = null
                onUpdate(NetworkStatus("Sem rede", "Conecte sua TV"))
            }
        }
        callback = listener
        try {
            connectivity.registerDefaultNetworkCallback(listener, main)
        } catch (_: RuntimeException) {
            callback = null
            onUpdate(NetworkStatus("Rede", "Estado indisponível"))
            return
        }
        try {
            current = connectivity.activeNetwork
            val capabilities = current?.let { connectivity.getNetworkCapabilities(it) }
            onUpdate(when {
                current == null -> NetworkStatus("Sem rede", "Conecte sua TV")
                capabilities == null -> NetworkStatus("Rede", "Estado desconhecido")
                else -> description(capabilities)
            })
        } catch (_: SecurityException) {
            onUpdate(NetworkStatus("Rede", "Estado indisponível"))
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
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cabo"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Móvel"
            else -> "Rede"
        }
        val network = if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) "$transport (VPN)" else transport
        return NetworkStatus.connected(network,
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
    }
}
