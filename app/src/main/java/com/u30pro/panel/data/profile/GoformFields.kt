package com.u30pro.panel.data.profile

/**
 * Candidate field names collected from verified U30 Air / F50 / MU5001 client
 * code. U30 Pro (MU5358 / UMS9632) is NOT field-verified, so the probe treats
 * every list as a candidate set and records what actually answered.
 */
object GoformFields {

    val SIGNAL = listOf(
        "network_type", "ppp_status", "network_provider", "network_provider_fullname",
        "network_signalbar", "signalbar", "rssi", "network_rssi",
        // Names confirmed from the U30 Pro B24 stock web UI (js/service.js).
        "Z5g_rsrp", "Z5g_snr", "Z5g_SINR", "Z5g_rsrq", "Z5g_rssi",
        "network_lte_rsrp", "lte_rsrp", "lte_rsrq", "lte_rssi", "lte_pci", "Lte_pci",
        "network_information",
        "network_sinr", "network_snr", "Lte_snr", "lte_snr", "network_ecio",
        "nr_rsrp", "nr_rsrq", "nr_rssi", "Nr_snr", "Nr_pci", "Nr_fcn",
        "network_Z_PCI", "network_Z5g_PCI", "network_ZCELLINFO_band", "network_Z5g_CELLINFO_band",
        "network_Z5g_dlEarfcn", "network_Z5g_CELL_ID", "network_cell_id", "cell_id",
        "nr5g_action_band", "nr5g_action_channel", "nr5g_nsa_bandwidth",
        "wan_active_band", "wan_active_channel", "wan_lte_ca",
        "network_lte_ca_pcell_arfcn", "network_lte_ca_pcell_band",
        "lte_ca_scell_arfcn", "bandwidth", "network_Z_dl_earfcn",
    ).distinct()

    val TRAFFIC = listOf(
        "flux_realtime_rx_thrpt", "flux_realtime_tx_thrpt",
        "flux_realtime_rx_bytes", "flux_realtime_tx_bytes", "flux_realtime_time",
        "flux_monthly_rx_bytes", "flux_monthly_tx_bytes", "flux_monthly_time",
        "realtime_rx_thrpt", "realtime_tx_thrpt",
        "realtime_rx_bytes", "realtime_tx_bytes", "realtime_time",
        "monthly_rx_bytes", "monthly_tx_bytes", "monthly_time",
        "data_volume_limit_switch", "data_volume_limit_size", "data_volume_alert_percent",
    )

    val DEVICE = listOf(
        "imei", "imsi", "iccid", "msisdn", "sim_msisdn", "sim_imsi", "sim_iccid",
        "sim_slot", "dual_sim_support", "mac_address", "lan_ipaddr", "lan_netmask",
        "wan_ipaddr", "ipv6_wan_ipaddr", "LocalDomain",
        "wa_inner_version", "cr_version", "wa_version", "web_version",
        "hardware_version", "model_name", "electron_id",
        "battery_value", "battery_vol_percent", "battery_charging",
        "sms_unread_num", "wifi_access_sta_num", "wifi_onoff_state", "wifi_5g_enable",
        "pin_status", "usb_port_switch", "loginfo", "new_version_state",
    )

    val TEMPERATURE = listOf(
        "temperature", "cpu_temp", "internal_temperature", "ic_temp",
        "cpu_utility", "mem_utility",
    )

    val WIFI = listOf(
        "wifi_chip1_ssid1_ssid", "wifi_chip1_ssid1_authmode", "wifi_chip1_ssid1_encryption",
        "queryAccessPointInfo",
    )

    val STATION = listOf("station_list", "lan_station_list", "station_num", "hostNameList")

    val ACL = listOf("queryDeviceAccessControlList")

    /** Ordered groups shown in the diagnostics screen. */
    val PROBE_GROUPS: Map<String, List<String>> = linkedMapOf(
        "信号" to SIGNAL,
        "流量" to TRAFFIC,
        "设备" to DEVICE,
        "温度" to TEMPERATURE,
        "WiFi" to WIFI,
    )

    val STATUS_READ: List<String> = SIGNAL + TRAFFIC + DEVICE + WIFI + TEMPERATURE
}