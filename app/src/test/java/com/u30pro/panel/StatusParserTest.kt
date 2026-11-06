package com.u30pro.panel

import com.u30pro.panel.data.parse.StatusParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusParserTest {

    @Test
    fun `parses 5g status fields`() {
        val raw = mapOf(
            "network_type" to "ENDC",
            "ppp_status" to "ipv4_ipv6_connected",
            "network_provider_fullname" to "China Mobile",
            "Z5g_rsrp" to "-87",
            "Z5g_SINR" to "18.5",
            "Z5g_rsrq" to "-11",
            "nr5g_action_band" to "n78",
            "network_Z5g_PCI" to "321",
            "network_Z5g_CELL_ID" to "123456",
            "flux_realtime_rx_thrpt" to "1048576",
            "flux_monthly_rx_bytes" to "1073741824",
            "flux_monthly_tx_bytes" to "268435456",
            "wifi_access_sta_num" to "3",
            "battery_vol_percent" to "82",
            "battery_charging" to "1",
            "temperature" to "45000",
            "model_name" to "U30 Pro",
        )

        val status = StatusParser.parse(raw, "temperature")

        assertTrue(status.connected)
        assertEquals("5G NSA", status.networkLabel)
        assertEquals("China Mobile", status.provider)
        assertEquals(-87, status.rsrp)
        assertEquals(18.5, status.sinr!!, 0.001)
        assertEquals(-11, status.rsrq)
        assertEquals("n78", status.band)
        assertEquals("321", status.pci)
        assertEquals(1_048_576L, status.downBps)
        assertEquals(1_073_741_824L, status.monthRxBytes)
        assertEquals(3, status.wifiClients)
        assertEquals(82, status.batteryPercent)
        assertEquals(true, status.charging)
        assertEquals(45.0, status.temperatureC!!, 0.001)
        assertEquals("U30 Pro", status.model)
    }

    @Test
    fun `falls back to lte field names`() {
        val status = StatusParser.parse(
            mapOf(
                "network_type" to "LTE",
                "network_lte_rsrp" to "-101",
                "lte_rsrq" to "-15",
                "Lte_snr" to "7.5",
                "realtime_rx_thrpt" to "2048",
            ),
        )

        assertEquals("4G LTE", status.networkLabel)
        assertEquals(-101, status.rsrp)
        assertEquals(-15, status.rsrq)
        assertEquals(7.5, status.sinr!!, 0.001)
        assertEquals(2048L, status.downBps)
    }

    @Test
    fun `empty payload degrades instead of inventing values`() {
        val status = StatusParser.parse(emptyMap())
        assertFalse(status.connected)
        assertEquals("未知", status.networkLabel)
        assertNull(status.rsrp)
        assertNull(status.downBps)
        assertNull(status.temperatureC)
        assertEquals(StatusParser.SignalQuality.UNKNOWN, StatusParser.quality(null))
    }

    @Test
    fun `disconnected ppp wins over network type`() {
        val status = StatusParser.parse(
            mapOf(
                "network_type" to "LTE",
                "ppp_status" to "ppp_disconnected",
            ),
        )
        assertFalse(status.connected)
    }

    @Test
    fun `parses U30 Pro stock field names`() {
        val status = StatusParser.parse(
            mapOf(
                "network_type" to "5G",
                "Z5g_rsrp" to "-71",
                "Z5g_snr" to "22.4",
                "Z5g_rsrq" to "-10",
                "network_ZCELLINFO_band" to "n41",
                "network_Z_PCI" to "123",
                "network_cell_id" to "456789",
            ),
        )

        assertEquals("5G", status.networkLabel)
        assertEquals(-71, status.rsrp)
        assertEquals(22.4, status.sinr!!, 0.001)
        assertEquals(-10, status.rsrq)
        assertEquals("n41", status.band)
        assertEquals("123", status.pci)
        assertEquals("456789", status.cellId)
    }

    @Test
    fun `network_rssi bar level is not treated as rsrp`() {
        val status = StatusParser.parse(mapOf("network_rssi" to "3"))
        assertNull(status.rsrp)
    }

    @Test
    fun `parses NR metrics from network_information json`() {
        val info = """{"nr_rsrp":"-72","Nr_snr":"14","nr_rsrq":"-6","Nr_bands":"41","Nr_pci":"910","Nr_cell_id":"13166960702"}"""
        val status = StatusParser.parse(
            mapOf("network_type" to "5G", "network_information" to info),
        )

        assertEquals(-72, status.rsrp)
        assertEquals(14.0, status.sinr!!, 0.001)
        assertEquals(-6, status.rsrq)
        assertEquals("n41", status.band)
        assertEquals("910", status.pci)
        assertEquals("13166960702", status.cellId)
    }

    @Test
    fun `signal quality buckets`() {
        assertEquals(StatusParser.SignalQuality.EXCELLENT, StatusParser.quality(-75))
        assertEquals(StatusParser.SignalQuality.GOOD, StatusParser.quality(-85))
        assertEquals(StatusParser.SignalQuality.FAIR, StatusParser.quality(-95))
        assertEquals(StatusParser.SignalQuality.POOR, StatusParser.quality(-110))
    }
}