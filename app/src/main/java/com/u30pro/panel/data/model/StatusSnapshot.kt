package com.u30pro.panel.data.model

/** Snapshot rendered by the overview screen. All values are best-effort. */
data class StatusSnapshot(
    val connected: Boolean = false,
    val networkType: String? = null,
    val networkLabel: String = "未知",
    val provider: String? = null,
    val rsrp: Int? = null,
    val sinr: Double? = null,
    val rsrq: Int? = null,
    val band: String? = null,
    val pci: String? = null,
    val cellId: String? = null,
    val downBps: Long? = null,
    val upBps: Long? = null,
    val monthRxBytes: Long? = null,
    val monthTxBytes: Long? = null,
    val sessionRxBytes: Long? = null,
    val sessionTxBytes: Long? = null,
    val uptimeSeconds: Long? = null,
    val wifiClients: Int? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean? = null,
    val smsUnread: Int? = null,
    val temperatureC: Double? = null,
    val model: String? = null,
    val imei: String? = null,
    val iccid: String? = null,
    val msisdn: String? = null,
    val wanIp: String? = null,
    val raw: Map<String, String> = emptyMap(),
) {
    val hasSignal: Boolean get() = rsrp != null || sinr != null || rsrq != null
}

data class RatePoint(val downBps: Long, val upBps: Long, val atMillis: Long)