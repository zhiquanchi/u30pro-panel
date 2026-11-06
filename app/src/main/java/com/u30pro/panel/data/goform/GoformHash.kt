package com.u30pro.panel.data.goform

import java.security.MessageDigest

/**
 * ZTE goform password/AD derivation.
 *
 * U30 Air family (verified in u30mon / AirDeck / UFI-AXIS):
 *   H1 = UPPER(hex(SHA256(password)))
 *   H  = UPPER(hex(SHA256(H1 + LD)))
 *
 * Legacy MiFi firmware instead sends Base64(password) — handled by the caller.
 */
object GoformHash {

    fun sha256HexUpper(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02X".format(byte) }

    fun loginHash(password: String, salt: String): String =
        sha256HexUpper(sha256HexUpper(password) + salt)

    /**
     * AD = UPPER(hex(SHA256( UPPER(hex(SHA256(wa_inner_version + cr_version))) + RD )))
     * Required for every write except LOGIN.
     */
    fun adToken(waInnerVersion: String, crVersion: String, rd: String): String =
        sha256HexUpper(sha256HexUpper(waInnerVersion + crVersion) + rd)
}