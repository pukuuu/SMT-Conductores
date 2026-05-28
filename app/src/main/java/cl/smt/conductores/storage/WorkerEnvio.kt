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

    fun procesarCola(
        context: Context,
        onResultado: ((Boolean, String) -> Unit)? = null
    ) {
        if (enviando) return

        val user = SessionManager.getUser(context)

        if (user == null) {
            onResultado?.invoke(false, "Sesión inválida")
            return
        }

        val entrega = ColaEntregas.obtenerEntregas(context)
            .firstOrNull {
                it.estado == "pendiente" || it.estado == "error"
            }

        if (entrega == null) {
            onResultado?.invoke(true, "No hay entregas pendientes")
            return
        }

        enviando = true

        entrega.estado = "enviando"
        ColaEntregas.actualizarEntrega(context, entrega)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val foto = File(entrega.fotoPath)

                val res = SmtApi.cerrarEntrega(
                    user = user,
                    postId = entrega.postId,
                    temperatura = entrega.temperatura,
                    horaGuia = entrega.horaGuia,
                    foto = foto
                )

                if (res.ok) {
                    ColaEntregas.eliminarEntrega(context, entrega.idLocal)
                    onResultado?.invoke(true, res.mensaje)
                } else {
                    marcarError(context, entrega)
                    onResultado?.invoke(false, res.mensaje)
                }

            } catch (e: Exception) {
                marcarError(context, entrega)
                onResultado?.invoke(false, e.message ?: "Error enviando entrega")
            } finally {
                enviando = false
            }
        }
    }

    private fun marcarError(
        context: Context,
        entrega: EntregaPendiente
    ) {
        entrega.estado = "error"
        entrega.intentos++
        ColaEntregas.actualizarEntrega(context, entrega)
    }
}