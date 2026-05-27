package cl.smt.conductores.data

import cl.smt.conductores.models.PedidoSmt

data class ApiSimpleResponse(
    val ok: Boolean,
    val mensaje: String = ""
)

data class LoginResponse(
    val ok: Boolean,
    val mensaje: String = "",
    val user: SmtUser? = null
)

data class PedidosResponse(
    val ok: Boolean,
    val mensaje: String = "",
    val pedidos: List<PedidoSmt> = emptyList()
)