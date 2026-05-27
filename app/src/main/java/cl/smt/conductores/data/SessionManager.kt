package cl.smt.conductores.data

import android.content.Context
import org.json.JSONObject

object SessionManager {

    private const val PREFS = "smt_session"
    private const val KEY_USER = "user"

    fun saveUser(context: Context, user: SmtUser) {
        val json = JSONObject().apply {
            put("id", user.id)
            put("name", user.name)
            put("email", user.email)
            put("role", user.role)
            put("token", user.token)
            put("sucursal", user.sucursal)
            put("traccarId", user.traccarId)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_USER, json.toString())
            .apply()
    }

    fun getUser(context: Context): SmtUser? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_USER, null)
            ?: return null

        return try {
            val json = JSONObject(raw)

            SmtUser(
                id = json.optInt("id"),
                name = json.optString("name"),
                email = json.optString("email"),
                role = json.optString("role"),
                token = json.optString("token"),
                sucursal = json.optString("sucursal"),
                traccarId = json.optString("traccarId")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun isLoggedIn(context: Context): Boolean {
        return getUser(context) != null
    }
}