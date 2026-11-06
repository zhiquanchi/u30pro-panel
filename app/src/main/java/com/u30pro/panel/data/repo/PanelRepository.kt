package com.u30pro.panel.data.repo

import android.util.Base64
import com.u30pro.panel.data.goform.GoformException
import com.u30pro.panel.data.goform.GoformHash
import com.u30pro.panel.data.goform.GoformTransport
import com.u30pro.panel.data.goform.long
import com.u30pro.panel.data.goform.semicolonList
import com.u30pro.panel.data.goform.userMessage
import com.u30pro.panel.data.goform.string
import com.u30pro.panel.data.model.StationSnapshot
import com.u30pro.panel.data.model.StatusSnapshot
import com.u30pro.panel.data.parse.SettingsParser
import com.u30pro.panel.data.parse.SmsCodec
import com.u30pro.panel.data.parse.SmsParser
import com.u30pro.panel.data.parse.StationParser
import com.u30pro.panel.data.parse.StatusParser
import com.u30pro.panel.data.profile.DeviceProfile
import com.u30pro.panel.data.profile.GoformFields
import com.u30pro.panel.data.profile.GroupReport
import com.u30pro.panel.data.profile.LoginScheme
import com.u30pro.panel.data.profile.ProbeReport
import com.u30pro.panel.data.settings.ApnProfile
import com.u30pro.panel.data.settings.SettingsCatalog
import com.u30pro.panel.data.settings.SettingsSnapshot
import com.u30pro.panel.data.settings.SmsMessage
import com.u30pro.panel.data.settings.WriteOutcome
import com.u30pro.panel.data.net.WifiNetworkBinder
import com.u30pro.panel.data.store.CredentialsStore
import com.u30pro.panel.data.store.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Facade over the stock admin API.
 *
 * Concurrency: the device allows ONE admin session, so every call is
 * serialized through [mutex]. Polling must be driven by the UI lifecycle.
 */
