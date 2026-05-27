package cl.smt.conductores.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.data.SmtUser
import cl.smt.conductores.models.PedidoSmt
import kotlinx.coroutines.launch

@Composable
fun PedidosScreen(
    user: SmtUser,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var pedidos by remember { mutableStateOf<List<PedidoSmt>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }

    fun cargar() {
        loading = true
        mensaje = ""

        scope.launch {
            val res = SmtApi.cargarMisPedidos(user)

            loading = false

            if (res.ok) {
                pedidos = res.pedidos
            } else {
                mensaje = res.mensaje.ifBlank { "No se pudieron cargar pedidos" }
            }
        }
    }

    LaunchedEffect(Unit) {
        cargar()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Mis pedidos",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { cargar() },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Cargando..." else "Actualizar")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (mensaje.isNotBlank()) {
            Text(
                text = mensaje,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(pedidos) { pedido ->
                PedidoCard(pedido)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }
    }
}

@Composable
fun PedidoCard(
    pedido: PedidoSmt
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Factura: ${pedido.factura}")
            Text("Paciente: ${pedido.paciente.ifBlank { "Sin paciente" }}")
            Text("Dirección: ${pedido.direccion.ifBlank { pedido.comuna }}")
            Text("Estado: ${pedido.estado}")
            Text("Patente: ${pedido.patente.ifBlank { "-" }}")
        }
    }
}