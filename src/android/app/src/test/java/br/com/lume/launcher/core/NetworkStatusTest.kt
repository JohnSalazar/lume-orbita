package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class NetworkStatusTest {
    @Test fun internetRequiresBothCapabilities() {
        for (internet in listOf(false, true)) for (validated in listOf(false, true)) {
            val status = NetworkStatus.connected("Wi-Fi", internet, validated, false)
            assertEquals(internet && validated, status.online)
            assertEquals(if (internet && validated) "Internet conectada" else "Internet não verificada", status.internet)
            assertEquals("Wi-Fi", status.connection)
        }
    }

    @Test fun captivePortalTakesPriorityEvenWithValidatedCapability() {
        for (internet in listOf(false, true)) for (validated in listOf(false, true)) {
            val status = NetworkStatus.connected("Cabo", internet, validated, true)
            assertFalse(status.online)
            assertEquals("Login necessário", status.internet)
        }
    }

    @Test fun unconfirmedStatesNeverClaimInternetIsConnected() {
        for (status in listOf(
            NetworkStatus("Rede", "Verificando internet"),
            NetworkStatus("Sem rede", "Conecte sua TV"),
            NetworkStatus("Rede", "Estado indisponível")
        )) assertFalse(status.online)
    }
}
