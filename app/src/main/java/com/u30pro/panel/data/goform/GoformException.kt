package com.u30pro.panel.data.goform

/** Domain error for everything the stock goform backend can throw at us. */
class GoformException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    enum class Kind {
        NOT_REACHABLE,
        SECURE_HEADER,
        WRONG_PASSWORD,
        SESSION_BUSY,
        NOT_LOGGED_IN,
        UNSUPPORTED,
        DEVICE,
        PARSE,
    }
}

fun GoformException.userMessage(): String = when (kind) {
    GoformException.Kind.NOT_REACHABLE ->
        "连不上设备。请确认手机已连接 U30 Pro 的 WiFi，网关通常是 192.168.0.1。"
    GoformException.Kind.SECURE_HEADER ->
        "设备拒绝了请求（安全头校验失败），可能是固件行为不同。"
    GoformException.Kind.WRONG_PASSWORD ->
        "管理密码错误。"
    GoformException.Kind.SESSION_BUSY ->
        "设备后台正被占用。同一时间只允许一个管理会话，已暂停轮询 30 秒。"
    GoformException.Kind.NOT_LOGGED_IN ->
        "会话已失效，正在重新登录。"
    GoformException.Kind.UNSUPPORTED ->
        "当前固件不支持这个操作。"
    GoformException.Kind.DEVICE ->
        message ?: "设备返回了错误。"
    GoformException.Kind.PARSE ->
        "无法解析设备返回的数据。"
}