package com.u30pro.panel

import com.u30pro.panel.data.goform.GoformHash
import org.junit.Assert.assertEquals
import org.junit.Test

class GoformHashTest {

    @Test
    fun `sha256 is uppercase hex`() {
        assertEquals(
            "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD",
            GoformHash.sha256HexUpper("abc"),
        )
    }

    @Test
    fun `login hash matches double sha256 with LD salt`() {
        assertEquals(
            "A0832D697A3C516C4A90F1D6F60FFDD14BF5FEB2CF5ED006BD657A752FB8DF95",
            GoformHash.loginHash("admin123", "abc-123"),
        )
    }

    @Test
    fun `ad token derivation`() {
        assertEquals(
            "BE97E764AFCE1BE682897403AEEC40BD2DCBB6DABC056FE0A9A9890A74782429",
            GoformHash.adToken("U30ProV1.0.0B23", "CR1", "RD-42"),
        )
    }
}