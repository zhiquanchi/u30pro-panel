package com.u30pro.panel

import com.u30pro.panel.data.export.buildDiagnosticJson
import com.u30pro.panel.data.model.Station
import com.u30pro.panel.data.model.StationSnapshot
import com.u30pro.panel.data.model.StatusSnapshot
import com.u30pro.panel.data.profile.DeviceProfile
import com.u30pro.panel.data.profile.GroupReport
import com.u30pro.panel.data.profile.LoginScheme
import com.u30pro.panel.data.profile.ProbeReport
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticExportTest {

    private val profile = DeviceProfile(
        gateway = "192.168.0.1",
        port = 8080,
        loginScheme = LoginScheme.LD_SHA256,
        model = "U30 Pro",
        firmware = "U30ProV1.0.0B24/MU5358V1.0.0B24",
        availableFields = listOf("Z5g_rsrp", "station_list"),
        stationKeys = listOf("mac_addr"),
        aclSupported = true,
        temperatureField = null,
        probedAt = 123L,
    )

    private val probe = ProbeReport(
        profile = profile,
        groups = mapOf("信号" to GroupReport(requested = 3, available = 2, missing = listOf("Nr_snr"))),
        notes = listOf("probe note"),
    )

    private val status = StatusSnapshot(
        connected = true,
        networkType = "ENDC",
        networkLabel = "5G NSA",
        rsrp = -88,
        downBps = 1024,
        raw = linkedMapOf("Z5g_rsrp" to "-88", "firmware_custom_field" to "v"),
    )

    private val stations = StationSnapshot(
        stations = listOf(
            Station(
                mac = "AA:BB:CC:DD:EE:01",
                hostname = "Phone",
                ip = "192.168.0.2",
                kind = Station.Kind.WIFI,
                blocked = true,
            ),
        ),
        aclMode = 2,
        blacklist = setOf("AA:BB:CC:DD:EE:01"),
        aclSupported = true,
    )

    @Test
    fun `export contains probe status raw and stations`() {
        val json = buildDiagnosticJson(
            appVersion = "0.1.0",
            probe = probe,
            status = status,
            stations = stations,
            generatedAt = 1_760_000_000_000L,
        )

        assertEquals("android-app", json["source"]!!.jsonPrimitive.content)
        assertEquals("0.1.0", json["appVersion"]!!.jsonPrimitive.content)

        val probeJson = json["probe"]!!.jsonObject
        assertEquals("192.168.0.1", probeJson["profile"]!!.jsonObject["gateway"]!!.jsonPrimitive.content)
        assertEquals(8080, probeJson["profile"]!!.jsonObject["port"]!!.jsonPrimitive.int)
        assertEquals(
            2,
            probeJson["groups"]!!.jsonObject["信号"]!!.jsonObject["available"]!!.jsonPrimitive.int,
        )
        assertEquals("probe note", probeJson["notes"]!!.jsonArray[0].jsonPrimitive.content)

        val statusJson = json["status"]!!.jsonObject
        assertEquals(-88, statusJson["rsrp"]!!.jsonPrimitive.int)
        assertEquals(
            "-88",
            statusJson["raw"]!!.jsonObject["Z5g_rsrp"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "v",
            statusJson["raw"]!!.jsonObject["firmware_custom_field"]!!.jsonPrimitive.content,
        )

        val stationsJson = json["stations"]!!.jsonObject
        assertEquals(2, stationsJson["aclMode"]!!.jsonPrimitive.int)
        assertEquals(
            "AA:BB:CC:DD:EE:01",
            stationsJson["blacklist"]!!.jsonArray[0].jsonPrimitive.content,
        )
        val device = stationsJson["devices"]!!.jsonArray[0].jsonObject
        assertEquals("WIFI", device["kind"]!!.jsonPrimitive.content)
        assertTrue(device["blocked"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `export handles missing sections`() {
        val json = buildDiagnosticJson(
            appVersion = "0.1.0",
            probe = null,
            status = null,
            stations = null,
            generatedAt = 0L,
        )

        assertEquals(JsonNull, json["probe"])
        assertEquals(JsonNull, json["status"])
        assertEquals(0, json["stations"]!!.jsonObject["devices"]!!.jsonArray.size)
        assertEquals("1970-01-01T00:00:00Z", json["generatedAt"]!!.jsonPrimitive.content)
    }
}