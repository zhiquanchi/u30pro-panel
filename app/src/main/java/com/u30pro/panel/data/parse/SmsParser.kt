package com.u30pro.panel.data.parse

import com.u30pro.panel.data.settings.SmsMessage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object SmsCodec {

    /** Stock UI sends UTF-16 code units as 4-digit uppercase hex. */
    fun encodeBody(text: String): String =
        text.toCharArray().joinToString(separator = "") { char -> "%04X".format(char.code) }

    /** Rough GSM7 detection; anything with non-ASCII (e.g. Chinese) is UNICODE. */
    fun encodeType(text: String): String =
        if (text.all { it.code in 0x20..0x7E }) "GSM7_default" else "UNICODE"
}

object SmsParser {

    fun parse(json: JsonObject): List<SmsMessage> {
        val messages = json["messages"] as? JsonArray ?: return emptyList()
        return messages.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.str("id")?.toLongOrNull() ?: return@mapNotNull null
            SmsMessage(
                id = id,
                number = obj.str("number").orEmpty(),
                body = obj.str("content").orEmpty(),
                time = formatTime(obj.str("date").orEmpty()),
                unread = obj.str("tag") == "1",
            )
        }
    }

    fun unread(json: JsonObject): Int? = json.str("sms_unread_num")?.toIntOrNull()

    private fun formatTime(raw: String): String {
        if (raw.length < 12) return raw
        return "20${raw.substring(0, 2)}-${raw.substring(2, 4)}-${raw.substring(4, 6)} " +
            "${raw.substring(6, 8)}:${raw.substring(8, 10)}:${raw.substring(10, 12)}"
    }

    private fun JsonObject.str(key: String): String? {
        val value = get(key) ?: return null
        return (value as? JsonPrimitive)?.contentOrNull
    }
}