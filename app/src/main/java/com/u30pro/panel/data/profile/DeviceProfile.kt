package com.u30pro.panel.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LoginScheme {
    @SerialName("ld-sha256")
    LD_SHA256,

    @SerialName("base64")
    BASE64,
}

/**
 * Result of the first-contact capability probe. Everything the UI shows is
 * gated on this, because U30 Pro firmware differs from U30 Air and cannot be
 * assumed to expose the same goform fields.
 */
@Serializable
data class DeviceProfile(
    val gateway: String = "192.168.0.1",
    val port: Int = 80,
    val loginScheme: LoginScheme = LoginScheme.LD_SHA256,
    val model: String? = null,
    val firmware: String? = null,
    val availableFields: List<String> = emptyList(),
    val stationKeys: List<String> = emptyList(),
    val aclSupported: Boolean = false,
    val renameSupported: Boolean = false,
    val temperatureField: String? = null,
    val probedAt: Long = 0L,
) {
    fun supports(field: String): Boolean = availableFields.isEmpty() || field in availableFields

    fun firstSupported(vararg fields: String): String? =
        fields.firstOrNull { supports(it) }
}

@Serializable
data class GroupReport(
    val requested: Int,
    val available: Int,
    val missing: List<String> = emptyList(),
)

@Serializable
data class ProbeReport(
    val profile: DeviceProfile,
    val groups: Map<String, GroupReport> = emptyMap(),
    val notes: List<String> = emptyList(),
)