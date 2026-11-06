package com.u30pro.panel

import android.app.Application
import android.content.Context
import com.u30pro.panel.data.export.ProbeExporter
import com.u30pro.panel.data.goform.GoformTransport
import com.u30pro.panel.data.net.BoundDns
import com.u30pro.panel.data.net.BoundSocketFactory
import com.u30pro.panel.data.net.WifiNetworkBinder
import com.u30pro.panel.data.repo.PanelRepository
import com.u30pro.panel.data.store.CredentialsStore
import com.u30pro.panel.data.store.SettingsStore

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val binder = WifiNetworkBinder(appContext).also { it.start() }

    private val http = GoformTransport.defaultClient(
        socketFactory = BoundSocketFactory(binder),
        dns = BoundDns(binder),
    )

    private val transport = GoformTransport(http)

    val settings = SettingsStore(appContext)

    val credentials = CredentialsStore(appContext)

    val probeExporter = ProbeExporter(appContext)

    val repository = PanelRepository(
        transport = transport,
        settings = settings,
        credentials = credentials,
        binder = binder,
    )
}

class U30App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}