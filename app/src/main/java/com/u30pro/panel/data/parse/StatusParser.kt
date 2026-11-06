package com.u30pro.panel.data.parse

import com.u30pro.panel.data.model.StatusSnapshot
import com.u30pro.panel.data.goform.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.math.roundToInt

object StatusParser {

    fun parse(values: Map<String, String>, temperatureField: String? = null): StatusSnapshot {
        val networkType = values.firstOf("network_type")
        val ppp = values.firstOf("ppp_status")
        val networkInfo = values["network_information"]?.let(::parseNetworkInformation)
        val connected = when {
            ppp == null -> networkType != null
            ppp.contains("disconnect", ignoreCase = true) -> false
            else -> ppp.contains("connect", ignoreCase = true)
        }

        val rawTemp = temperatureField?.let { values[it] }?.toDoubleOrNull()

        return StatusSnapshot(
            connected = connected,
            networkType = networkType,
            networkLabel = networkLabel(networkType),
            provider = values.firstOf("network_provider_fullname", "network_provider"),
            rsrp = values.dbmOf("Z5g_rsrp", "nr_rsrp", "network_lte_rsrp", "lte_rsrp", "rssi")
                ?: networkInfo?.rsrp,
            sinr = values.doubleOf("Z5g_snr", "Z5g_SINR", "network_sinr", "Nr_snr", "network_snr", "Lte_snr", "lte_snr")
                ?: networkInfo?.snr,
            rsrq = values.intOf("Z5g_rsrq", "lte_rsrq", "nr_rsrq")
                ?: networkInfo?.rsrq,
            band = values.firstOf(
                "network_ZCELLINFO_band",
                "nr5g_action_band",
                "wan_active_band",
                "network_Z5g_CELLINFO_band",
            ) ?: networkInfo?.band,
            pci = values.firstOf("network_Z_PCI", "network_Z5g_PCI", "lte_pci", "Lte_pci", "Nr_pci")
                ?: networkInfo?.pci,
            cellId = values.firstOf("network_Z5g_CELL_ID", "network_cell_id", "cell_id")
                ?: networkInfo?.cellId,
            downBps = values.longOf("flux_realtime_rx_thrpt", "realtime_rx_thrpt"),
            upBps = values.longOf("flux_realtime_tx_thrpt", "realtime_tx_thrpt"),
            monthRxBytes = values.longOf("flux_monthly_rx_bytes", "monthly_rx_bytes"),
            monthTxBytes = values.longOf("flux_monthly_tx_bytes", "monthly_tx_bytes"),
            sessionRxBytes = values.longOf("flux_realtime_rx_bytes", "realtime_rx_bytes"),
            sessionTxBytes = values.longOf("flux_realtime_tx_bytes", "realtime_tx_bytes"),
            uptimeSeconds = values.longOf("flux_realtime_time", "realtime_time"),
            wifiClients = values.intOf("wifi_access_sta_num", "station_num"),
            batteryPercent = values.intOf("battery_vol_percent"),
            charging = values.firstOf("battery_charging")?.let {
                it == "1" || it.equals("true", ignoreCase = true)
            },
            smsUnread = values.intOf("sms_unread_num", "sms_sim_unread_num"),
            // Some firmware returns 0 for "sensor not available"; never show it as 0 ℃.
            temperatureC = rawTemp
                ?.let { if (it > 200.0) it / 1000.0 else it }
                ?.takeIf { it > 0.0 && it < 150.0 },
            model = values.firstOf("model_name"),
            imei = values.firstOf("imei"),
            iccid = values.firstOf("iccid", "sim_iccid"),
            msisdn = values.firstOf("msisdn", "sim_msisdn"),
            wanIp = values.firstOf("wan_ipaddr", "ipv6_wan_ipaddr"),
            raw = values,
        )
    }

    fun networkLabel(type: String?): String = when {
        type == null -> "未知"
        type.equals("5G", ignoreCase = true) -> "5G"
        type.contains("ENDC", ignoreCase = true) -> "5G NSA"
        type.contains("NR5G", ignoreCase = true) || type.contains("NR", ignoreCase = true) -> "5G SA"
        type.contains("LTE", ignoreCase = true) -> "4G LTE"
        type.contains("WCDMA", ignoreCase = true) || type.contains("UMTS", ignoreCase = true) -> "3G"
        type.contains("GSM", ignoreCase = true) -> "2G"
        else -> type
    }

    /** Signal bucket used for colors and labels. */
    fun quality(rsrp: Int?): SignalQuality = when {
        rsrp == null -> SignalQuality.UNKNOWN
        rsrp >= -80 -> SignalQuality.EXCELLENT
        rsrp >= -90 -> SignalQuality.GOOD
        rsrp >= -100 -> SignalQuality.FAIR
        else -> SignalQuality.POOR
    }

    enum class SignalQuality { EXCELLENT, GOOD, FAIR, POOR, UNKNOWN }

    private fun Map<String, String>.firstOf(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> this[key]?.takeIf { it.isNotBlank() && it != "null" } }

    private fun Map<String, String>.intOf(vararg keys: String): Int? =
        firstOf(*keys)?.toDoubleOrNull()?.roundToInt()

    /** RSRP/RSSI must be negative dBm; network_rssi on U30 Pro is a bar level (0-5). */
    private fun Map<String, String>.dbmOf(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            firstOf(key)?.toDoubleOrNull()?.roundToInt()?.takeIf { it < 0 }
        }

    private fun Map<String, String>.longOf(vararg keys: String): Long? =
        firstOf(*keys)?.toDoubleOrNull()?.toLong()

    private fun Map<String, String>.doubleOf(vararg keys: String): Double? =
        firstOf(*keys)?.toDoubleOrNull()

    private data class NetworkInfo(
        val rsrp: Int?,
        val snr: Double?,
        val rsrq: Int?,
        val band: String?,
        val pci: String?,
        val cellId: String?,
    )

    /**
     * U30 Pro exposes the detailed NR metrics as a JSON-encoded string under
     * `network_information` (Nr_snr, Nr_rsrq, Nr_bands, Nr_pci, Nr_cell_id).
     */
    private fun parseNetworkInformation(text: String): NetworkInfo? {
        val obj = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        }.getOrNull() ?: return null

        fun value(key: String): String? = obj.string(key)

        val rawBand = value("Nr_bands") ?: value("lte_band")
        val band = rawBand?.takeIf { it.isNotBlank() }?.let { bands ->
            if (bands.all { it.isDigit() }) "n$bands" else bands
        }

        return NetworkInfo(
            rsrp = value("nr_rsrp")?.toIntOrNull()
                ?: value("Nr_signal_strength")?.toIntOrNull()
                ?: value("lte_rsrp")?.toIntOrNull(),
            snr = value("Nr_snr")?.toDoubleOrNull() ?: value("lte_snr")?.toDoubleOrNull(),
            rsrq = value("nr_rsrq")?.toIntOrNull() ?: value("lte_rsrq")?.toIntOrNull(),
            band = band,
            pci = value("Nr_pci") ?: value("Lte_pci"),
            cellId = value("Nr_cell_id") ?: value("network_cell_id"),
        )
    }
}