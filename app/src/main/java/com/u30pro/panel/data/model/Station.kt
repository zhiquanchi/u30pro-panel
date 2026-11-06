package com.u30pro.panel.data.model

data class Station(
    val mac: String,
    val hostname: String? = null,
    val ip: String? = null,
    val kind: Kind = Kind.WIFI,
    val blocked: Boolean = false,
) {
    enum class Kind { WIFI, LAN }

    val displayName: String
        get() = hostname?.takeIf { it.isNotBlank() } ?: mac
}

data class StationSnapshot(
    val stations: List<Station> = emptyList(),
    val aclMode: Int? = null,
    val blacklist: Set<String> = emptySet(),
    val aclSupported: Boolean = false,
) {
    val wifiCount: Int get() = stations.count { it.kind == Station.Kind.WIFI }
    val lanCount: Int get() = stations.count { it.kind == Station.Kind.LAN }
}