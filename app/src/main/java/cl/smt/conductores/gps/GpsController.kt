package cl.smt.conductores.gps

import android.content.Context
import android.content.Intent
import android.os.Build
import cl.smt.conductores.data.SessionManager

object GpsController {

    private const val PREFS = "smt_gps"
    private const val KEY_ACTIVO = "gps_activo"

    fun iniciar(context: Context): Boolean {
        val user = SessionManager.getUser(context)
        val traccarId = user?.traccarId?.trim().orEmpty()

        if (traccarId.isBlank()) {
            return false
        }

        val intent = Intent(context, SmtLocationService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVO, true)
            .apply()

        return true
    }

    fun detener(context: Context) {
        val intent = Intent(context, SmtLocationService::class.java)
        context.stopService(intent)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ACTIVO, false)
            .apply()
    }

    fun estaActivo(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVO, false)
    }
}