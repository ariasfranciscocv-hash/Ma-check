package com.example.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ConnectivityChecker {
    val isOnline: StateFlow<Boolean>
    fun isCurrentlyOnline(): Boolean
}

class NetworkMonitor(context: Context) : ConnectivityChecker {

    private val connectivityManager: ConnectivityManager? =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkOnline(connectivityManager))
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = checkOnline(connectivityManager)
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkOnline(connectivityManager)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            _isOnline.value =
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun isCurrentlyOnline(): Boolean {
        val current = checkOnline(connectivityManager)
        _isOnline.value = current
        return current
    }

    fun unregister() {
        runCatching {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        }
    }

    private fun checkOnline(cm: ConnectivityManager?): Boolean {
        if (cm == null) return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
