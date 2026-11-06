package com.u30pro.panel.data.settings

import java.util.Base64

/**
 * Registry of device settings. Commands/params below were extracted from the
 * U30 Pro B24 stock pages (js/wifi/wifi_main.js, js/network/mobile_network.js,
 * js/adm pages) and verified against real reads where possible.
 *
 * Items whose required field is absent on the device are hidden by [supported].
 */
object SettingsCatalog {

    val specs: List<SettingSpec> = buildList {
        addAll(networkSpecs())
        addAll(wifiSpecs())
        addAll(systemSpecs())
        addAll(trafficSpecs())
    }

    fun byId(id: String): SettingSpec? = specs.firstOrNull { it.id == id }

    fun supported(group: SettingGroup, snapshot: SettingsSnapshot): List<SettingSpec> =
        specs.filter { it.group == group && it.supported(snapshot) }

    // ---------------------------------------------------------------------
    // network
    // ---------------------------------------------------------------------

    private fun networkSpecs(): List<SettingSpec> = listOf(
        SettingSpec(
            id = "mobile_data",
            group = SettingGroup.NETWORK,
            title = "移动数据",
            subtitle = "断开/恢复蜂窝数据连接",
            control = ControlType.Toggle,
            requiredFields = listOf("ppp_status"),
            display = { snap ->
                val status = snap.text("ppp_status")
                when {
                    status == null -> "未知"
                    status.contains("disconnect", ignoreCase = true) -> "已断开"
                    status.contains("connect", ignoreCase = true) -> "已连接"
                    else -> status
                }
            },
            risk = SettingRisk.CONFIRM,
            goformIdFor = { input ->
                if (input["value"] == "1") "CONNECT_NETWORK" else "DISCONNECT_NETWORK"
            },
        ),
        SettingSpec(
            id = "network_mode",
            group = SettingGroup.NETWORK,
            title = "网络模式",
            subtitle = "优先驻留 5G / 4G / 自动",
            control = ControlType.Choice(
                listOf(
                    SettingOption("auto", "自动"),
                    SettingOption("5g", "仅 5G"),
                    SettingOption("4g", "仅 4G"),
                ),
            ),
            requiredFields = listOf("network_net_select_mode"),
            display = { snap ->
                when (snap.text("network_net_select_mode")) {
                    "auto_select" -> "自动"
                    "manual_select" -> "手动选择"
                    else -> null
                }
            },
            risk = SettingRisk.CONFIRM,
            goformId = "SET_BEARER_PREFERENCE",
            buildParams = { input, _ ->
                mapOf(
                    "BearerPreference" to when (input["value"]) {
                        "5g" -> "Only_5G"
                        "4g" -> "Only_LTE"
                        else -> "NETWORK_auto"
                    },
                )
            },
        ),
        SettingSpec(
            id = "roaming",
            group = SettingGroup.NETWORK,
            title = "数据漫游",
            subtitle = "允许连接漫游网络",
            control = ControlType.Toggle,
            requiredFields = listOf("autoConnectWhenRoaming"),
            display = { onOff(it.bool("autoConnectWhenRoaming")) },
            risk = SettingRisk.CONFIRM,
            goformId = "SET_CONNECTION_MODE",
            buildParams = { input, snap ->
                linkedMapOf(
                    "ConnectionMode" to (snap.text("connectionMode") ?: "auto_dial"),
                    "dial_roam_setting_option" to if (input["value"] == "1") "on" else "off",
                )
            },
            verifyFields = listOf("autoConnectWhenRoaming"),
            expected = { input, _ ->
                mapOf("autoConnectWhenRoaming" to input["value"])
            },
            verifyTransform = { _, actual ->
                when (actual?.lowercase()) {
                    "on", "1", "enable", "enabled" -> "1"
                    "off", "0", "disable", "disabled" -> "0"
                    else -> actual
                }
            },
        ),
        SettingSpec(
            id = "band_lock_info",
            group = SettingGroup.NETWORK,
            title = "频段锁定",
            subtitle = "当前锁定掩码（只读）",
            control = ControlType.ReadOnly,
            requiredFields = listOf("lte_band_lock", "nr_band_lock"),
            display = { snap ->
                val lte = snap.text("lte_band_lock").orEmpty().split(',').count { it.isNotBlank() }
                val nr = snap.text("nr_band_lock").orEmpty().split(',').count { it.isNotBlank() }
                "LTE $lte 段 · NR $nr 段"
            },
        ),
        SettingSpec(
            id = "unlock_cells",
            group = SettingGroup.NETWORK,
            title = "解除小区/频段锁定",
            subtitle = "恢复到运营商默认选网",
            control = ControlType.Action,
            requiredFields = listOf("locked_cell_info"),
            display = { snap ->
                val raw = snap.raw("locked_cell_info")
                if (raw.isNullOrBlank() || raw == "[]") "无锁定" else "存在锁定"
            },
            risk = SettingRisk.STRONG,
            advanced = true,
            actionId = "UNLOCK_CELLS",
        ),
        SettingSpec(
            id = "open_apn",
            group = SettingGroup.NETWORK,
            title = "APN 配置",
            subtitle = "查看、编辑、设为默认或删除",
            control = ControlType.Action,
            display = { snap ->
                if (snap.apns.isEmpty()) {
                    snap.text("apn_wan_apn")?.let { "当前 $it" } ?: "无配置"
                } else {
                    "${snap.apns.size} 个配置"
                }
            },
            actionId = "OPEN_APN",
        ),
    )

