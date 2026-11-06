package com.u30pro.panel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.u30pro.panel.data.export.ProbeExporter
import com.u30pro.panel.data.goform.GoformException
import com.u30pro.panel.data.goform.userMessage
import com.u30pro.panel.data.model.RatePoint
import com.u30pro.panel.data.model.Station
import com.u30pro.panel.data.model.StationSnapshot
import com.u30pro.panel.data.model.StatusSnapshot
import com.u30pro.panel.data.profile.DeviceProfile
import com.u30pro.panel.data.profile.ProbeReport
import com.u30pro.panel.data.repo.PanelRepository
import com.u30pro.panel.data.settings.ApnProfile
import com.u30pro.panel.data.settings.SettingsCatalog
import com.u30pro.panel.data.settings.SettingsSnapshot
import com.u30pro.panel.data.settings.SmsMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { Connect, Ready }

enum class Connection { Disconnected, Connecting, Connected, Yielded, Failed }

enum class ManageRoute { ROOT, APN, SMS }

data class PendingSettingWrite(
    val specId: String,
    val input: Map<String, String>,
)

data class PanelUiState(
    val phase: Phase = Phase.Connect,
    val connection: Connection = Connection.Disconnected,
    val profile: DeviceProfile? = null,
    val status: StatusSnapshot? = null,
    val stations: StationSnapshot? = null,
    val probe: ProbeReport? = null,
    val rateHistory: List<RatePoint> = emptyList(),
    val busy: Boolean = false,
    val sessionBusy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val isForeground: Boolean = true,
    val gateway: String = "192.168.0.1",
    val savedPassword: Boolean = false,
    val pollIntervalMs: Long = 1_000L,
    val renameTarget: Station? = null,
    val blockTarget: Station? = null,
    val blockValue: Boolean = false,
    val lastExportPath: String? = null,
    val settings: SettingsSnapshot? = null,
    val settingsLoading: Boolean = false,
    val pendingWrite: PendingSettingWrite? = null,
    val manageRoute: ManageRoute = ManageRoute.ROOT,
    val smsMessages: List<SmsMessage> = emptyList(),
    val smsLoading: Boolean = false,
)

