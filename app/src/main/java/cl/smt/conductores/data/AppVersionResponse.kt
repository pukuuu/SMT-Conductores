package cl.smt.conductores.data

data class AppVersionResponse(
    val ok: Boolean,
    val latestVersionCode: Int = 0,
    val minimumVersionCode: Int = 0,
    val latestVersionName: String = "",
    val forceUpdate: Boolean = false,
    val message: String = "",
    val playStoreUrl: String = "",
    val error: String = ""
)