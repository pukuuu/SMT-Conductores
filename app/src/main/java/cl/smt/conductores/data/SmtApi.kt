package cl.smt.conductores.data

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

            val response = conn.inputStream.bufferedReader().use(BufferedReader::readText)

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
}