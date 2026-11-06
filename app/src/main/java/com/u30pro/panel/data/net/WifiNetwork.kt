package com.u30pro.panel.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import okhttp3.Dns
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

/**
 * The phone is usually on cellular as its default network while talking to the
 * device over WiFi. Bind every socket to the WiFi network so requests to
 * 192.168.0.1 do not get routed out of the cellular interface.
 */
class WifiNetworkBinder(context: Context) {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var wifiNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifiNetwork = network
        }

        override fun onLost(network: Network) {
            if (wifiNetwork == network) wifiNetwork = null
        }
    }

    fun start() {
        runCatching {
            connectivity.requestNetwork(
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .build(),
                callback,
            )
        }
    }

    fun stop() {
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    fun current(): Network? = wifiNetwork

    fun isOnWifi(): Boolean {
        val active = connectivity.activeNetwork ?: return false
        return connectivity.getNetworkCapabilities(active)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}

class BoundSocketFactory(private val binder: WifiNetworkBinder) : SocketFactory() {

    override fun createSocket(): Socket =
        binder.current()?.socketFactory?.createSocket() ?: Socket()

    override fun createSocket(host: String, port: Int): Socket =
        binder.current()?.socketFactory?.createSocket(host, port) ?: Socket(host, port)

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        binder.current()?.socketFactory?.createSocket(host, port, localHost, localPort)
            ?: Socket(host, port, localHost, localPort)

    override fun createSocket(host: InetAddress, port: Int): Socket =
        binder.current()?.socketFactory?.createSocket(host, port) ?: Socket(host, port)

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        binder.current()?.socketFactory?.createSocket(address, port, localAddress, localPort)
            ?: Socket(address, port, localAddress, localPort)
}

class BoundDns(private val binder: WifiNetworkBinder) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val network = binder.current()
        if (network != null) {
            runCatching { return network.getAllByName(hostname).toList() }
        }
        return Dns.SYSTEM.lookup(hostname)
    }
}