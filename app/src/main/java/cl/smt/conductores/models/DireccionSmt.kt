package cl.smt.conductores.models

data class DireccionSmt(
    val id: Int = 0,
    val sucursal: String = "",
    val nombre: String = "",
    val wazeUrl: String = "",
    val mapsUrl: String = "",
    val notas: String = "",
    val fotos: List<String> = emptyList()
)
