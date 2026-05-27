package cl.smt.conductores.data

data class SmtUser(
    val id: Int,
    val name: String,
    val email: String,
    val role: String,
    val token: String,
    val sucursal: String,
    val traccarId: String = ""
)