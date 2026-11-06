package com.u30pro.panel.data.parse

import com.u30pro.panel.data.goform.parseObjectList
import com.u30pro.panel.data.goform.parseJsonObject
import com.u30pro.panel.data.goform.semicolonList
import com.u30pro.panel.data.goform.string
import com.u30pro.panel.data.model.Station
import com.u30pro.panel.data.model.StationSnapshot
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object StationParser {

    fun parse(
        stationList: JsonElement?,
        lanStationList: JsonElement?,
        aclElement: JsonElement?,
    ): StationSnapshot {
        val acl = aclElement.toJsonObject()
        val blacklist = (acl?.string("BlackMacList") ?: acl?.string("blackMacList"))
            .orEmpty()
            .semicolonList()
            .map { it.uppercase() }
            .toSet()
        val aclMode = acl?.string("AclMode")?.toIntOrNull()
            ?: acl?.string("aclMode")?.toIntOrNull()

        val wifi = parseObjectList(stationList).map { it.toStation(Station.Kind.WIFI) }
        val lan = parseObjectList(lanStationList).map { it.toStation(Station.Kind.LAN) }

        val merged = LinkedHashMap<String, Station>()
        for (station in wifi + lan) {
            if (station.mac.isBlank()) continue
            val existing = merged[station.mac]
            merged[station.mac] = if (existing == null) {
                station.copy(blocked = station.mac.uppercase() in blacklist)
            } else {
                existing.copy(
                    hostname = existing.hostname ?: station.hostname,
                    ip = existing.ip ?: station.ip,
                )
            }
        }

        return StationSnapshot(
            stations = merged.values.sortedWith(
                compareByDescending<Station> { it.kind == Station.Kind.WIFI }
                    .thenBy { it.displayName.lowercase() },
            ),
            aclMode = aclMode,
            blacklist = blacklist,
            aclSupported = acl != null,
        )
    }

    private fun JsonObject.toStation(kind: Station.Kind): Station {
        val mac = firstNonBlank("mac_addr", "mac", "macAddress", "MACAddress")
            ?.uppercase()
            .orEmpty()
        val hostname = firstNonBlank("hostname", "host_name", "HostName", "name", "device_name")
        val ip = firstNonBlank("ip_addr", "ip", "ipAddress", "IPAddr")
        return Station(mac = mac, hostname = hostname, ip = ip, kind = kind)
    }

    private fun JsonObject.firstNonBlank(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> string(key)?.takeIf { it.isNotBlank() } }

    private fun JsonElement?.toJsonObject(): JsonObject? = when (this) {
        is JsonObject -> this
        is JsonPrimitive -> contentOrNull?.let { parseJsonObject(it) }
        else -> null
    }
}