package com.u30pro.panel.data.settings

/** Groups rendered as cards on the management tab. */
enum class SettingGroup(val title: String) {
    NETWORK("移动网络"),
    WIFI("WiFi"),
    SYSTEM("系统与硬件"),
    TRAFFIC("流量"),
}

enum class SettingRisk {
    /** Safe, reversible, no confirmation. */
    SAFE,

    /** Needs a confirmation dialog. */
    CONFIRM,

    /** Can disconnect / reboot / lock; requires typing 确认. */
    STRONG,
}

data class SettingOption(val value: String, val label: String)

sealed interface ControlType {
    data object Toggle : ControlType
    data class Choice(val options: List<SettingOption>) : ControlType
    data class Text(
        val secret: Boolean = false,
        val numeric: Boolean = false,
        val hint: String? = null,
    ) : ControlType
    data object ReadOnly : ControlType
    data object Action : ControlType
}

data class SettingsSnapshot(
    val values: Map<String, String?> = emptyMap(),
    val accessPoints: List<AccessPointProfile> = emptyList(),
    val apns: List<ApnProfile> = emptyList(),
    val fetchedAt: Long = 0L,
) {
    fun raw(key: String): String? = values[key]

    fun text(key: String): String? = values[key]?.takeIf { it.isNotEmpty() }

    fun has(key: String): Boolean = values.containsKey(key)

    fun bool(key: String): Boolean? = when (raw(key)?.lowercase()) {
        "1", "on", "true", "enabled", "enable", "open" -> true
        "0", "off", "false", "disabled", "disable", "close" -> false
        else -> null
    }

    fun int(key: String): Int? = text(key)?.toIntOrNull()

    fun accessPoint(chip: Int = 0, index: Int = 0): AccessPointProfile? =
        accessPoints.firstOrNull { it.chipIndex == chip && it.apIndex == index }
}

data class AccessPointProfile(
    val chipIndex: Int,
    val apIndex: Int,
    val switchOn: Boolean,
    val ssid: String,
    val authMode: String,
    val encrypType: String,
    val password: String,
    val passwordEncoded: String,
    val hidden: Boolean,
    val isolate: Boolean,
    val maxStations: Int?,
    val pmf: Boolean,
)

data class ApnProfile(
    val index: Int,
    val name: String,
    val apn: String,
    val mode: String,
    val dial: String,
    val auth: String,
    val username: String,
    val password: String,
    val pdpType: String,
    val raw: String,
    val isDefault: Boolean = false,
)

data class SmsMessage(
    val id: Long,
    val number: String,
    val body: String,
    val time: String,
    val unread: Boolean,
)

data class WriteOutcome(
    val success: Boolean,
    val verified: Boolean? = null,
    val message: String? = null,
)

/**
 * Declarative description of one device setting. The UI renders controls from
 * this; the repository turns it into a goform write and read-back check.
 */
data class SettingSpec(
    val id: String,
    val group: SettingGroup,
    val title: String,
    val subtitle: String? = null,
    val control: ControlType,
    val requiredFields: List<String> = emptyList(),
    val display: (SettingsSnapshot) -> String? = { null },
    val risk: SettingRisk = SettingRisk.SAFE,
    val advanced: Boolean = false,
    val goformId: String? = null,
    val goformIdFor: (Map<String, String>) -> String? = { goformId },
    val buildParams: (Map<String, String>, SettingsSnapshot) -> Map<String, String> = { _, _ -> emptyMap() },
    val verifyFields: List<String> = emptyList(),
    val expected: (Map<String, String>, SettingsSnapshot) -> Map<String, String?> = { _, _ -> emptyMap() },
    val verifyTransform: (field: String, actual: String?) -> String? = { _, value -> value },
    val actionId: String? = null,
    val supported: (SettingsSnapshot) -> Boolean = { snapshot ->
        requiredFields.isEmpty() || requiredFields.any { snapshot.has(it) }
    },
)