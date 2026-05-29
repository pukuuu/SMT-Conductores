package cl.smt.conductores.data

import cl.smt.conductores.models.PedidoSmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object SmtApi {
    suspend fun checkAppVersion(): AppVersionResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$CHOFER_URL/app-version")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")

            val statusCode = conn.responseCode

            val responseText = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            }

            val json = JSONObject(responseText)

            if (!json.optBoolean("success", false)) {
                return@withContext AppVersionResponse(
                    ok = false,
                    error = extraerMensaje(json, "No se pudo verificar versión")
                )
            }

            val data = json.getJSONObject("data")

            AppVersionResponse(
                ok = true,
                latestVersionCode = data.optInt("latestVersionCode"),
                minimumVersionCode = data.optInt("minimumVersionCode"),
                latestVersionName = data.optString("latestVersionName"),
                forceUpdate = data.optBoolean("forceUpdate"),
                message = data.optString("message"),
                playStoreUrl = data.optString("playStoreUrl")
            )

        } catch (e: Exception) {
            AppVersionResponse(
                ok = false,
                error = e.message ?: "Error verificando versión"
            )
        }
    }

    private const val AUTH_URL = "https://backend.smtransportes.app/wp-json/smt/v1"
    private const val CHOFER_URL = "https://backend.smtransportes.app/wp-json/smt-chofer/v1"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun extraerMensaje(json: JSONObject, fallback: String): String {
        return json.optString(
            "mensaje",
            json.optString(
                "message",
                json.optJSONObject("data")?.optString("message", fallback) ?: fallback
            )
        )
    }

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

    suspend fun loginBarcode(
        barcodeToken: String
    ): LoginResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$AUTH_URL/login-barcode")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject().apply {
                put("barcode_token", barcodeToken)
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
                    mensaje = json.optString("message", "QR inválido")
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

    suspend fun actualizarPedidoEstado(
        user: SmtUser,
        postId: Int,
        estado: String
    ): ApiSimpleResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$AUTH_URL/actualizarpedido")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-SMT-Token", user.token)
            conn.setRequestProperty("Authorization", "Bearer ${user.token}")

            val body = JSONObject().apply {
                put("user_id", user.id)
                put("token", user.token)
                put("post_id", postId)
                put("estado", estado)
            }

            OutputStreamWriter(conn.outputStream).use {
                it.write(body.toString())
            }

            val statusCode = conn.responseCode

            val responseText = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            }

            val json = if (responseText.isNotBlank()) JSONObject(responseText) else JSONObject()

            val okReal = json.optBoolean(
                "ok",
                json.optBoolean("success", statusCode in 200..299)
            )

            val mensaje = extraerMensaje(
                json,
                if (okReal) "Pedido actualizado" else "Error actualizando pedido"
            )

            ApiSimpleResponse(
                ok = statusCode in 200..299 && okReal,
                mensaje = mensaje
            )

        } catch (e: Exception) {
            ApiSimpleResponse(
                ok = false,
                mensaje = e.message ?: "Error desconocido"
            )
        }
    }

    suspend fun iniciarRuta(
        user: SmtUser
    ): ApiSimpleResponse = withContext(Dispatchers.IO) {
        try {
            val pedidosRes = cargarMisPedidos(user)

            if (!pedidosRes.ok) {
                return@withContext ApiSimpleResponse(
                    ok = false,
                    mensaje = pedidosRes.mensaje
                )
            }

            val pendientes = pedidosRes.pedidos.filter {
                it.estado.equals("pendiente", true)
            }

            if (pendientes.isEmpty()) {
                return@withContext ApiSimpleResponse(
                    ok = false,
                    mensaje = "No hay pedidos pendientes"
                )
            }

            notificarInicioRuta(user)

            var errores = 0

            pendientes.forEach { pedido ->
                val res = actualizarPedidoEstado(
                    user = user,
                    postId = pedido.id,
                    estado = "en_ruta"
                )

                if (!res.ok) {
                    errores++
                }
            }

            if (errores > 0) {
                return@withContext ApiSimpleResponse(
                    ok = false,
                    mensaje = "No se pudieron iniciar todos los pedidos"
                )
            }

            ApiSimpleResponse(
                ok = true,
                mensaje = "Ruta iniciada correctamente"
            )

        } catch (e: Exception) {
            ApiSimpleResponse(
                ok = false,
                mensaje = e.message ?: "Error desconocido"
            )
        }
    }

    private suspend fun notificarInicioRuta(
        user: SmtUser
    ): ApiSimpleResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$AUTH_URL/notificar-inicio-ruta")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true

            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-SMT-Token", user.token)
            conn.setRequestProperty("Authorization", "Bearer ${user.token}")

            val body = JSONObject().apply {
                put("user_id", user.id)
                put("token", user.token)
            }

            OutputStreamWriter(conn.outputStream).use {
                it.write(body.toString())
            }

            val statusCode = conn.responseCode

            val responseText = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            }

            val json = if (responseText.isNotBlank()) JSONObject(responseText) else JSONObject()

            ApiSimpleResponse(
                ok = statusCode in 200..299,
                mensaje = extraerMensaje(
                    json,
                    if (statusCode in 200..299) {
                        "Inicio de ruta notificado"
                    } else {
                        "Error notificando inicio de ruta"
                    }
                )
            )

        } catch (e: Exception) {
            ApiSimpleResponse(
                ok = false,
                mensaje = e.message ?: "Error desconocido"
            )
        }
    }

    suspend fun cerrarEntrega(
        user: SmtUser,
        postId: Int,
        temperatura: String,
        horaGuia: String,
        foto: java.io.File
    ): ApiSimpleResponse = withContext(Dispatchers.IO) {
        try {
            if (!foto.exists()) {
                return@withContext ApiSimpleResponse(
                    ok = false,
                    mensaje = "Foto no encontrada: ${foto.absolutePath}"
                )
            }

            if (foto.length() <= 0L) {
                return@withContext ApiSimpleResponse(
                    ok = false,
                    mensaje = "Foto vacía: ${foto.absolutePath}"
                )
            }

            val textMediaType = "text/plain; charset=utf-8".toMediaTypeOrNull()
            val imageMediaType = "image/jpeg".toMediaTypeOrNull()

            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("user_id", user.id.toString())
                .addFormDataPart("token", user.token)
                .addFormDataPart("post_id", postId.toString())
                .addFormDataPart("pedido_id", postId.toString())
                .addFormDataPart("id", postId.toString())
                .addFormDataPart("temperatura", temperatura)
                .addFormDataPart("hora_guia", horaGuia)
                .addFormDataPart("horaEntrega", horaGuia)
                .addFormDataPart(
                    "foto",
                    if (foto.name.endsWith(".jpg", true) || foto.name.endsWith(".jpeg", true)) {
                        foto.name
                    } else {
                        "entrega_$postId.jpg"
                    },
                    foto.asRequestBody(imageMediaType)
                )
                .build()

            val request = Request.Builder()
                .url("$CHOFER_URL/cerrarentrega")
                .post(body)
                .addHeader("X-SMT-Token", user.token)
                .addHeader("Authorization", "Bearer ${user.token}")
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val statusCode = response.code
                val responseText = response.body?.string().orEmpty()

                val json = try {
                    if (responseText.isNotBlank()) JSONObject(responseText) else JSONObject()
                } catch (e: Exception) {
                    JSONObject().apply {
                        put("mensaje", responseText)
                    }
                }

                val okApi = json.optBoolean(
                    "ok",
                    json.optBoolean("success", statusCode in 200..299)
                )

                val mensajeApi = extraerMensaje(
                    json,
                    if (statusCode in 200..299) {
                        "Entrega cerrada correctamente"
                    } else {
                        "HTTP $statusCode: $responseText"
                    }
                )

                ApiSimpleResponse(
                    ok = statusCode in 200..299 && okApi,
                    mensaje = if (statusCode in 200..299 && okApi) {
                        mensajeApi.ifBlank { "Entrega cerrada correctamente" }
                    } else {
                        "HTTP $statusCode - ${mensajeApi.ifBlank { "Error cerrando entrega" }}"
                    }
                )
            }

        } catch (e: Exception) {
            ApiSimpleResponse(
                ok = false,
                mensaje = "Error cerrarEntrega APK: ${e.message ?: "Error desconocido"}"
            )
        }
    }

    suspend fun crearPedido(
        user: SmtUser,
        factura: String,
        paciente: String,
        direccion: String,
        comuna: String,
        telefono: String,
        tipoEnvio: String,
        patente: String
    ): ApiSimpleResponse = withContext(Dispatchers.IO) {
        try {
            val url = URL("$AUTH_URL/crearpedido")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-SMT-Token", user.token)
            conn.setRequestProperty("Authorization", "Bearer ${user.token}")

            val body = JSONObject().apply {
                put("user_id", user.id)
                put("token", user.token)
                put("factura", factura)
                put("paciente", paciente)
                put("direccion", direccion)
                put("ciudad_region", comuna)
                put("telefono", telefono)
                put("tipo_envio", tipoEnvio)
                put("patente", patente)
            }

            OutputStreamWriter(conn.outputStream).use {
                it.write(body.toString())
            }

            val statusCode = conn.responseCode

            val responseText = if (statusCode in 200..299) {
                conn.inputStream.bufferedReader().use(BufferedReader::readText)
            } else {
                conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
            }

            val json = if (responseText.isNotBlank()) JSONObject(responseText) else JSONObject()

            val mensajeApi = extraerMensaje(json, "Error al crear pedido")

            val mensajeLimpio = when {
                mensajeApi.contains("existe", true) ||
                        mensajeApi.contains("duplic", true) ||
                        mensajeApi.contains("already", true) -> "Guía ya existe"

                mensajeApi.contains("patente", true) -> "Patente inválida"
                mensajeApi.contains("token", true) -> "Sesión expirada"
                mensajeApi.isBlank() -> "Error al crear pedido"

                else -> mensajeApi
            }

            ApiSimpleResponse(
                ok = statusCode in 200..299 && json.optBoolean("ok", false),
                mensaje = if (statusCode in 200..299) {
                    json.optString("mensaje", "Pedido creado correctamente")
                } else {
                    mensajeLimpio
                }
            )

        } catch (e: Exception) {
            ApiSimpleResponse(
                ok = false,
                mensaje = e.message ?: "Error desconocido"
            )
        }
    }

    suspend fun cargarPatentes(
        user: SmtUser
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$AUTH_URL/patentes?user_id=${user.id}&token=${user.token}")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "GET"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-SMT-Token", user.token)
            conn.setRequestProperty("Authorization", "Bearer ${user.token}")

            val response = conn.inputStream.bufferedReader()
                .use(BufferedReader::readText)

            val json = JSONObject(response)

            if (!json.optBoolean("ok")) {
                return@withContext emptyList()
            }

            val arr = json.getJSONArray("patentes")
            val patentes = mutableListOf<String>()

            for (i in 0 until arr.length()) {
                patentes.add(arr.optString(i))
            }

            patentes

        } catch (e: Exception) {
            emptyList()
        }
    }
}