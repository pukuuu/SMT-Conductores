package cl.smt.conductores.storage

import android.content.Context
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

object WorkerEnvio {

    private var procesando = false

    fun procesarCola(
        context: Context,
        callback: ((Boolean, String) -> Unit)? = null
    ) {
        if (procesando) {
            callback?.invoke(false, "Ya hay envíos en proceso")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            procesando = true

            try {
                val user = SessionManager.getUser(context)

                if (user == null) {
                    callback?.invoke(false, "Sesión inválida")
                    return@launch
                }

                var procesadas = 0
                var errores = 0

                while (true) {
                    val entrega = ColaEntregas.obtenerEntregas(context)
                        .firstOrNull {
                            it.estado == "pendiente" || it.estado == "error"
                        } ?: break

                    entrega.estado = "enviando"
                    ColaEntregas.actualizarEntrega(context, entrega)

                    val foto = File(entrega.fotoPath)

                    if (!foto.exists() || foto.length() <= 0L) {
                        entrega.estado = "error"
                        entrega.intentos++
                        ColaEntregas.actualizarEntrega(context, entrega)
                        errores++
                        break
                    }

                    val res = SmtApi.cerrarEntrega(
                        user = user,
                        postId = entrega.postId,
                        temperatura = entrega.temperatura,
                        horaGuia = entrega.horaGuia,
                        foto = foto
                    )

                    if (res.ok) {
                        ColaEntregas.eliminarEntrega(
                            context,
                            entrega.idLocal
                        )

                        foto.delete()
                        procesadas++
                    } else {
                        entrega.estado = "error"
                        entrega.intentos++
                        ColaEntregas.actualizarEntrega(context, entrega)
                        errores++
                        break
                    }
                }

                val pendientes = ColaEntregas.obtenerEntregas(context).size

                val mensaje = when {
                    procesadas > 0 && pendientes == 0 ->
                        "Todas las entregas fueron enviadas"

                    procesadas > 0 && pendientes > 0 ->
                        "Se enviaron $procesadas entrega(s). Quedan $pendientes pendiente(s)."

                    errores > 0 ->
                        "No se pudo enviar una entrega. Revisa la conexión."

                    pendientes > 0 ->
                        "Hay $pendientes entrega(s) pendiente(s)"

                    else ->
                        "No hay entregas pendientes"
                }

                callback?.invoke(errores == 0, mensaje)

            } catch (e: Exception) {
                callback?.invoke(false, e.message ?: "Error procesando cola")
            } finally {
                procesando = false
            }
        }
    }
}