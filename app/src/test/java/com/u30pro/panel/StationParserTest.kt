package com.u30pro.panel

import com.u30pro.panel.data.model.Station
import com.u30pro.panel.data.parse.StationParser
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationParserTest {

    @Test
    fun `parses station_list array and acl blacklist`() {
        val wifi = buildJsonArray {
            addJsonObject {
                put("hostname", "iPhone")
                put("mac_addr", "aa:bb:cc:dd:ee:01")
                put("ip_addr", "192.168.0.2")
            }
            addJsonObject {
                put("hostname", "Laptop")
                put("mac_addr", "AA:BB:CC:DD:EE:02")
                put("ip_addr", "192.168.0.3")
            }
        }
        val acl = buildJsonObject {
            put("AclMode", "2")
            put("BlackMacList", "AA:BB:CC:DD:EE:02;")
            put("WhiteMacList", "")
        }

        val snapshot = StationParser.parse(wifi, null, acl)

        assertEquals(2, snapshot.stations.size)
        assertTrue(snapshot.aclSupported)
        assertEquals(2, snapshot.aclMode)
        assertEquals(setOf("AA:BB:CC:DD:EE:02"), snapshot.blacklist)
        val blocked = snapshot.stations.first { it.mac == "AA:BB:CC:DD:EE:02" }
        assertTrue(blocked.blocked)
        assertEquals("Laptop", blocked.hostname)
        assertEquals(Station.Kind.WIFI, blocked.kind)
    }

    @Test
    fun `accepts json encoded string shape`() {
        val encoded = JsonPrimitive(
            """[{"hostname":"Pixel","mac":"11:22:33:44:55:66","ip":"192.168.0.9"}]""",
        )

        val snapshot = StationParser.parse(encoded, null, null)

        assertEquals(1, snapshot.stations.size)
        assertEquals("Pixel", snapshot.stations.first().hostname)
        assertEquals("11:22:33:44:55:66", snapshot.stations.first().mac)
        assertEquals(false, snapshot.aclSupported)
    }

    @Test
    fun `merges lan list and dedupes by mac`() {
        val wifi = buildJsonArray {
            addJsonObject {
                put("hostname", "TV")
                put("mac_addr", "aa:bb:cc:00:00:01")
            }
        }
        val lan = buildJsonArray {
            addJsonObject {
                put("hostname", "TV")
                put("mac_addr", "aa:bb:cc:00:00:01")
                put("ip_addr", "192.168.0.20")
            }
            addJsonObject {
                put("hostname", "NAS")
                put("mac_addr", "aa:bb:cc:00:00:02")
                put("ip_addr", "192.168.0.21")
            }
        }

        val snapshot = StationParser.parse(wifi, lan, null)

        assertEquals(2, snapshot.stations.size)
        val tv = snapshot.stations.first { it.mac == "AA:BB:CC:00:00:01" }
        assertEquals("192.168.0.20", tv.ip)
        assertEquals(1, snapshot.wifiCount)
        assertEquals(1, snapshot.lanCount)
    }

    @Test
    fun `empty or malformed payloads produce empty snapshot`() {
        val snapshot = StationParser.parse(null, JsonPrimitive("not-json"), JsonPrimitive(""))
        assertTrue(snapshot.stations.isEmpty())
        assertEquals(false, snapshot.aclSupported)
    }
}