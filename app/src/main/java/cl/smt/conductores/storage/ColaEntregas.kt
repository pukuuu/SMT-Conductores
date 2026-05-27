package cl.smt.conductores.storage

import android.content.Context
import cl.smt.conductores.models.EntregaPendiente
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ColaEntregas {
    private const val PREFS = "smt_entregas"
    private const val KEY = "cola"
    private val gson = Gson()

    fun guardarEntrega(context: Context, entrega: EntregaPendiente) {
        val lista = obtenerEntregas(context).toMutableList()
        lista.add(entrega)
        guardarLista(context, lista)
    }

    fun obtenerEntregas(context: Context): List<EntregaPendiente> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<EntregaPendiente>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun guardarLista(context: Context, lista: List<EntregaPendiente>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, gson.toJson(lista))
            .apply()
    }

    fun eliminarEntrega(context: Context, idLocal: Long) {
        val lista = obtenerEntregas(context).toMutableList()
        lista.removeAll { it.idLocal == idLocal }
        guardarLista(context, lista)
    }

    fun actualizarEntrega(context: Context, entrega: EntregaPendiente) {
        val lista = obtenerEntregas(context).toMutableList()
        val index = lista.indexOfFirst { it.idLocal == entrega.idLocal }
        if (index != -1) {
            lista[index] = entrega
            guardarLista(context, lista)
        }
    }
}