    // ---------------------------------------------------------------------
    // wifi
    // ---------------------------------------------------------------------

    private fun wifiSpecs(): List<SettingSpec> = listOf(
        SettingSpec(
            id = "wifi_radio",
            group = SettingGroup.WIFI,
            title = "WiFi 开关",
            subtitle = "关闭会断开当前手机与设备的连接",
            control = ControlType.Toggle,
            requiredFields = listOf("WiFiModuleSwitch"),
            display = { onOff(it.bool("WiFiModuleSwitch")) },
            risk = SettingRisk.STRONG,
            goformId = "switchWiFiModule",
            buildParams = { input, _ ->
                mapOf("SwitchOption" to if (input["value"] == "1") "1" else "0")
            },
            verifyFields = listOf("WiFiModuleSwitch"),
            expected = { input, _ -> mapOf("WiFiModuleSwitch" to input["value"]) },
        ),
        SettingSpec(
            id = "wifi_ssid",
            group = SettingGroup.WIFI,
            title = "WiFi 名称",
            subtitle = "主 SSID，写入后已连接设备需重新连接",
            control = ControlType.Text(hint = "新的 WiFi 名称"),
            requiredFields = listOf("ap_0_ssid"),
            display = { it.accessPoint()?.ssid ?: "—" },
            risk = SettingRisk.STRONG,
            goformId = "setAccessPointInfo",
            buildParams = { input, snap -> apParams(mapOf("ssid" to input["value"].orEmpty()), snap) },
            verifyFields = listOf("ap_0_ssid"),
            expected = { input, _ -> mapOf("ap_0_ssid" to input["value"]) },
        ),
        SettingSpec(
            id = "wifi_password",
            group = SettingGroup.WIFI,
            title = "WiFi 密码",
            subtitle = "WPA2 密码，至少 8 位",
            control = ControlType.Text(secret = true, hint = "新的 WiFi 密码"),
            requiredFields = listOf("ap_0_password"),
            display = { snap ->
                if (snap.accessPoint()?.password.isNullOrEmpty()) "未设置" else "已设置"
            },
            risk = SettingRisk.STRONG,
            goformId = "setAccessPointInfo",
            buildParams = { input, snap -> apParams(mapOf("password" to input["value"].orEmpty()), snap) },
            verifyFields = listOf("ap_0_password"),
            expected = { input, _ -> mapOf("ap_0_password" to input["value"]) },
        ),
        SettingSpec(
            id = "wifi_hidden",
            group = SettingGroup.WIFI,
            title = "隐藏 WiFi 名称",
            subtitle = "关闭 SSID 广播",
            control = ControlType.Toggle,
            requiredFields = listOf("ap_0_hidden"),
            display = { onOff(it.accessPoint()?.hidden) },
            risk = SettingRisk.CONFIRM,
            goformId = "setAccessPointInfo",
            buildParams = { input, snap -> apParams(mapOf("hidden" to input["value"].orEmpty()), snap) },
            verifyFields = listOf("ap_0_hidden"),
            expected = { input, _ -> mapOf("ap_0_hidden" to input["value"]) },
        ),
        SettingSpec(
            id = "wifi_max_stations",
            group = SettingGroup.WIFI,
            title = "最大连接数",
            subtitle = "同时连接的 WiFi 客户端上限",
            control = ControlType.Text(numeric = true, hint = "1 - 16"),
            requiredFields = listOf("ap_0_max_station"),
            display = { it.accessPoint()?.maxStations?.toString() ?: "—" },
            risk = SettingRisk.CONFIRM,
            goformId = "setAccessPointInfo",
            buildParams = { input, snap -> apParams(mapOf("max" to input["value"].orEmpty()), snap) },
            verifyFields = listOf("ap_0_max_station"),
            expected = { input, _ -> mapOf("ap_0_max_station" to input["value"]) },
        ),
        SettingSpec(
            id = "wifi_isolate",
            group = SettingGroup.WIFI,
            title = "AP 隔离",
            subtitle = "禁止已连接客户端互访",
            control = ControlType.Toggle,
            requiredFields = listOf("ap_0_isolate"),
            display = { onOff(it.accessPoint()?.isolate) },
            risk = SettingRisk.CONFIRM,
            goformId = "setAccessPointInfo",
            buildParams = { input, snap -> apParams(mapOf("isolate" to input["value"].orEmpty()), snap) },
            verifyFields = listOf("ap_0_isolate"),
            expected = { input, _ -> mapOf("ap_0_isolate" to input["value"]) },
        ),
    )

