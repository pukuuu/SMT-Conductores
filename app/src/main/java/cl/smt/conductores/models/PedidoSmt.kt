package cl.smt.conductores.models

data class PedidoSmt(
    val id: Int,
    val fecha: String = "",
    val factura: String = "",
    val paciente: String = "",
    val direccion: String = "",
    val comuna: String = "",
    val telefono: String = "",
    val tipoEnvio: String = "",
    val estado: String = "",
    val temperatura: String = "",
    val horaEntrega: String = "",
    val patente: String = "",
    val conductorId: String = "",
    val sucursal: String = "",
    val respaldoUrl: String = "",
    val motivoProblema: String = ""
)
