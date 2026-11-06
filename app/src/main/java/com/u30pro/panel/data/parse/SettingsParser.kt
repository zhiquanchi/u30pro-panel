package com.u30pro.panel.data.parse

import com.u30pro.panel.data.settings.AccessPointProfile
import com.u30pro.panel.data.settings.ApnProfile
import com.u30pro.panel.data.settings.SettingsSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Flattens the raw goform responses used by the management screen.
 *
 * `values` keeps empty strings ("key exists but is empty") distinct from absent
 * keys, which is how the UI hides unsupported settings.
 */
object SettingsParser {

    fun build(raw: Map<String, JsonElement>, now: Long = System.currentTimeMillis()): SettingsSnapshot {
        val values = linkedMapOf<String, String?>()

        for ((key, element) in raw) {
            if (element is JsonNull) {
                values[key] = null
                continue
            }
            val primitive = element as? JsonPrimitive ?: continue
            values[key] = primitive.contentOrNull ?: ""
        }

        // Some responses wrap arrays/objects; keep a readable string form for
        // read-only rows and the locked-cell check.
        raw["locked_cell_info"]?.let { values["locked_cell_info"] = it.toString() }

        val accessPoints = parseAccessPoints(raw["ResponseList"])
        accessPoints.forEach { ap ->
            val prefix = "ap_${ap.chipIndex}"
            values["${prefix}_ssid"] = ap.ssid
            values["${prefix}_auth"] = ap.authMode
            values["${prefix}_encrypt"] = ap.encrypType
            values["${prefix}_password"] = ap.password
            values["${prefix}_password_encoded"] = ap.passwordEncoded
            values["${prefix}_hidden"] = if (ap.hidden) "1" else "0"
            values["${prefix}_isolate"] = if (ap.isolate) "1" else "0"
            values["${prefix}_max_station"] = ap.maxStations?.toString() ?: ""
            values["${prefix}_switch"] = if (ap.switchOn) "1" else "0"
        }

        val apns = parseApns(raw)

        return SettingsSnapshot(
            values = values,
            accessPoints = accessPoints,
            apns = apns,
            fetchedAt = now,
        )
    }

    fun parseAccessPoints(element: JsonElement?): List<AccessPointProfile> {
        val array = when (element) {
            is kotlinx.serialization.json.JsonArray -> element
            is JsonPrimitive -> {
                val text = element.contentOrNull ?: return emptyList()
                runCatching { kotlinx.serialization.json.Json.parseToJsonElement(text) }
                    .getOrNull() as? kotlinx.serialization.json.JsonArray
                    ?: return emptyList()
            }
            else -> return emptyList()
        }
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val passwordEncoded = obj.str("Password").orEmpty()
            val password = decodeBase64(passwordEncoded)?.toString(Charsets.UTF_8) ?: passwordEncoded
            AccessPointProfile(
                chipIndex = obj.str("ChipIndex")?.toIntOrNull() ?: 0,
                apIndex = obj.str("AccessPointIndex")?.toIntOrNull() ?: 0,
                switchOn = obj.str("AccessPointSwitchStatus") == "1",
                ssid = obj.str("SSID").orEmpty(),
                authMode = obj.str("AuthMode").orEmpty(),
                encrypType = obj.str("EncrypType").orEmpty(),
                password = password,
                passwordEncoded = passwordEncoded,
                hidden = obj.str("ApBroadcastDisabled") == "1",
                isolate = obj.str("ApIsolate") == "1",
                maxStations = obj.str("ApMaxStationNumber")?.toIntOrNull(),
                pmf = obj.str("Pmf_switch") == "true" || obj.str("Pmf_switch") == "1",
            )
        }
    }

    /**
     * APN_configN format (from the stock mobile_network page):
     *   name($)apn($)mode($)dial($)auth($)username($)password($)pdp
     */
    fun parseApns(raw: Map<String, JsonElement>): List<ApnProfile> {
        val currentName = (raw["profile_name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val currentApn = (raw["apn_wan_apn"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val entries = mutableListOf<ApnProfile>()
        for (index in 0 until 20) {
            val text = (raw["APN_config$index"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            if (text.isBlank()) continue
            parseApn(index, text, currentName, currentApn)?.let { entries.add(it) }
        }
        if (entries.isEmpty()) {
            val auto = (raw["apn_auto_config"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            parseApn(0, auto, currentName, currentApn)?.let { entries.add(it) }
        }
        return entries
    }

    private fun parseApn(
        index: Int,
        raw: String,
        currentName: String,
        currentApn: String,
    ): ApnProfile? {
        if (raw.isBlank()) return null
        val parts = raw.split("($)")
        fun at(i: Int): String = parts.getOrNull(i)?.trim().orEmpty()
        val name = at(0)
        val apn = at(1)
        // APN_config0 is often a "Default" placeholder with an empty APN while
        // the modem uses the auto-configured APN. Editing that entry would
        // break data, so keep it out of the editable list.
        if (apn.isBlank()) return null
        val isDefault = if (currentName.isNotBlank() || currentApn.isNotBlank()) {
            (currentName.isNotBlank() && name == currentName) ||
                (currentApn.isNotBlank() && apn == currentApn)
        } else {
            index == 0
        }
        return ApnProfile(
            index = index,
            name = name,
            apn = apn,
            mode = at(2),
            dial = at(3),
            auth = at(4),
            username = at(5),
            password = at(6),
            pdpType = at(7),
            raw = raw,
            isDefault = isDefault,
        )
    }

    private fun JsonObject.str(key: String): String? {
        val value = get(key) ?: return null
        if (value is JsonNull) return null
        return (value as? JsonPrimitive)?.contentOrNull
    }

    private fun decodeBase64(text: String): ByteArray? {
        if (text.isBlank()) return null
        return runCatching { java.util.Base64.getDecoder().decode(text) }.getOrNull()
    }
}