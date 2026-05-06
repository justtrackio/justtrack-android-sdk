package io.justtrack

internal enum class ConnectionType(private val type: String) {
    OFFLINE("offline"),
    BLUETOOTH("bluetooth"),
    ETHERNET("ethernet"),
    CELLULAR_UNKNOWN("cellular_unknown"),
    CELLULAR_2G("cellular_2g"),
    CELLULAR_3G("cellular_3g"),
    CELLULAR_4G("cellular_4g"),
    CELLULAR_5G("cellular_5g"),
    VPN("vpn"),
    WIFI("wifi"),
    UNKNOWN("unknown"),
    ;

    override fun toString(): String {
        return type
    }
}
