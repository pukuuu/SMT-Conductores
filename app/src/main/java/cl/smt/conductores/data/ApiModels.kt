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

data class PatentesResponse(
    val ok: Boolean,
    val mensaje: String = "",
    val patentes: List<String> = emptyList()
)

data class VerificarVinculacionResponse(
    val ok: Boolean,
    val requiereVinculacion: Boolean = false,
    val mensaje: String = ""
)

data class RutaBackendPoint(
    val pedidoId: Int,
    val nombre: String,
    val lat: Double,
    val lng: Double
)

data class RutaGeometryPoint(
    val lat: Double,
    val lng: Double
)

data class OptimizarRutaResponse(
    val ok: Boolean,
    val mensaje: String = "",
    val orderedIds: List<Int> = emptyList(),
    val geometry: List<RutaGeometryPoint> = emptyList(),
    val durationSeconds: Double? = null,
    val distanceMeters: Double? = null,
    val originalDurationSeconds: Double? = null,
    val optimizedDurationSeconds: Double? = null,
    val savedSeconds: Double? = null,
    val source: String = "",
    val algorithm: String = ""
)
