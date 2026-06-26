package cl.smt.conductores.data

import cl.smt.conductores.models.DireccionSmt

data class DireccionesResponse(
    val ok: Boolean,
    val mensaje: String,
    val direcciones: List<DireccionSmt> = emptyList()
)
