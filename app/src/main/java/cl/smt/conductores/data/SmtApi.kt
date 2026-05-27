package cl.smt.conductores.data

import cl.smt.conductores.models.PedidoSmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SmtApi {

    private const val AUTH_URL = "https://backend.smtransportes.app/wp-json/smt/v1"
    private const val CHOFER_URL = "https://backend.smtransportes.app/wp-json/smt-chofer/v1"

    suspend fun login(
        usuario: String,
        password: String
    ): LoginResponse = withContext(Dispatchers.IO) {

        try {
            val url = URL("$AUTH_URL/login")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("username", usuario)
                put("password", password)
            }

            OutputStreamWriter(conn.outputStream).use {
                it.write(body.toString())
            }

            val response = conn.inputStream.bufferedReader()
                .use(BufferedReader::readText)

            val json = JSONObject(response)

            if (!json.optBoolean("success")) {
                return@withContext LoginResponse(
                    ok = false,
                    mensaje = json.optString("message", "Login inválido")
                )
            }

            val userJson = json.getJSONObject("user")

            LoginResponse(
                ok = true,
                mensaje = "Login correcto",
                user = SmtUser(
                    id = userJson.optInt("id"),
                    name = userJson.optString("name"),
                    email = userJson.optString("email"),
                    role = userJson.optString("role"),
                    token = userJson.optString("token"),
                    sucursal = userJson.optString("sucursal"),
                    traccarId = userJson.optString("traccar_id")
                )
            )

        } catch (e: Exception) {
            LoginResponse(
                ok = false,
                mensaje = e.message ?: "Error desconocido"
            )
        }
    }

    suspend fun cargarMisPedidos(
        user: SmtUser
    ): PedidosResponse = withContext(Dispatchers.IO) {

        try {
            val url = URL("$AUTH_URL/mis-pedidos?user_id=${user.id}&token=${user.token}")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-SMT-Token", user.token)
            conn.setRequestProperty("Authorization", "Bearer ${user.token}")

            val response = conn.inputStream.bufferedReader()
                .use(BufferedReader::readText)

            val json = JSONObject(response)

            if (!json.optBoolean("ok")) {
                return@withContext PedidosResponse(
                    ok = false,
                    mensaje = json.optString("mensaje", "No se pudieron cargar pedidos")
                )
            }

            val pedidosJson = json.getJSONArray("pedidos")
            val pedidos = mutableListOf<PedidoSmt>()

            for (i in 0 until pedidosJson.length()) {
                val p = pedidosJson.getJSONObject(i)

                pedidos.add(
                    PedidoSmt(
                        id = p.optInt("id"),
                        fecha = p.optString("fecha"),
                        factura = p.optString("factura"),
                        paciente = p.optString("paciente"),
                        direccion = p.optString("direccion"),
                        comuna = p.optString("comuna"),
                        telefono = p.optString("telefono"),
                        tipoEnvio = p.optString("tipo_envio"),
                        estado = p.optString("estado"),
                        temperatura = p.optString("temperatura"),
                        horaEntrega = p.optString("hora_entrega"),
                        patente = p.optString("patente"),
                        conductorId = p.optString("conductor_id"),
                        sucursal = p.optString("sucursal"),
                        respaldoUrl = p.optString("respaldo_url"),
                        motivoProblema = p.optString("motivo_problema")
                    )
                )
            }

            PedidosResponse(
                ok = true,
                mensaje = "Pedidos cargados",
                pedidos = pedidos
            )

        } catch (e: Exception) {
            PedidosResponse(
                ok = false,
                mensaje = e.message ?: "Error desconocido"
            )
        }
    }
}