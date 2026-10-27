package br.com.lume.launcher.core

data class NetworkStatus(val connection: String, val internet: String, val online: Boolean = false) {
    companion object {
        fun connected(transport: String, internet: Boolean, validated: Boolean, captive: Boolean): NetworkStatus = when {
            captive -> NetworkStatus(transport, "Login necessário")
            internet && validated -> NetworkStatus(transport, "Internet conectada", true)
            else -> NetworkStatus(transport, "Internet não verificada")
        }
    }
}