    // ---------------------------------------------------------------------
    // system / power / hardware
    // ---------------------------------------------------------------------

    private fun systemSpecs(): List<SettingSpec> = listOf(
        SettingSpec(
            id = "indicator_light",
            group = SettingGroup.SYSTEM,
            title = "指示灯",
            subtitle = "机身 LED 开关",
            control = ControlType.Toggle,
            requiredFields = listOf("indicator_light_switch"),
            display = { onOff(it.bool("indicator_light_switch")) },
            risk = SettingRisk.SAFE,
            goformId = "INDICATOR_LIGHT_SETTING",
            buildParams = toggleParams("indicator_light_switch"),
            verifyFields = listOf("indicator_light_switch"),
            expected = toggleExpected("indicator_light_switch"),
        ),
        SettingSpec(
            id = "usb_port",
            group = SettingGroup.SYSTEM,
            title = "USB 端口",
            subtitle = "切换 USB 功能，可能导致连接中断",
            control = ControlType.Toggle,
            requiredFields = listOf("usb_port_switch"),
            display = { onOff(it.bool("usb_port_switch")) },
            risk = SettingRisk.STRONG,
            goformId = "USB_PORT_SETTING",
            buildParams = toggleParams("usb_port_switch"),
            verifyFields = listOf("usb_port_switch"),
            expected = toggleExpected("usb_port_switch"),
        ),
        SettingSpec(
            id = "usb_protocol",
            group = SettingGroup.SYSTEM,
            title = "USB 网络协议",
            subtitle = "修改后设备会重启 USB 网络",
            control = ControlType.Choice(
                listOf(
                    SettingOption("0", "自动"),
                    SettingOption("1", "RNDIS"),
                    SettingOption("2", "CDC-ECM"),
                ),
            ),
            requiredFields = listOf("usb_network_protocal"),
            display = { snap ->
                when (snap.text("usb_network_protocal")) {
                    "0" -> "自动"
                    "1" -> "RNDIS"
                    "2" -> "CDC-ECM"
                    else -> null
                }
            },
            risk = SettingRisk.STRONG,
            goformId = "SET_USB_NETWORK_PROTOCAL",
            buildParams = { input, _ -> mapOf("usb_network_protocal" to input["value"].orEmpty()) },
            verifyFields = listOf("usb_network_protocal"),
            expected = { input, _ -> mapOf("usb_network_protocal" to input["value"]) },
        ),
        SettingSpec(
            id = "samba",
            group = SettingGroup.SYSTEM,
            title = "文件共享 (Samba)",
            subtitle = "共享设备存储",
            control = ControlType.Toggle,
            requiredFields = listOf("samba_switch"),
            display = { onOff(it.bool("samba_switch")) },
            risk = SettingRisk.CONFIRM,
            goformId = "SAMBA_SETTING",
            buildParams = { input, snap ->
                linkedMapOf(
                    "samba_switch" to if (input["value"] == "1") "1" else "0",
                ).apply {
                    if (input["value"] == "1") {
                        snap.text("samba_password")?.let { put("samba_password", it) }
                    }
                }
            },
            verifyFields = listOf("samba_switch"),
            expected = toggleExpected("samba_switch"),
        ),
        SettingSpec(
            id = "performance_mode",
            group = SettingGroup.SYSTEM,
            title = "性能模式",
            subtitle = "优先性能或功耗",
            control = ControlType.Toggle,
            requiredFields = listOf("performance_mode"),
            display = { onOff(it.bool("performance_mode")) },
            risk = SettingRisk.CONFIRM,
            goformId = "PERFORMANCE_MODE_SETTING",
            buildParams = toggleParams("performance_mode"),
            verifyFields = listOf("performance_mode"),
            expected = toggleExpected("performance_mode"),
        ),
        SettingSpec(
            id = "battery_protection",
            group = SettingGroup.SYSTEM,
            title = "电池保护",
            subtitle = "限制充电上限以延长电池寿命",
            control = ControlType.Toggle,
            requiredFields = listOf("battery_protection"),
            display = { onOff(it.bool("battery_protection")) },
            risk = SettingRisk.SAFE,
            goformId = "BATTERY_PROTECTION_SETTING",
            buildParams = toggleParams("battery_protection"),
            verifyFields = listOf("battery_protection"),
            expected = toggleExpected("battery_protection"),
        ),
        SettingSpec(
            id = "sim_slot",
            group = SettingGroup.SYSTEM,
            title = "SIM 卡槽",
            subtitle = "切换后移动网络会短暂中断",
            control = ControlType.Choice(
                listOf(
                    SettingOption("0", "卡槽 1"),
                    SettingOption("1", "卡槽 2"),
                ),
            ),
            requiredFields = listOf("sim_slot"),
            display = { snap ->
                when (snap.text("sim_slot")) {
                    "0" -> "卡槽 1"
                    "1" -> "卡槽 2"
                    else -> null
                }
            },
            risk = SettingRisk.STRONG,
            supported = { snap -> snap.has("sim_slot") && snap.text("dual_sim_support") == "1" },
            goformId = "SET_SIM_SLOT",
            buildParams = { input, _ -> mapOf("sim_slot" to input["value"].orEmpty()) },
            verifyFields = listOf("sim_slot"),
            expected = { input, _ -> mapOf("sim_slot" to input["value"]) },
        ),
        SettingSpec(
            id = "sim_pin_status",
            group = SettingGroup.SYSTEM,
            title = "SIM PIN 状态",
            subtitle = "为避免锁卡，App 只展示状态、不修改 PIN",
            control = ControlType.ReadOnly,
            requiredFields = listOf("sim_pin_status"),
            display = { snap -> onOff(snap.bool("sim_pin_status")) },
        ),
        SettingSpec(
            id = "ota_check",
            group = SettingGroup.SYSTEM,
            title = "检查固件更新",
            subtitle = "只检查，不下载或安装",
            control = ControlType.Action,
            requiredFields = listOf("new_version_state"),
            display = { snap ->
                when (snap.text("new_version_state")) {
                    "version_idle" -> "空闲"
                    "version_checking" -> "检查中"
                    "version_has_new" -> "有新版本"
                    null -> "—"
                    else -> snap.text("new_version_state")
                }
            },
            risk = SettingRisk.SAFE,
            actionId = "OTA_CHECK",
        ),
        SettingSpec(
            id = "reboot",
            group = SettingGroup.SYSTEM,
            title = "重启设备",
            subtitle = "所有客户端会断开，约 1 分钟后恢复",
            control = ControlType.Action,
            display = { "谨慎操作" },
            risk = SettingRisk.STRONG,
            advanced = true,
            actionId = "REBOOT",
        ),
        SettingSpec(
            id = "open_sms",
            group = SettingGroup.SYSTEM,
            title = "短信",
            subtitle = "查看、发送和删除短信",
            control = ControlType.Action,
            display = { snap ->
                val unread = snap.int("sms_unread_num")
                if (unread == null || unread == 0) "打开" else "$unread 条未读"
            },
            actionId = "OPEN_SMS",
        ),
    )

