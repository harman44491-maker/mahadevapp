package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NetworkMonitor(
    context: Context,
    scope: CoroutineScope
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isDeviceConnected = MutableStateFlow(checkInitialConnectivity())
    val isDeviceConnected: StateFlow<Boolean> = _isDeviceConnected

    // Simulation toggle for remote field scenarios (allows testing offline queuing on connected devices)
    val isSimulatingOffline = MutableStateFlow(false)

    // Effective network state: connected only if device has network AND simulation is false
    val isOnline: StateFlow<Boolean> = combine(
        _isDeviceConnected,
        isSimulatingOffline
    ) { connected, simulatingOffline ->
        connected && !simulatingOffline
    }.stateIn(scope, SharingStarted.Eagerly, checkInitialConnectivity())

    init {
        registerNetworkCallback()
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val builder = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cm.registerNetworkCallback(builder, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isDeviceConnected.value = true
            }

            override fun onLost(network: Network) {
                _isDeviceConnected.value = checkInitialConnectivity()
            }
        })
    }

    fun setSimulatingOffline(simulate: Boolean) {
        isSimulatingOffline.value = simulate
    }
}
