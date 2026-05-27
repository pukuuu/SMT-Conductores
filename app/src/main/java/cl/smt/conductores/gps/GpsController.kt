package cl.smt.conductores.gps

import android.content.Context

object GpsController {
    fun estaActivo(context: Context): Boolean =
        context.getSharedPreferences("smt_gps", Context.MODE_PRIVATE).getBoolean("activo", false)

    fun iniciar(context: Context): Boolean {
        context.getSharedPreferences("smt_gps", Context.MODE_PRIVATE).edit().putBoolean("activo", true).apply()
        return true
    }

    fun detener(context: Context) {
        context.getSharedPreferences("smt_gps", Context.MODE_PRIVATE).edit().putBoolean("activo", false).apply()
    }
}