    // ---------------------------------------------------------------------
    // traffic
    // ---------------------------------------------------------------------

    private fun trafficSpecs(): List<SettingSpec> = listOf(
        SettingSpec(
            id = "data_limit_switch",
            group = SettingGroup.TRAFFIC,
            title = "套餐限额",
            subtitle = "达到限额后提醒或断网",
            control = ControlType.Toggle,
            requiredFields = listOf("flux_data_volume_limit_switch"),
            display = { onOff(it.bool("flux_data_volume_limit_switch")) },
            risk = SettingRisk.CONFIRM,
            goformId = "DATA_LIMIT_SETTING",
            buildParams = { input, snap ->
                trafficParams(mapOf("switch" to input["value"].orEmpty()), snap)
            },
            verifyFields = listOf("flux_data_volume_limit_switch"),
            expected = { input, _ ->
                mapOf("flux_data_volume_limit_switch" to if (input["value"] == "1") "1" else "0")
            },
        ),
        SettingSpec(
            id = "data_limit_size",
            group = SettingGroup.TRAFFIC,
            title = "限额大小",
            subtitle = "格式：数值_单位，1024=GB，1=MB",
            control = ControlType.Text(hint = "例如 300_1024"),
            requiredFields = listOf("flux_data_volume_limit_size"),
            display = { snap -> snap.text("flux_data_volume_limit_size") ?: "—" },
            risk = SettingRisk.CONFIRM,
            goformId = "DATA_LIMIT_SETTING",
            buildParams = { input, snap ->
                trafficParams(mapOf("size" to input["value"].orEmpty()), snap)
            },
            verifyFields = listOf("flux_data_volume_limit_size"),
            expected = { input, _ -> mapOf("flux_data_volume_limit_size" to input["value"]) },
        ),
        SettingSpec(
            id = "data_alert",
            group = SettingGroup.TRAFFIC,
            title = "提醒阈值",
            subtitle = "已用百分比达到该值时提醒",
            control = ControlType.Text(numeric = true, hint = "1 - 100"),
            requiredFields = listOf("flux_data_volume_alert_percent"),
            display = { snap -> snap.text("flux_data_volume_alert_percent")?.let { "$it%" } ?: "—" },
            risk = SettingRisk.CONFIRM,
            goformId = "DATA_LIMIT_SETTING",
            buildParams = { input, snap ->
                trafficParams(mapOf("alert" to input["value"].orEmpty()), snap)
            },
            verifyFields = listOf("flux_data_volume_alert_percent"),
            expected = { input, _ -> mapOf("flux_data_volume_alert_percent" to input["value"]) },
        ),
        SettingSpec(
            id = "data_clear_date",
            group = SettingGroup.TRAFFIC,
            title = "每月清零日",
            subtitle = "每月几号重置统计（1-28）",
            control = ControlType.Text(numeric = true, hint = "1 - 28"),
            requiredFields = listOf("flux_clear_date"),
            display = { snap -> snap.text("flux_clear_date")?.let { "每月 $it 日" } ?: "—" },
            risk = SettingRisk.CONFIRM,
            goformId = "DATA_LIMIT_SETTING",
            buildParams = { input, snap ->
                trafficParams(mapOf("date" to input["value"].orEmpty()), snap)
            },
            verifyFields = listOf("flux_clear_date"),
            expected = { input, _ -> mapOf("flux_clear_date" to input["value"]) },
        ),
        SettingSpec(
            id = "reset_traffic",
            group = SettingGroup.TRAFFIC,
            title = "清零流量统计",
            subtitle = "重置本月与本次会话计数",
            control = ControlType.Action,
            display = { "立即清零" },
            risk = SettingRisk.CONFIRM,
            actionId = "RESET_TRAFFIC",
        ),
    )

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private fun onOff(value: Boolean?): String = when (value) {
        true -> "开"
        false -> "关"
        null -> "—"
    }

