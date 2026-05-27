package cl.smt.conductores.storage

import android.content.Context
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.models.EntregaPendiente
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object WorkerEnvio {
    private var enviando = false

    fun procesarCola(context: Context) {
        if (enviando) return
        val user = SessionManager.getUser(context) ?: return
        val entrega = ColaEntregas.obtenerEntregas(context)
            .firstOrNull { it.estado == "pendiente" || it.estado == "error" } ?: return

        enviando = true
        entrega.estado = "enviando"
        ColaEntregas.actualizarEntrega(context, entrega)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val res = SmtApi.cerrarEntrega(
                    user = user,
                    postId = entrega.postId,
                    temperatura = entrega.temperatura,
                    horaGuia = entrega.horaGuia,
                    foto = File(entrega.fotoPath)
                )
                if (res.ok) {
                    ColaEntregas.eliminarEntrega(context, entrega.idLocal)
                } else {
                    marcarError(context, entrega)
                }
            } catch (_: Exception) {
                marcarError(context, entrega)
            } finally {
                enviando = false
            }
        }
    }

    private fun marcarError(context: Context, entrega: EntregaPendiente) {
        entrega.estado = "error"
        entrega.intentos++
        ColaEntregas.actualizarEntrega(context, entrega)
    }
}
