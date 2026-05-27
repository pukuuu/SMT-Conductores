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

    private const val BASE_URL = "https://smt-seguimientos.cl/wp-json/smt-chofer/v1"

    suspend fun login(
        email: String,
        password: String
    ): LoginResponse = withContext(Dispatchers.IO) {

        try {
            val url = URL("$BASE_URL/login-token")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("email", email)
                put("password", password)
            }

            OutputStreamWriter(conn.outputStream).use {
                it.write(body.toString())
            }

            val response = conn.inputStream.bufferedReader()
                .use(BufferedReader::readText)

            val json = JSONObject(response)

            if (!json.optBoolean("ok")) {
                return@withContext LoginResponse(
                    ok = false,
                    mensaje = json.optString("mensaje")
                )
            }

            val userJson = json.getJSONObject("user")

            LoginResponse(
                ok = true,
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

            val url = URL("$BASE_URL/mispedidos")

            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("user_id", user.id)
                put("token", user.token)
            }

            OutputStreamWriter(conn.outputStream).use {
                it.write(body.toString())
            }

            val response = conn.inputStream.bufferedReader()
                .use(BufferedReader::readText)

            val json = JSONObject(response)

            if (!json.optBoolean("ok")) {
                return@withContext PedidosResponse(
                    ok = false,
                    mensaje = json.optString("mensaje")
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