    private fun toggleParams(field: String): (Map<String, String>, SettingsSnapshot) -> Map<String, String> =
        { input, _ -> mapOf(field to if (input["value"] == "1") "1" else "0") }

    private fun toggleExpected(field: String): (Map<String, String>, SettingsSnapshot) -> Map<String, String?> =
        { input, _ -> mapOf(field to if (input["value"] == "1") "1" else "0") }

    /**
     * The stock WiFi page writes the whole AP object, so every edit sends the
     * current values for the other fields.
     */
    private fun apParams(input: Map<String, String>, snap: SettingsSnapshot): Map<String, String> {
        val ap = snap.accessPoint() ?: return emptyMap()
        val ssid = input["ssid"] ?: ap.ssid
        val password = input["password"] ?: ap.password
        val hidden = input["hidden"]?.let { it == "1" } ?: ap.hidden
        val isolate = input["isolate"]?.let { it == "1" } ?: ap.isolate
        val maxStations = input["max"] ?: ap.maxStations?.toString() ?: "5"
        val encodedPassword = Base64.getEncoder().encodeToString(password.toByteArray(Charsets.UTF_8))
        return linkedMapOf(
            "ChipIndex" to ap.chipIndex.toString(),
            "AccessPointIndex" to ap.apIndex.toString(),
            "AccessPointSwitchStatus" to "1",
            "SSID" to ssid,
            "AuthMode" to ap.authMode.ifBlank { "WPA2PSK" },
            "EncrypType" to ap.encrypType.ifBlank { "AES" },
            "Password" to encodedPassword,
            "ApBroadcastDisabled" to if (hidden) "1" else "0",
            "ApIsolate" to if (isolate) "1" else "0",
            "ApMaxStationNumber" to maxStations,
            "QrImageShow" to "1",
            "wifi_syncparas_flag" to "1",
            "lan_sec_ssid_control" to "0",
        )
    }

    private fun trafficParams(input: Map<String, String>, snap: SettingsSnapshot): Map<String, String> {
        return linkedMapOf(
            "flux_data_volume_limit_switch" to
                (input["switch"] ?: snap.text("flux_data_volume_limit_switch") ?: "1"),
            "flux_data_volume_limit_unit" to
                (snap.text("flux_data_volume_limit_unit") ?: "data"),
            "flux_data_volume_limit_size" to
                (input["size"] ?: snap.text("flux_data_volume_limit_size") ?: "300_1024"),
            "flux_data_volume_alert_percent" to
                (input["alert"] ?: snap.text("flux_data_volume_alert_percent") ?: "80"),
            "flux_auto_clear_flow_data_switch" to
                (snap.text("flux_auto_clear_flow_data_switch") ?: "off"),
            "flux_clear_date" to
                (input["date"] ?: snap.text("flux_clear_date") ?: "1"),
            "flux_limited_disconnect" to
                (snap.text("flux_limited_disconnect") ?: "0"),
        )
    }
}