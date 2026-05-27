package cl.smt.conductores.models

data class EntregaPendiente(
    val idLocal: Long = System.currentTimeMillis(),
    val postId: Int,
    val factura: String,
    val temperatura: String,
    val horaGuia: String,
    val fotoPath: String,
    var estado: String = "pendiente",
    var intentos: Int = 0
)
