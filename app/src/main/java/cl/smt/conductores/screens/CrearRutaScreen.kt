package cl.smt.conductores.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cl.smt.conductores.components.Camera2BarcodeScanner
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearRutaScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = SessionManager.getUser(context)

    var factura by remember { mutableStateOf("") }
    var paciente by remember { mutableStateOf("") }
    var direccion by remember { mutableStateOf("") }
    var comuna by remember { mutableStateOf("") }
    var telefono by remember { mutableStateOf("") }

    var patente by remember { mutableStateOf("") }
    var patentes by remember { mutableStateOf<List<String>>(emptyList()) }
    var patenteExpandida by remember { mutableStateOf(false) }

    var tipoEnvio by remember { mutableStateOf("A") }
    var tipoExpandido by remember { mutableStateOf(false) }

    var cargando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var mostrandoScanner by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (user != null) {
            patentes = SmtApi.cargarPatentes(user)
            if (patentes.isNotEmpty() && patente.isBlank()) {
                patente = patentes.first()
            }
        }
    }

    if (mostrandoScanner) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Camera2BarcodeScanner(
                barcodeFormat = Barcode.FORMAT_PDF417,
                modifier = Modifier.fillMaxSize(),
                onCodeScanned = { codigo ->
                    factura = codigo.take(40)
                    mensaje = "Guía escaneada"
                    mostrandoScanner = false
                },
                onError = { error ->
                    mensaje = error
                }
            )

            OutlinedButton(
                onClick = { mostrandoScanner = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .align(androidx.compose.ui.Alignment.BottomCenter)
            ) {
                Text("Cancelar escaneo")
            }
        }

        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF00140D),
                        Color(0xFF020617),
                        Color(0xFF001F14)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(28.dp))

        Text(
            text = "Crear ruta",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White
        )

        Button(
            onClick = {
                mensaje = ""
                mostrandoScanner = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Escanear guía PDF417")
        }

        OutlinedTextField(
            value = factura,
            onValueChange = { factura = it },
            label = { Text("Factura") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = paciente,
            onValueChange = { paciente = it },
            label = { Text("Paciente") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = direccion,
            onValueChange = { direccion = it },
            label = { Text("Dirección") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = comuna,
            onValueChange = { comuna = it },
            label = { Text("Comuna") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = telefono,
            onValueChange = { telefono = it },
            label = { Text("Teléfono") },
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = patenteExpandida,
            onExpandedChange = { patenteExpandida = !patenteExpandida }
        ) {
            OutlinedTextField(
                value = patente,
                onValueChange = {},
                readOnly = true,
                label = { Text("Patente") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = patenteExpandida)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            DropdownMenu(
                expanded = patenteExpandida,
                onDismissRequest = { patenteExpandida = false }
            ) {
                patentes.forEach { p ->
                    DropdownMenuItem(
                        text = { Text(p) },
                        onClick = {
                            patente = p
                            patenteExpandida = false
                        }
                    )
                }
            }
        }

        ExposedDropdownMenuBox(
            expanded = tipoExpandido,
            onExpandedChange = { tipoExpandido = !tipoExpandido }
        ) {
            OutlinedTextField(
                value = when (tipoEnvio) {
                    "A" -> "Therapia"
                    "B" -> "Profar"
                    "C" -> "Cesfar"
                    else -> tipoEnvio
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Tipo envío") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = tipoExpandido)
                },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )

            DropdownMenu(
                expanded = tipoExpandido,
                onDismissRequest = { tipoExpandido = false }
            ) {
                listOf("A", "B", "C").forEach { tipo ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                when (tipo) {
                                    "A" -> "Therapia"
                                    "B" -> "Profar"
                                    else -> "Cesfar"
                                }
                            )
                        },
                        onClick = {
                            tipoEnvio = tipo
                            tipoExpandido = false
                        }
                    )
                }
            }
        }

        if (mensaje.isNotBlank()) {
            Text(
                text = mensaje,
                color = if (
                    mensaje.contains("correct", true) ||
                    mensaje.contains("escaneada", true)
                ) Color(0xFF00C853) else Color.Red
            )
        }

        Button(
            onClick = {
                if (user == null) {
                    mensaje = "Sesión inválida"
                    return@Button
                }

                if (factura.isBlank() || direccion.isBlank() || patente.isBlank()) {
                    mensaje = "Faltan datos"
                    return@Button
                }

                scope.launch {
                    cargando = true
                    mensaje = ""

                    val res = SmtApi.crearPedido(
                        user = user,
                        factura = factura,
                        paciente = paciente,
                        direccion = direccion,
                        comuna = comuna,
                        telefono = telefono,
                        tipoEnvio = tipoEnvio,
                        patente = patente
                    )

                    cargando = false
                    mensaje = res.mensaje

                    if (res.ok) {
                        factura = ""
                        paciente = ""
                        direccion = ""
                        comuna = ""
                        telefono = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            if (cargando) {
                CircularProgressIndicator()
            } else {
                Text("Crear pedido")
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Volver")
        }
    }
}