package cl.smt.conductores.data

data class ApiSimpleResponse(
    val ok: Boolean,
    val mensaje: String = ""
)

data class LoginResponse(
    val ok: Boolean,
    val mensaje: String = "",
    val user: SmtUser? = null
)