class PanelViewModel(
    private val repo: PanelRepository,
    private val exporter: ProbeExporter,
) : ViewModel() {

    private val _state = MutableStateFlow(PanelUiState(savedPassword = repo.hasSavedPassword()))
    val state: StateFlow<PanelUiState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var stationsTick = 0

    init {
        viewModelScope.launch {
            repo.pollIntervalMs.collect { interval ->
                _state.update { it.copy(pollIntervalMs = interval) }
            }
        }
        viewModelScope.launch { tryAutoConnect() }
    }

    private suspend fun tryAutoConnect() {
        if (!repo.hasSavedPassword()) return
        _state.update { it.copy(connection = Connection.Connecting, busy = true) }
        try {
            val report = repo.autoConnect() ?: return
            onConnected(report)
            refreshStationsInternal()
        } catch (e: GoformException) {
            _state.update {
                it.copy(
                    connection = Connection.Disconnected,
                    busy = false,
                    savedPassword = repo.hasSavedPassword(),
                    error = e.userMessage(),
                )
            }
        }
    }

    fun connect(gateway: String, password: String, remember: Boolean) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update {
                it.copy(busy = true, error = null, connection = Connection.Connecting, gateway = gateway)
            }
            try {
                val report = repo.connect(gateway, password, remember)
                onConnected(report)
                refreshStationsInternal()
            } catch (e: GoformException) {
                _state.update {
                    it.copy(
                        busy = false,
                        connection = Connection.Failed,
                        error = e.userMessage(),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(busy = false, connection = Connection.Failed, error = e.message)
                }
            }
        }
    }

    fun retry() {
        val current = _state.value
        if (current.connection == Connection.Connecting || current.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val report = repo.reprobe()
                onConnected(report)
                refreshStationsInternal()
            } catch (e: GoformException) {
                _state.update {
                    it.copy(busy = false, connection = Connection.Failed, error = e.userMessage())
                }
            }
        }
    }

    fun rerunProbe() = retry()

    private fun onConnected(report: ProbeReport) {
        _state.update {
            it.copy(
                phase = Phase.Ready,
                connection = Connection.Connected,
                profile = report.profile,
                probe = report,
                gateway = report.profile.gateway,
                busy = false,
                sessionBusy = false,
                error = null,
                savedPassword = repo.hasSavedPassword(),
            )
        }
        startPolling()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val snapshot = _state.value
                if (!snapshot.isForeground || snapshot.phase != Phase.Ready) {
                    delay(800)
                    continue
                }
                try {
                    val status = repo.readStatus()
                    val now = System.currentTimeMillis()
                    _state.update { current ->
                        val history = (current.rateHistory +
                            RatePoint(
                                downBps = status.downBps ?: 0L,
                                upBps = status.upBps ?: 0L,
                                atMillis = now,
                            )).takeLast(HISTORY_SIZE)
                        current.copy(
                            status = status,
                            rateHistory = history,
                            connection = Connection.Connected,
                            sessionBusy = false,
                        )
                    }
                    stationsTick++
                    if (stationsTick % STATIONS_EVERY_N_TICKS == 0) {
                        refreshStationsInternal()
                    }
                } catch (e: GoformException) {
                    handleError(e)
                } catch (e: Exception) {
                    _state.update { it.copy(error = e.message) }
                    delay(3_000)
                }
                delay(_state.value.pollIntervalMs)
            }
        }
    }

    private suspend fun handleError(e: GoformException) {
        when (e.kind) {
            GoformException.Kind.SESSION_BUSY -> {
                _state.update {
                    it.copy(
                        sessionBusy = true,
                        connection = Connection.Yielded,
                        error = null,
                    )
                }
                delay(YIELD_MS)
                _state.update { it.copy(sessionBusy = false, connection = Connection.Connected) }
            }
            GoformException.Kind.WRONG_PASSWORD -> {
                _state.update {
                    it.copy(
                        phase = Phase.Connect,
                        connection = Connection.Failed,
                        busy = false,
                        error = e.userMessage(),
                    )
                }
                pollJob?.cancel()
                pollJob = null
            }
            GoformException.Kind.NOT_LOGGED_IN -> {
                // Repository already retried the login once; wait for the next tick.
                _state.update { it.copy(connection = Connection.Connected) }
                delay(2_000)
            }
            else -> {
                _state.update {
                    it.copy(connection = Connection.Failed, error = e.userMessage())
                }
                delay(3_000)
            }
        }
    }

    fun refreshStations() {
        viewModelScope.launch { refreshStationsInternal() }
    }

    private suspend fun refreshStationsInternal() {
        try {
            val stations = repo.readStations()
            _state.update { it.copy(stations = stations) }
        } catch (e: GoformException) {
            handleError(e)
        }
    }

    // -----------------------------------------------------------------------
    // device management
    // -----------------------------------------------------------------------

    fun refreshSettings() {
        viewModelScope.launch { refreshSettingsInternal() }
    }

    private suspend fun refreshSettingsInternal() {
        _state.update { it.copy(settingsLoading = true) }
        try {
            val snapshot = repo.readSettingsSnapshot()
            _state.update { it.copy(settings = snapshot, settingsLoading = false) }
        } catch (e: GoformException) {
            _state.update { it.copy(settingsLoading = false, error = e.userMessage()) }
        }
    }

    fun openManageRoot() {
        _state.update { it.copy(manageRoute = ManageRoute.ROOT) }
        refreshSettings()
    }

    fun openApn() {
        _state.update { it.copy(manageRoute = ManageRoute.APN) }
        refreshSettings()
    }

    fun openSms() {
        _state.update { it.copy(manageRoute = ManageRoute.SMS) }
        refreshSms()
    }

    fun backToManage() {
        _state.update { it.copy(manageRoute = ManageRoute.ROOT) }
    }

    fun requestSettingWrite(specId: String, input: Map<String, String>) {
        val spec = SettingsCatalog.byId(specId) ?: return
        if (spec.risk == com.u30pro.panel.data.settings.SettingRisk.SAFE) {
            executeSettingWrite(specId, input)
        } else {
            _state.update { it.copy(pendingWrite = PendingSettingWrite(specId, input)) }
        }
    }

    fun confirmPendingWrite() {
        val pending = _state.value.pendingWrite ?: return
        executeSettingWrite(pending.specId, pending.input)
    }

    fun dismissPendingWrite() {
        _state.update { it.copy(pendingWrite = null) }
    }

    private fun executeSettingWrite(specId: String, input: Map<String, String>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, pendingWrite = null, error = null) }
            try {
                val outcome = repo.writeSetting(specId, input)
                if (!outcome.success) {
                    _state.update { it.copy(error = outcome.message ?: "写入失败") }
                } else {
                    val notice = when {
                        outcome.verified == false ->
                            outcome.message ?: "已写入，但回读不一致"
                        !outcome.message.isNullOrBlank() -> outcome.message
                        else -> "已保存"
                    }
                    _state.update { it.copy(notice = notice) }
                    refreshSettingsInternal()
                }
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun refreshSms() {
        viewModelScope.launch {
            _state.update { it.copy(smsLoading = true) }
            try {
                val messages = repo.readSms()
                _state.update { it.copy(smsMessages = messages, smsLoading = false) }
            } catch (e: GoformException) {
                _state.update { it.copy(smsLoading = false, error = e.userMessage()) }
            }
        }
    }

    fun sendSms(number: String, body: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val outcome = repo.sendSms(number, body)
                _state.update {
                    it.copy(
                        notice = if (outcome.success) outcome.message else null,
                        error = if (outcome.success) null else outcome.message,
                    )
                }
                if (outcome.success) refreshSms()
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun deleteSms(ids: List<Long>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val outcome = repo.deleteSms(ids)
                _state.update {
                    it.copy(
                        notice = if (outcome.success) outcome.message else null,
                        error = if (outcome.success) null else outcome.message,
                    )
                }
                if (outcome.success) refreshSms()
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun markSmsRead(ids: List<Long>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val outcome = repo.markSmsRead(ids)
                _state.update {
                    it.copy(
                        notice = if (outcome.success) outcome.message else null,
                        error = if (outcome.success) null else outcome.message,
                    )
                }
                if (outcome.success) refreshSms()
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun saveApn(profile: ApnProfile) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val outcome = repo.saveApn(profile)
                _state.update {
                    it.copy(
                        notice = if (outcome.success) outcome.message else null,
                        error = if (outcome.success) null else outcome.message,
                    )
                }
                if (outcome.success) refreshSettingsInternal()
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun deleteApn(index: Int) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val outcome = repo.deleteApn(index)
                _state.update {
                    it.copy(
                        notice = if (outcome.success) outcome.message else null,
                        error = if (outcome.success) null else outcome.message,
                    )
                }
                if (outcome.success) refreshSettingsInternal()
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun setDefaultApn(profile: ApnProfile) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                val outcome = repo.setDefaultApn(profile)
                _state.update {
                    it.copy(
                        notice = if (outcome.success) outcome.message else null,
                        error = if (outcome.success) null else outcome.message,
                    )
                }
                if (outcome.success) refreshSettingsInternal()
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun requestBlock(station: Station, blocked: Boolean) {
        _state.update { it.copy(blockTarget = station, blockValue = blocked) }
    }

    fun dismissBlockDialog() {
        _state.update { it.copy(blockTarget = null) }
    }

    fun confirmBlock() {
        val target = _state.value.blockTarget ?: return
        val blocked = _state.value.blockValue
        _state.update { it.copy(blockTarget = null, busy = true, error = null) }
        viewModelScope.launch {
            try {
                repo.setBlocked(target.mac, blocked)
                refreshStationsInternal()
                _state.update {
                    it.copy(
                        notice = if (blocked) "已拉黑 ${target.displayName}" else "已解黑 ${target.displayName}",
                    )
                }
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun requestRename(station: Station) {
        _state.update { it.copy(renameTarget = station) }
    }

    fun dismissRenameDialog() {
        _state.update { it.copy(renameTarget = null) }
    }

    fun confirmRename(hostname: String) {
        val target = _state.value.renameTarget ?: return
        val trimmed = hostname.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(renameTarget = null, busy = true, error = null) }
        viewModelScope.launch {
            try {
                repo.rename(target.mac, trimmed)
                refreshStationsInternal()
                _state.update { it.copy(notice = "已重命名 ${target.mac}") }
            } catch (e: GoformException) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    fun setPollInterval(ms: Long) {
        viewModelScope.launch { repo.setPollInterval(ms) }
    }

    fun logout(clearSaved: Boolean) {
        pollJob?.cancel()
        pollJob = null
        viewModelScope.launch {
            repo.logout()
            if (clearSaved) repo.clearSaved()
            _state.value = PanelUiState(
                savedPassword = repo.hasSavedPassword(),
                gateway = _state.value.gateway,
                pollIntervalMs = _state.value.pollIntervalMs,
            )
        }
    }

    fun onForeground() {
        _state.update { it.copy(isForeground = true) }
    }

    fun onBackground() {
        _state.update { it.copy(isForeground = false) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /**
     * Writes the current diagnostics to the app-specific external directory so
     * the computer can `adb pull` it without joining the device WiFi.
     */
    fun exportDiagnostics() {
        val snapshot = _state.value
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    exporter.export(
                        probe = snapshot.probe,
                        status = snapshot.status,
                        stations = snapshot.stations,
                    )
                }
                _state.update {
                    it.copy(
                        lastExportPath = file.absolutePath,
                        notice = "诊断已导出：${file.absolutePath}",
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "导出失败：${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    companion object {
        private const val HISTORY_SIZE = 60
        private const val STATIONS_EVERY_N_TICKS = 5
        private const val YIELD_MS = 30_000L

        fun factory(repo: PanelRepository, exporter: ProbeExporter) = viewModelFactory {
            initializer { PanelViewModel(repo, exporter) }
        }
    }
}