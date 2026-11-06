package com.u30pro.panel.data.export

import android.content.Context
import com.u30pro.panel.BuildConfig
import com.u30pro.panel.data.model.StationSnapshot
import com.u30pro.panel.data.model.StatusSnapshot
import com.u30pro.panel.data.profile.ProbeReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes diagnostics where `adb pull` can reach them without the computer
 * joining the device WiFi:
 *
 *   /sdcard/Android/data/com.u30pro.panel/files/diagnostics/latest.json
 *
 * Uses the app-specific external directory, so no storage permission is needed
 * (on API 26+, including release builds). Falls back to internal storage when
 * external storage is unavailable; the returned path always reflects reality.
 */
class ProbeExporter(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun export(
        probe: ProbeReport?,
        status: StatusSnapshot?,
        stations: StationSnapshot?,
        now: Long = System.currentTimeMillis(),
    ): File {
        val payload = buildDiagnosticJson(
            appVersion = BuildConfig.VERSION_NAME,
            probe = probe,
            status = status,
            stations = stations,
            generatedAt = now,
        )
        val text = json.encodeToString(JsonObject.serializer(), payload)

        val directory = exportDirectory()
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("cannot create ${directory.absolutePath}")
        }

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val latest = File(directory, LATEST_FILE)
        val archived = File(directory, "u30pro-$stamp.json")

        latest.writeText(text, Charsets.UTF_8)
        archived.writeText(text, Charsets.UTF_8)
        prune(directory)
        return latest
    }

    private fun exportDirectory(): File {
        val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        return File(base, "diagnostics")
    }

    private fun prune(directory: File) {
        val archives = directory.listFiles { file ->
            file.isFile && file.name.endsWith(".json") && file.name != LATEST_FILE
        } ?: return
        archives.sortedByDescending { it.lastModified() }
            .drop(KEEP_ARCHIVES)
            .forEach { it.delete() }
    }

    private companion object {
        const val LATEST_FILE = "latest.json"
        const val KEEP_ARCHIVES = 10
    }
}