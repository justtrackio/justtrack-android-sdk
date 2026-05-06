package io.justtrack.config

internal data class RemoteConfigQueryParams(
    val installInstanceId: String,
    val osVersion: String,
    val appVersionCode: String,
    val appVersionName: String,
    val sdkVersionMajor: String,
    val sdkVersionMinor: String,
    val sdkVersionPatch: String,
    val sdkVersionName: String,
    val sdkVersionPlatform: String,
    val sdkVersionWrapper: String?,
    val deviceType: String,
    val deviceModel: String,
    val countryIso2: String?,
    val deviceTimestamp: Long,
    val attributionTimestamp: Long?,
    val firstSdkInitTimestamp: Long?,
    val installTimestamp: Long?,
) {

    fun toMap(): Map<String, String> {
        return listOfNotNull(
            "installInstanceId" to installInstanceId,
            "osVersion" to osVersion,
            "appVersionCode" to appVersionCode,
            "appVersionName" to appVersionName,
            "sdkVersionMajor" to sdkVersionMajor,
            "sdkVersionMinor" to sdkVersionMinor,
            "sdkVersionPatch" to sdkVersionPatch,
            "sdkVersionName" to sdkVersionName,
            "sdkVersionPlatform" to sdkVersionPlatform,
            "deviceType" to deviceType,
            "deviceModel" to deviceModel,
            sdkVersionWrapper?.let { "sdkVersionWrapper" to it },
            countryIso2?.let { "countryIso2" to it },
            "deviceTimestamp" to deviceTimestamp.toString(),
            attributionTimestamp?.let { "attributionTimestamp" to it.toString() },
            firstSdkInitTimestamp?.let { "firstSdkInitTimestamp" to it.toString() },
            "installTimestamp" to installTimestamp.toString(),

        ).toMap()
    }
}