class PanelRepository(
    private val transport: GoformTransport,
    private val settings: SettingsStore,
    private val credentials: CredentialsStore,
    private val binder: WifiNetworkBinder,
) {

    private val mutex = Mutex()

    @Volatile
    private var password: String? = null

    @Volatile
    private var profile: DeviceProfile? = null

    @Volatile
    private var lastOkAt = 0L

    @Volatile
    private var yieldedUntil = 0L

    @Volatile
    private var lastProbe: ProbeReport? = null

    /**
     * U30 Pro B24 serves status/station_list/ACL reads without a login session.
     * Set to true only when a firmware rejects unauthenticated reads; then the
     * old "hold a session" behaviour is used.
     */
    @Volatile
    private var readsRequireLogin = false

    /** True only while we actually hold a login session (writes / legacy reads). */
    @Volatile
    private var sessionActive = false

    fun currentProfile(): DeviceProfile? = profile

    fun probeReport(): ProbeReport? = lastProbe

    fun hasSavedPassword(): Boolean = credentials.hasSaved()

    fun isOnWifi(): Boolean = binder.isOnWifi()

    suspend fun savedProfile(): DeviceProfile? = profile ?: settings.loadProfile()

    val pollIntervalMs: kotlinx.coroutines.flow.Flow<Long> get() = settings.pollIntervalMs

    suspend fun setPollInterval(ms: Long) = settings.savePollInterval(ms)

    suspend fun clearSaved() {
        credentials.clear()
        settings.clear()
        password = null
        profile = null
        lastOkAt = 0
        yieldedUntil = 0
        lastProbe = null
    }

    suspend fun autoConnect(): ProbeReport? {
        val saved = settings.loadProfile() ?: return null
        val savedPassword = credentials.load() ?: return null
        return mutex.withLock {
            profile = saved
            password = savedPassword
            transport.setOrigin(saved.gateway, saved.port)

            // Try the open read path first: it avoids occupying the device's
            // single admin session while the stock web UI is in use.
            val versions = try {
                readFields(listOf("wa_inner_version", "cr_version"))
            } catch (e: GoformException) {
                if (e.kind != GoformException.Kind.NOT_LOGGED_IN) throw e
                readsRequireLogin = true
                ensureLoggedIn(force = true)
                readFields(listOf("wa_inner_version", "cr_version"))
            }
            val firmware = firmwareKey(versions)
            val report = if (firmware != null && saved.firmware != null && firmware != saved.firmware) {
                // Firmware changed under us: field support may have changed too.
                runProbe(saved.gateway, saved.port, saved.loginScheme, note = "固件已变化，重新探测")
            } else {
                val refreshed = saved.copy(
                    firmware = firmware ?: saved.firmware,
                    availableFields = saved.availableFields,
                )
                profile = refreshed
                ProbeReport(
                    profile = refreshed,
                    groups = emptyMap(),
                    notes = listOf("使用已保存的设备档案"),
                ).also { lastProbe = it }
            }
            report
        }
    }

    /**
     * First contact: find the goform port, detect the login scheme, probe every
     * candidate field, persist the resulting capability profile.
     */
    suspend fun connect(gatewayInput: String, passwordInput: String, remember: Boolean): ProbeReport =
        mutex.withLock {
            val gateway = normalizeGateway(gatewayInput)
            if (passwordInput.isBlank()) {
                throw GoformException(GoformException.Kind.WRONG_PASSWORD, "empty password")
            }

            val port = findReachablePort(gateway)
            transport.setOrigin(gateway, port)
            password = passwordInput
            readsRequireLogin = false

            val scheme = login(passwordInput, preferred = null)
            val report = try {
                runProbe(gateway, port, scheme, note = null)
            } finally {
                // Release the admin session as soon as the one-time password
                // validation is done; monitoring uses open reads.
                logoutLocked()
            }

            profile = report.profile
            settings.saveProfile(report.profile)
            if (remember) credentials.save(passwordInput) else credentials.clear()
            lastProbe = report
            report
        }

    suspend fun reprobe(): ProbeReport = mutex.withLock {
        val current = profile
            ?: settings.loadProfile()
            ?: throw GoformException(GoformException.Kind.NOT_LOGGED_IN, "not connected")
        val pw = password
            ?: credentials.load()
            ?: throw GoformException(GoformException.Kind.NOT_LOGGED_IN, "no password")
        password = pw
        transport.setOrigin(current.gateway, current.port)
        val report = try {
            runProbe(current.gateway, current.port, current.loginScheme, note = null)
        } catch (e: GoformException) {
            if (e.kind != GoformException.Kind.NOT_LOGGED_IN) throw e
            readsRequireLogin = true
            ensureLoggedIn(force = true)
            runProbe(current.gateway, current.port, current.loginScheme, note = null)
        }
        profile = report.profile
        settings.saveProfile(report.profile)
        lastProbe = report
        report
    }

    suspend fun readStatus(): StatusSnapshot = withReads {
        val current = profile
        val fields = if (current != null && current.availableFields.isNotEmpty()) {
            (current.availableFields.filter { it in GoformFields.STATUS_READ } + ESSENTIAL_STATUS_FIELDS)
                .distinct()
        } else {
            GoformFields.STATUS_READ
        }
        val values = readFields(fields).toMutableMap()
        // cmd=network_information answers with flattened Nr_* keys, not a
        // nested object. Read it separately and hand the JSON text to the parser.
        readNetworkInformation()?.let { info ->
            values["network_information"] = info.toString()
        }
        StatusParser.parse(values, current?.temperatureField)
    }

    private suspend fun readNetworkInformation(): JsonObject? = try {
        transport.get(cmdParams("network_information"))
    } catch (e: GoformException) {
        when (e.kind) {
            GoformException.Kind.NOT_REACHABLE,
            GoformException.Kind.NOT_LOGGED_IN,
            GoformException.Kind.SESSION_BUSY,
            GoformException.Kind.SECURE_HEADER,
            -> throw e
            else -> null
        }
    }

    suspend fun readStations(): StationSnapshot = withReads {
        val raw = readRawWithFallback(GoformFields.STATION)
        StationParser.parse(
            stationList = raw["station_list"],
            lanStationList = raw["lan_station_list"],
            aclElement = readAcl(),
        )
    }

    // -----------------------------------------------------------------------
    // device settings (management screen)
    // -----------------------------------------------------------------------

    suspend fun readSettingsSnapshot(): SettingsSnapshot = withReads {
        SettingsParser.build(readSettingsValues())
    }

    private suspend fun readSettingsValues(): Map<String, JsonElement> {
        val values = linkedMapOf<String, JsonElement>()
        val scalars = buildList {
            addAll(SETTINGS_SCALARS)
            for (index in 0 until 20) add("APN_config$index")
        }
        scalars.chunked(20).forEach { batch ->
            val response = transport.get(cmdParams(batch.joinToString(",")))
            batch.forEach { key -> response[key]?.let { values[key] = it } }
        }
        for (cmd in SETTINGS_QUERIES) {
            val response = try {
                transport.get(cmdParams(cmd))
            } catch (e: GoformException) {
                when (e.kind) {
                    GoformException.Kind.NOT_REACHABLE,
                    GoformException.Kind.NOT_LOGGED_IN,
                    GoformException.Kind.SESSION_BUSY,
                    GoformException.Kind.SECURE_HEADER,
                    -> throw e
                    else -> continue
                }
            }
            response.forEach { (key, element) -> values[key] = element }
        }
        return values
    }

    suspend fun writeSetting(specId: String, input: Map<String, String>): WriteOutcome {
        val spec = SettingsCatalog.byId(specId)
            ?: return WriteOutcome(false, null, "未知设置")
        val before = readSettingsSnapshot()
        return when (spec.actionId) {
            "REBOOT" -> runAction("REBOOT_DEVICE", emptyMap(), "设备正在重启")
            "OTA_CHECK" -> runAction(
                "IF_UPGRADE",
                linkedMapOf("select_op" to "check", "ota_manual_check_roam_state" to "1"),
                "已发起更新检查",
            )
            "RESET_TRAFFIC" -> runAction("RESET_DATA_COUNTER", emptyMap(), "流量统计已清零")
            "UNLOCK_CELLS" -> runAction("UNLOCK_ALL_CELL", emptyMap(), "已解除小区/频段锁定")
            "OPEN_APN", "OPEN_SMS" -> WriteOutcome(true, null, null)
            else -> {
                val goformId = spec.goformIdFor(input)
                    ?: return WriteOutcome(false, null, "该设置不可写入")
                val params = spec.buildParams(input, before)
                if (params.isEmpty()) {
                    return WriteOutcome(false, null, "缺少当前设备参数，无法安全写入")
                }
                try {
                    withWrite { write(goformId, params) }
                } catch (e: GoformException) {
                    return WriteOutcome(false, null, e.userMessage())
                }

                val after = readSettingsSnapshot()
                val expected = spec.expected(input, before)
                val mismatched = expected.filter { (field, exp) ->
                    exp != null && spec.verifyTransform(field, after.values[field]) != exp
                }.keys
                if (mismatched.isEmpty()) {
                    WriteOutcome(true, true, null)
                } else {
                    WriteOutcome(
                        success = true,
                        verified = false,
                        message = "已写入，但回读不一致：${mismatched.joinToString()}",
                    )
                }
            }
        }
    }

    private suspend fun runAction(
        goformId: String,
        params: Map<String, String>,
        message: String,
    ): WriteOutcome = try {
        withWrite { write(goformId, params) }
        WriteOutcome(true, null, message)
    } catch (e: GoformException) {
        WriteOutcome(false, null, e.userMessage())
    }

    suspend fun readApns(): List<ApnProfile> = readSettingsSnapshot().apns

    suspend fun saveApn(profile: ApnProfile): WriteOutcome =
        apnWrite("save", profile, profile.index)

    suspend fun deleteApn(index: Int): WriteOutcome = apnWrite("delete", null, index)

    suspend fun setDefaultApn(profile: ApnProfile): WriteOutcome =
        apnWrite("set_default", profile, profile.index)

    private suspend fun apnWrite(
        action: String,
        profile: ApnProfile?,
        index: Int,
    ): WriteOutcome {
        val params = linkedMapOf(
            "apn_action" to action,
            "apn_mode" to "manual",
            "index" to index.toString(),
        )
        if (profile != null) {
            params["profile_name"] = profile.name
            params["apn_wan_apn"] = profile.apn
            params["apn_pdp_type"] = profile.pdpType.ifBlank { "IPv4v6" }
            params["apn_ppp_auth_mode"] = profile.auth.ifBlank { "none" }
            params["apn_ppp_username"] = profile.username
            params["apn_ppp_passwd"] = profile.password
            params["apn_wan_dial"] = profile.dial.ifBlank { "*99#" }
            params["apn_select"] = "manual"
            params["dns_mode"] = "none"
        }
        if (action == "set_default") params["set_default_flag"] = "1"

        return try {
            withWrite { write("APN_PROC_EX", params) }
            WriteOutcome(
                true,
                null,
                when (action) {
                    "delete" -> "APN 已删除"
                    "set_default" -> "已设为默认 APN"
                    else -> "APN 已保存"
                },
            )
        } catch (e: GoformException) {
            WriteOutcome(false, null, e.userMessage())
        }
    }

    suspend fun readSms(page: Int = 0, count: Int = 50): List<SmsMessage> = withReads {
        val response = transport.get(
            mapOf(
                "cmd" to "sms_status_rpt_data",
                "page" to page.toString(),
                "data_per_page" to count.toString(),
                "isTest" to "false",
                "_" to now().toString(),
            ),
        )
        SmsParser.parse(response)
    }

    suspend fun sendSms(number: String, body: String): WriteOutcome {
        if (number.isBlank() || body.isBlank()) {
            return WriteOutcome(false, null, "号码和内容不能为空")
        }
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        return try {
            withWrite {
                write(
                    "SEND_SMS",
                    linkedMapOf(
                        "Number" to number.trim(),
                        "sms_time" to time,
                        "MessageBody" to SmsCodec.encodeBody(body),
                        "ID" to "0",
                        "encode_type" to SmsCodec.encodeType(body),
                        "notCallback" to "true",
                    ),
                )
            }
            WriteOutcome(true, null, "短信已发送")
        } catch (e: GoformException) {
            WriteOutcome(false, null, e.userMessage())
        }
    }

    suspend fun deleteSms(ids: List<Long>): WriteOutcome {
        if (ids.isEmpty()) return WriteOutcome(false, null, "没有选中短信")
        val joined = ids.joinToString(";") + ";"
        return try {
            withWrite {
                write(
                    "DELETE_SMS",
                    linkedMapOf("msg_id" to joined, "notCallback" to "true"),
                )
            }
            WriteOutcome(true, null, "短信已删除")
        } catch (e: GoformException) {
            WriteOutcome(false, null, e.userMessage())
        }
    }

    suspend fun markSmsRead(ids: List<Long>): WriteOutcome {
        if (ids.isEmpty()) return WriteOutcome(false, null, "没有选中短信")
        val joined = ids.joinToString(";") + ";"
        return try {
            withWrite {
                write("SET_MSG_READ", linkedMapOf("msg_id" to joined, "tag" to "0"))
            }
            WriteOutcome(true, null, "已标记为已读")
        } catch (e: GoformException) {
            WriteOutcome(false, null, e.userMessage())
        }
    }

    suspend fun setBlocked(mac: String, blocked: Boolean) = withWrite {
        val acl = readAcl()
            ?: throw GoformException(
                GoformException.Kind.UNSUPPORTED,
                "ACL list unavailable on this firmware",
            )

        val currentBlack = (acl.string("BlackMacList") ?: "").semicolonList()
        val updated = if (blocked) {
            (currentBlack + mac.uppercase()).distinct()
        } else {
            currentBlack.filterNot { it.equals(mac, ignoreCase = true) }
        }

        write(
            goformId = "setDeviceAccessControlList",
            params = linkedMapOf(
                "AclMode" to "2",
                "WhiteMacList" to (acl.string("WhiteMacList") ?: ""),
                "BlackMacList" to updated.joinToString(";"),
                "WhiteNameList" to (acl.string("WhiteNameList") ?: ""),
                "BlackNameList" to (acl.string("BlackNameList") ?: ""),
            ),
        )
        Unit
    }

    suspend fun rename(mac: String, hostname: String) = withWrite {
        write(
            goformId = "EDIT_HOSTNAME",
            params = linkedMapOf("mac" to mac, "hostname" to hostname),
        )
        Unit
    }

    suspend fun logout() {
        runCatching {
            mutex.withLock { logoutLocked() }
        }
        lastOkAt = 0
    }

    private suspend fun logoutLocked() {
        lastOkAt = 0
        sessionActive = false
        runCatching {
            transport.post(
                linkedMapOf(
                    "isTest" to "false",
                    "goformId" to "LOGOUT",
                ),
            )
        }
    }

    // -----------------------------------------------------------------------
    // session
    // -----------------------------------------------------------------------

    /**
     * Reads first, login only if the firmware demands it. On U30 Pro B24 every
     * read (status, station_list, ACL) works anonymously, so monitoring never
     * occupies the device's single admin session.
     */
    private suspend fun <T> withReads(block: suspend () -> T): T = mutex.withLock {
        if (readsRequireLogin) {
            ensureLoggedIn()
            return@withLock block().also { lastOkAt = now() }
        }
        try {
            block().also { lastOkAt = now() }
        } catch (e: GoformException) {
            if (e.kind != GoformException.Kind.NOT_LOGGED_IN) throw e
            sessionActive = false
            readsRequireLogin = true
            ensureLoggedIn(force = true)
            block().also { lastOkAt = now() }
        }
    }

    /** Writes need LOGIN + RD for the AD token, then release the session. */
    private suspend fun <T> withWrite(block: suspend () -> T): T = mutex.withLock {
        ensureLoggedIn()
        try {
            block().also { lastOkAt = now() }
        } catch (e: GoformException) {
            if (e.kind == GoformException.Kind.NOT_LOGGED_IN) {
                ensureLoggedIn(force = true)
                block().also { lastOkAt = now() }
            } else {
                throw e
            }
        } finally {
            if (!readsRequireLogin) logoutLocked()
        }
    }

    private suspend fun ensureLoggedIn(force: Boolean = false) {
        val current = now()
        if (current < yieldedUntil) {
            throw GoformException(GoformException.Kind.SESSION_BUSY, "yielding to stock UI")
        }
        if (!force && sessionActive && lastOkAt > 0 && current - lastOkAt < SESSION_TTL_MS) return

        val pw = password ?: runCatching { credentials.load() }.getOrNull()
            ?: throw GoformException(GoformException.Kind.NOT_LOGGED_IN, "no password")

        if (!force && sessionActive && probeAlive()) {
            lastOkAt = now()
            return
        }

        sessionActive = false
        login(pw, preferred = profile?.loginScheme)
    }

    private suspend fun probeAlive(): Boolean = try {
        val json = transport.get(
            mapOf(
                "cmd" to "RD",
                "multi_data" to "1",
                "isTest" to "false",
                "_" to now().toString(),
            ),
        )
        json.string("RD") != null
    } catch (e: GoformException) {
        if (e.kind != GoformException.Kind.SESSION_BUSY) sessionActive = false
        when (e.kind) {
            GoformException.Kind.SESSION_BUSY -> throw e
            else -> false
        }
    }

    private suspend fun login(pw: String, preferred: LoginScheme?): LoginScheme {
        val schemes = when (preferred) {
            LoginScheme.LD_SHA256 -> listOf(LoginScheme.LD_SHA256, LoginScheme.BASE64)
            LoginScheme.BASE64 -> listOf(LoginScheme.BASE64, LoginScheme.LD_SHA256)
            null -> listOf(LoginScheme.LD_SHA256, LoginScheme.BASE64)
        }

        var lastKind: GoformException.Kind = GoformException.Kind.WRONG_PASSWORD
        var lastMessage = "login failed"

        for ((index, scheme) in schemes.withIndex()) {
            if (index > 0) delay(LOGIN_BACKOFF_MS)
            val result = try {
                tryLogin(pw, scheme)
            } catch (e: GoformException) {
                if (e.kind == GoformException.Kind.SESSION_BUSY) {
                    yieldedUntil = now() + YIELD_MS
                    throw e
                }
                lastKind = e.kind
                lastMessage = e.message ?: lastMessage
                continue
            }

            when (result) {
                0 -> {
                    lastOkAt = now()
                    sessionActive = true
                    return scheme
                }
                3 -> {
                    lastKind = GoformException.Kind.WRONG_PASSWORD
                    lastMessage = "password rejected"
                }
                5 -> {
                    yieldedUntil = now() + YIELD_MS
                    throw GoformException(GoformException.Kind.SESSION_BUSY, "device session busy")
                }
                else -> {
                    lastKind = GoformException.Kind.DEVICE
                    lastMessage = "login result=$result"
                }
            }
        }
        throw GoformException(lastKind, lastMessage)
    }

    private suspend fun tryLogin(pw: String, scheme: LoginScheme): Int {
        val hashed = when (scheme) {
            LoginScheme.LD_SHA256 -> {
                val salt = readLd()
                    ?: throw GoformException(
                        GoformException.Kind.UNSUPPORTED,
                        "LD salt unavailable",
                    )
                GoformHash.loginHash(pw, salt)
            }
            LoginScheme.BASE64 -> Base64.encodeToString(pw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }
        val response = transport.post(
            linkedMapOf(
                "isTest" to "false",
                "goformId" to "LOGIN",
                "user" to "admin",
                "password" to hashed,
            ),
        )
        val raw = response["result"]
        val text = (raw as? JsonPrimitive)?.contentOrNull
        if (text != null && text.contains("session", ignoreCase = true)) return 5
        return text?.toIntOrNull() ?: -1
    }

    private suspend fun readLd(): String? = runCatching {
        transport.get(
            mapOf(
                "cmd" to "LD",
                "multi_data" to "1",
                "isTest" to "false",
                "_" to now().toString(),
            ),
        ).string("LD")
    }.getOrNull()

    // -----------------------------------------------------------------------
    // probing
    // -----------------------------------------------------------------------

    private suspend fun findReachablePort(gateway: String): Int {
        for (port in listOf(80, 8080)) {
            transport.setOrigin(gateway, port)
            if (readLd() != null) return port
        }
        // Nothing answered cmd=LD; fall back to 80 so the surfaced error is
        // "cannot reach device" rather than a misleading port problem.
        return 80
    }

    private suspend fun runProbe(
        gateway: String,
        port: Int,
        scheme: LoginScheme,
        note: String?,
    ): ProbeReport {
        val available = linkedMapOf<String, String>()
        val groups = linkedMapOf<String, GroupReport>()

        for ((name, fields) in GoformFields.PROBE_GROUPS) {
            val values = probeGroup(fields)
            available.putAll(values)
            groups[name] = GroupReport(
                requested = fields.size,
                available = values.size,
                missing = fields.filterNot { it in values },
            )
        }

        // station_list can come back empty when combined with other commands.
        val stationRaw = readRawWithFallback(GoformFields.STATION)
        val stationKeys = stationRaw
            .filterKeys { it == "station_list" || it == "lan_station_list" }
            .values
            .flatMap { element ->
                com.u30pro.panel.data.goform.parseObjectList(element)
                    .flatMap { it.keys }
            }
            .distinct()
            .sorted()

        // U30 Pro returns the ACL as a flat object {"AclMode":...}, not nested
        // under the command name.
        val aclPresent = readAcl() != null
        val temperatureField = GoformFields.TEMPERATURE.firstOrNull { it in available }

        val versions = readFields(listOf("wa_inner_version", "cr_version"))
        val firmware = firmwareKey(versions)

        val newProfile = DeviceProfile(
            gateway = gateway,
            port = port,
            loginScheme = scheme,
            model = available["model_name"],
            firmware = firmware,
            availableFields = available.keys.sorted(),
            stationKeys = stationKeys,
            aclSupported = aclPresent,
            // U30 Pro stock UI exposes both queryDeviceAccessControlList and
            // EDIT_HOSTNAME; ACL presence is the best non-destructive proxy.
            renameSupported = aclPresent,
            temperatureField = temperatureField,
            probedAt = now(),
        )

        val notes = buildList {
            note?.let { add(it) }
            if (temperatureField == null) {
                add("未发现可用的温度字段：U30 系列的温度通常不经过 goform，原厂 API 可能本来就不提供。")
            }
            if (!aclPresent) {
                add("未读到 ACL 列表：拉黑/解黑功能将不可用。")
            }
        }

        return ProbeReport(profile = newProfile, groups = groups, notes = notes)
    }

    /**
     * Batch read; if a batch returns nothing, bisect it so one unsupported
     * field does not hide the rest of the group.
     */
    private suspend fun probeGroup(fields: List<String>): Map<String, String> {
        val values = mutableMapOf<String, String>()

        suspend fun attempt(batch: List<String>) {
            if (batch.isEmpty()) return
            val response = try {
                transport.get(
                    mapOf(
                        "cmd" to batch.joinToString(","),
                        "multi_data" to "1",
                        "isTest" to "false",
                        "_" to now().toString(),
                    ),
                )
            } catch (e: GoformException) {
                when (e.kind) {
                    GoformException.Kind.NOT_REACHABLE,
                    GoformException.Kind.NOT_LOGGED_IN,
                    GoformException.Kind.SESSION_BUSY,
                    GoformException.Kind.SECURE_HEADER,
                    -> throw e
                    else -> {
                        if (batch.size == 1) return
                        val mid = (batch.size + 1) / 2
                        attempt(batch.subList(0, mid))
                        attempt(batch.subList(mid, batch.size))
                        return
                    }
                }
            }

            val hit = batch.mapNotNull { key -> response.string(key)?.let { key to it } }.toMap()
            values.putAll(hit)
            if (hit.isEmpty() && batch.size > 1) {
                val mid = (batch.size + 1) / 2
                attempt(batch.subList(0, mid))
                attempt(batch.subList(mid, batch.size))
            } else if (hit.isNotEmpty()) {
                // U30 Pro B24 populates some metrics only when they are the
                // sole cmd. Probe missing keys one by one instead of assuming
                // the whole batch revealed support.
                for (key in batch.filterNot { it in hit }) {
                    try {
                        val single = transport.get(
                            mapOf(
                                "cmd" to key,
                                "multi_data" to "1",
                                "isTest" to "false",
                                "_" to now().toString(),
                            ),
                        )
                        single.string(key)?.let { values[key] = it }
                    } catch (e: GoformException) {
                        when (e.kind) {
                            GoformException.Kind.NOT_REACHABLE,
                            GoformException.Kind.NOT_LOGGED_IN,
                            GoformException.Kind.SESSION_BUSY,
                            GoformException.Kind.SECURE_HEADER,
                            -> throw e
                            else -> Unit
                        }
                    }
                }
            }
        }

        fields.chunked(PROBE_BATCH).forEach { attempt(it) }
        return values
    }

    // -----------------------------------------------------------------------
    // reads / writes
    // -----------------------------------------------------------------------

    private suspend fun readFields(fields: List<String>): Map<String, String> {
        if (fields.isEmpty()) return emptyMap()
        val values = mutableMapOf<String, String>()
        fields.chunked(READ_BATCH).forEach { batch ->
            val response = transport.get(
                mapOf(
                    "cmd" to batch.joinToString(","),
                    "multi_data" to "1",
                    "isTest" to "false",
                    "_" to now().toString(),
                ),
            )
            batch.forEach { key -> response.string(key)?.let { values[key] = it } }
        }
        return values
    }

    private suspend fun readRawWithFallback(fields: List<String>): Map<String, JsonElement> {
        val values = mutableMapOf<String, JsonElement>()

        suspend fun read(batch: List<String>) {
            if (batch.isEmpty()) return
            val response = transport.get(
                mapOf(
                    "cmd" to batch.joinToString(","),
                    "multi_data" to "1",
                    "isTest" to "false",
                    "_" to now().toString(),
                ),
            )
            batch.forEach { key -> response[key]?.let { values[key] = it } }
        }

        read(fields)
        // Retry the important ones alone, since some firmware drops the whole
        // response when station_list is mixed with other commands.
        for (key in listOf("station_list", "lan_station_list", "hostNameList")) {
            if (values[key] == null) read(listOf(key))
        }
        return values
    }

    /**
     * The ACL query answers with a flat object:
     *   {"AclMode":"2","BlackMacList":"...","WhiteMacList":"..."}
     * so it must not be looked up under its own command name.
     */
    private suspend fun readAcl(): JsonObject? {
        val response = transport.get(
            mapOf(
                "cmd" to "queryDeviceAccessControlList",
                "multi_data" to "1",
                "isTest" to "false",
                "_" to now().toString(),
            ),
        )
        val looksLikeAcl = response.keys.any {
            it == "AclMode" || it == "aclMode" ||
                it == "BlackMacList" || it == "WhiteMacList" ||
                it == "BlackNameList" || it == "WhiteNameList"
        }
        return if (looksLikeAcl) response else null
    }

    private suspend fun write(goformId: String, params: Map<String, String>): JsonObject {
        val ad = writeToken()
        val form = linkedMapOf(
            "isTest" to "false",
            "goformId" to goformId,
        )
        form.putAll(params)
        form["AD"] = ad

        val response = transport.post(form)
        val result = response.string("result")
        if (result != null && result != "0" && !result.equals("success", ignoreCase = true)) {
            throw GoformException(GoformException.Kind.DEVICE, "写入失败：$result")
        }
        return response
    }

    private suspend fun writeToken(): String {
        val versions = readFields(listOf("wa_inner_version", "cr_version"))
        val wa = versions["wa_inner_version"]
        val cr = versions["cr_version"]
        if (wa == null || cr == null) {
            throw GoformException(GoformException.Kind.UNSUPPORTED, "missing version fields")
        }
        val rd = transport.get(
            mapOf(
                "cmd" to "RD",
                "multi_data" to "1",
                "isTest" to "false",
                "_" to now().toString(),
            ),
        ).string("RD")
            ?: throw GoformException(GoformException.Kind.NOT_LOGGED_IN, "RD missing")
        return GoformHash.adToken(wa, cr, rd)
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private fun firmwareKey(values: Map<String, String>): String? {
        val wa = values["wa_inner_version"] ?: return null
        val cr = values["cr_version"] ?: return null
        return "$wa/$cr"
    }

    private fun normalizeGateway(input: String): String {
        val host = input.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore('/')
        if (host.isBlank()) {
            throw GoformException(GoformException.Kind.NOT_REACHABLE, "empty gateway")
        }
        return host
    }

    private fun now(): Long = System.currentTimeMillis()

    private fun cmdParams(cmd: String): Map<String, String> = mapOf(
        "cmd" to cmd,
        "multi_data" to "1",
        "isTest" to "false",
        "_" to now().toString(),
    )

    private companion object {
        const val SESSION_TTL_MS = 60_000L
        const val YIELD_MS = 30_000L
        const val LOGIN_BACKOFF_MS = 1_500L
        const val READ_BATCH = 20
        const val PROBE_BATCH = 16

        /** Always requested even if an older profile predates these fields. */
        val ESSENTIAL_STATUS_FIELDS = listOf("network_information")

        val SETTINGS_SCALARS = listOf(
            "wifi_onoff_state",
            "ConnectionMode",
            "network_net_select_mode",
            "current_network",
            "roam_setting_option",
            "lte_band_lock",
            "nr_band_lock",
            "indicator_light_switch",
            "led_night_mode_switch",
            "led_night_mode_start_time",
            "led_night_mode_end_time",
            "night_mode_close_all_led",
            "usb_port_switch",
            "usb_network_protocal",
            "samba_switch",
            "samba_username",
            "samba_password",
            "auto_power_save",
            "performance_mode",
            "battery_protection",
            "power_supply_mode",
            "web_wake_switch",
            "web_sleep_switch",
            "web_wake_time",
            "web_sleep_time",
            "flux_data_volume_limit_switch",
            "flux_data_volume_limit_unit",
            "flux_data_volume_limit_size",
            "flux_data_volume_alert_percent",
            "flux_auto_clear_flow_data_switch",
            "flux_clear_date",
            "flux_limited_disconnect",
            "sim_slot",
            "dual_sim_support",
            "sim_pin_status",
            "sim_pinnumber",
            "puknumber",
            "sms_unread_num",
            "sms_data_total",
            "new_version_state",
            "current_upgrade_state",
            "dm_new_version",
            "dm_description",
            "upgrade_result",
            "profile_name",
            "apn_wan_apn",
            "apn_mode",
            "apn_pdp_type",
            "apn_interface_version",
            "apn_auto_config",
            "wifi_chip1_ssid1_ssid",
            "m_ssid_enable",
            "guest_switch",
        )

        val SETTINGS_QUERIES = listOf(
            "queryAccessPointInfo",
            "queryWiFiModuleSwitch",
            "GetUpgAutoSetting",
            "locked_cell_info",
        )
    }
}