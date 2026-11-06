package com.u30pro.panel.data.export

import com.u30pro.panel.data.model.StationSnapshot
import com.u30pro.panel.data.model.StatusSnapshot
import com.u30pro.panel.data.profile.ProbeReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant

private val exportJson = Json {
    encodeDefaults = true
}

/**
 * Builds the payload pulled over adb when the computer is not on the device
 * WiFi. `status.raw` is the important part: it exposes fields the stock page
 * shows but this app does not parse yet.
 *
 * The schema is app-side camelCase; it is semantically aligned with
 * tools/probe.mjs, not byte-identical.
 */
fun buildDiagnosticJson(
    appVersion: String,
    probe: ProbeReport?,
    status: StatusSnapshot?,
    stations: StationSnapshot?,
    generatedAt: Long,
): JsonObject = buildJsonObject {
    put("generatedAt", Instant.ofEpochMilli(generatedAt).toString())
    put("generatedAtMillis", generatedAt)
    put("source", "android-app")
    put("appVersion", appVersion)
    put("probe", probe?.let { exportJson.encodeToJsonElement(ProbeReport.serializer(), it) } ?: JsonNull)

    put(
        "status",
        status?.let { snapshot ->
            buildJsonObject {
                put("connected", snapshot.connected)
                put("networkType", snapshot.networkType)
                put("networkLabel", snapshot.networkLabel)
                put("provider", snapshot.provider)
                put("rsrp", snapshot.rsrp)
                put("sinr", snapshot.sinr)
                put("rsrq", snapshot.rsrq)
                put("band", snapshot.band)
                put("pci", snapshot.pci)
                put("cellId", snapshot.cellId)
                put("downBps", snapshot.downBps)
                put("upBps", snapshot.upBps)
                put("monthRxBytes", snapshot.monthRxBytes)
                put("monthTxBytes", snapshot.monthTxBytes)
                put("sessionRxBytes", snapshot.sessionRxBytes)
                put("sessionTxBytes", snapshot.sessionTxBytes)
                put("uptimeSeconds", snapshot.uptimeSeconds)
                put("wifiClients", snapshot.wifiClients)
                put("batteryPercent", snapshot.batteryPercent)
                put("charging", snapshot.charging)
                put("smsUnread", snapshot.smsUnread)
                put("temperatureC", snapshot.temperatureC)
                put("model", snapshot.model)
                put("imei", snapshot.imei)
                put("iccid", snapshot.iccid)
                put("msisdn", snapshot.msisdn)
                put("wanIp", snapshot.wanIp)
                putJsonObject("raw") {
                    snapshot.raw.toSortedMap().forEach { (key, value) -> put(key, value) }
                }
            }
        } ?: JsonNull,
    )

    putJsonObject("stations") {
        put("aclMode", stations?.aclMode)
        put("aclSupported", stations?.aclSupported)
        putJsonArray("blacklist") {
            stations?.blacklist?.sorted()?.forEach { add(JsonPrimitive(it)) }
        }
        putJsonArray("devices") {
            stations?.stations?.forEach { station ->
                addJsonObject {
                    put("mac", station.mac)
                    put("hostname", station.hostname)
                    put("ip", station.ip)
                    put("kind", station.kind.name)
                    put("blocked", station.blocked)
                }
            }
        }
    }
}