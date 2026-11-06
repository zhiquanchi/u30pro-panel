package com.u30pro.panel

import com.u30pro.panel.data.parse.SettingsParser
import com.u30pro.panel.data.parse.SmsCodec
import com.u30pro.panel.data.parse.SmsParser
import com.u30pro.panel.data.settings.SettingsCatalog
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsParserTest {

    private val d = "$"

    private fun apnLine(name: String, apn: String): String =
        "$name(${d})${apn}(${d})manual(${d})*99#(${d})none(${d})(${d})(${d})IPv4v6(${d})(${d})(${d})(${d})(${d})"

    private fun snapshot() = SettingsParser.build(
        mapOf(
            "WiFiModuleSwitch" to JsonPrimitive("1"),
            "indicator_light_switch" to JsonPrimitive("1"),
            "flux_data_volume_limit_switch" to JsonPrimitive("1"),
            "flux_data_volume_limit_size" to JsonPrimitive("300_1024"),
            "flux_data_volume_alert_percent" to JsonPrimitive("80"),
            "apn_auto_config" to JsonPrimitive(apnLine("China Mobile", "CMIOT5G")),
            "profile_name" to JsonPrimitive("China Mobile"),
            "apn_wan_apn" to JsonPrimitive("CMIOT5G"),
            "locked_cell_info" to JsonArray(emptyList()),
            "ResponseList" to buildJsonArray {
                addJsonObject {
                    put("ChipIndex", "0")
                    put("AccessPointIndex", "0")
                    put("SSID", "Test-AP")
                    put("AuthMode", "WPA2PSK")
                    put("Password", "cGFzc3dvcmQxMjM=")
                    put("ApBroadcastDisabled", "0")
                    put("ApIsolate", "0")
                    put("ApMaxStationNumber", 5)
                    put("AccessPointSwitchStatus", "1")
                }
            },
        ),
        now = 1L,
    )

    @Test
    fun `flattens access point response`() {
        val snap = snapshot()
        val ap = snap.accessPoint()!!
        assertEquals("Test-AP", ap.ssid)
        assertEquals("password123", ap.password)
        assertEquals(5, ap.maxStations)
        assertTrue(ap.switchOn)
        assertFalse(ap.hidden)
        assertTrue(snap.has("ap_0_ssid"))
        assertEquals("Test-AP", snap.text("ap_0_ssid"))
    }

    @Test
    fun `skips placeholder apn and falls back to auto config`() {
        val withPlaceholder = SettingsParser.build(
            mapOf(
                "APN_config0" to JsonPrimitive(apnLine("Default", "")),
                "apn_auto_config" to JsonPrimitive(apnLine("China Mobile", "CMIOT5G")),
                "profile_name" to JsonPrimitive("China Mobile"),
                "apn_wan_apn" to JsonPrimitive("CMIOT5G"),
            ),
        )
        assertEquals(1, withPlaceholder.apns.size)
        assertEquals("CMIOT5G", withPlaceholder.apns[0].apn)
        assertTrue(withPlaceholder.apns[0].isDefault)
    }

    @Test
    fun `matches real APN entry`() {
        val snap = SettingsParser.build(
            mapOf(
                "APN_config0" to JsonPrimitive(apnLine("China Mobile", "CMIOT5G")),
                "profile_name" to JsonPrimitive("China Mobile"),
                "apn_wan_apn" to JsonPrimitive("CMIOT5G"),
            ),
        )
        assertEquals(1, snap.apns.size)
        assertEquals("China Mobile", snap.apns[0].name)
        assertEquals("IPv4v6", snap.apns[0].pdpType)
        assertTrue(snap.apns[0].isDefault)
    }

    @Test
    fun `keeps empty values distinct from missing keys`() {
        val snap = SettingsParser.build(
            mapOf(
                "indicator_light_switch" to JsonPrimitive(""),
                "locked_cell_info" to JsonArray(emptyList()),
            ),
        )
        assertTrue(snap.has("indicator_light_switch"))
        assertEquals("", snap.raw("indicator_light_switch"))
        assertFalse(snap.has("does_not_exist"))
        assertEquals("[]", snap.raw("locked_cell_info"))
    }

    @Test
    fun `wifi params keep current password and encode new one`() {
        val snap = snapshot()
        val spec = SettingsCatalog.byId("wifi_ssid")!!
        val params = spec.buildParams(mapOf("value" to "NewName"), snap)
        assertEquals("NewName", params["SSID"])
        assertEquals(
            "password123",
            String(Base64.getDecoder().decode(params["Password"]!!.toByteArray()), Charsets.UTF_8),
        )
        assertEquals("0", params["ChipIndex"])
        assertEquals("WPA2PSK", params["AuthMode"])
    }

    @Test
    fun `traffic params preserve other fields`() {
        val snap = snapshot()
        val spec = SettingsCatalog.byId("data_limit_size")!!
        val params = spec.buildParams(mapOf("value" to "500_1024"), snap)
        assertEquals("500_1024", params["flux_data_volume_limit_size"])
        assertEquals("80", params["flux_data_volume_alert_percent"])
        assertEquals("1", params["flux_data_volume_limit_switch"])
    }

    @Test
    fun `sms codec and parser`() {
        assertEquals("4E2D", SmsCodec.encodeBody("中"))
        assertEquals("UNICODE", SmsCodec.encodeType("中文"))
        assertEquals("GSM7_default", SmsCodec.encodeType("hello"))

        val json = buildJsonObject {
            putJsonArray("messages") {
                addJsonObject {
                    put("id", "7")
                    put("number", "10086")
                    put("content", "hello")
                    put("date", "251106112000")
                    put("tag", "1")
                }
            }
        }
        val messages = SmsParser.parse(json)
        assertEquals(1, messages.size)
        assertEquals(7L, messages[0].id)
        assertEquals("10086", messages[0].number)
        assertEquals("hello", messages[0].body)
        assertEquals("2025-11-06 11:20:00", messages[0].time)
        assertTrue(messages[0].unread)
    }
}