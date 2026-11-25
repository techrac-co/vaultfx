package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {
  private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

  private val _isDeviceOnline = MutableStateFlow(checkCurrentConnectivity())
  val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

  private val callback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
      _isDeviceOnline.value = checkCurrentConnectivity()
    }

    override fun onLost(network: Network) {
      _isDeviceOnline.value = checkCurrentConnectivity()
    }

    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
      _isDeviceOnline.value = hasValidatedInternet(networkCapabilities)
    }
  }

  init {
    runCatching { cm.registerDefaultNetworkCallback(callback) }
  }

  fun release() {
    runCatching { cm.unregisterNetworkCallback(callback) }
  }

  private fun checkCurrentConnectivity(): Boolean {
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return hasValidatedInternet(caps)
  }

  private fun hasValidatedInternet(caps: NetworkCapabilities): Boolean {
    val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    return hasInternet && validated
  }
}
