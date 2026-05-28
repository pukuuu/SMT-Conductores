package cl.smt.conductores.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

data class GuiaPdf417Data(
    val factura: String = "",
    val paciente: String = "",
    val direccion: String = "",
    val comuna: String = "",
    val telefono: String = ""
)

fun parsearGuiaPdf417(raw: String): GuiaPdf417Data {
    val partes = raw.trim().split("*").map { it.trim() }.filter { it.isNotBlank() }

    return GuiaPdf417Data(
        factura = partes.getOrNull(5).orEmpty(),
        paciente = partes.getOrNull(6).orEmpty(),
        direccion = partes.getOrNull(7).orEmpty(),
        comuna = partes.getOrNull(8).orEmpty()
            .replace("RegiÃ³nMetropolitana", "")
            .replace("RegiónMetropolitana", "")
            .replace("RegionMetropolitana", "")
            .trim(),
        telefono = partes.getOrNull(10).orEmpty()
    )
}

fun mensajeCrearRutaLimpio(mensaje: String): String {
    return when {
        mensaje.contains("existe", true) ||
                mensaje.contains("duplic", true) ||
                mensaje.contains("already", true) -> "Guía ya existe"

        mensaje.contains("patente", true) -> "Patente inválida"
        mensaje.contains("token", true) -> "Sesión expirada"
        mensaje.contains("timeout", true) -> "Servidor demoró demasiado"
        mensaje.isBlank() -> "Error al crear pedido"

        else -> mensaje
    }
}

fun esMensajeOk(mensaje: String): Boolean {
    return mensaje.contains("correct", true) ||
            mensaje.contains("escaneada", true) ||
            mensaje.contains("creado", true) ||
            mensaje.contains("creada", true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrearRutaScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val prefs = remember {
        context.getSharedPreferences("smt_config", android.content.Context.MODE_PRIVATE)
    }

    val beepActivo = remember {
        mutableStateOf(prefs.getBoolean("beep_scan_enabled", true))
    }

    fun reproducirBeep() {
        if (!beepActivo.value) return

        try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                .startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {
        }
    }

    val scope = rememberCoroutineScope()
    val user = SessionManager.getUser(context)

    var codigoEscaneado by remember { mutableStateOf("") }

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
    var flashActivo by remember { mutableStateOf(false) }

    fun crearPedidoDesdeFormulario() {
        if (user == null) {
            mensaje = "Sesión inválida"
            return
        }

        if (factura.isBlank() || direccion.isBlank() || patente.isBlank()) {
            mensaje = "Faltan datos"
            return
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
            mensaje = mensajeCrearRutaLimpio(res.mensaje)

            if (res.ok) {
                codigoEscaneado = ""
                factura = ""
                paciente = ""
                direccion = ""
                comuna = ""
                telefono = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        if (user != null) {
            patentes = SmtApi.cargarPatentes(user)

            if (patentes.isNotEmpty() && patente.isBlank()) {
                patente = patentes.first()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 120.dp),
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
                    flashActivo = false
                    mostrandoScanner = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Escanear guía PDF417")
            }

            Button(
                onClick = { crearPedidoDesdeFormulario() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !cargando
            ) {
                if (cargando) {
                    CircularProgressIndicator()
                } else {
                    Text("Crear pedido")
                }
            }

            if (codigoEscaneado.isNotBlank()) {
                Text("Código leído correctamente", color = Color(0xFF00C853))
            }

            if (mensaje.isNotBlank()) {
                Text(
                    text = mensaje,
                    color = if (esMensajeOk(mensaje)) Color(0xFF00C853) else Color.Red
                )
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

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Volver")
            }
        }

        if (mostrandoScanner) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)),
                    border = BorderStroke(1.dp, Color(0xFF00C853))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Escanear PDF417",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .background(Color.Black, RoundedCornerShape(18.dp))
                        ) {
                            Camera2BarcodeScanner(
                                barcodeFormat = Barcode.FORMAT_PDF417,
                                flashEnabled = flashActivo,
                                modifier = Modifier.fillMaxSize(),
                                onCodeScanned = { codigo ->
                                    codigoEscaneado = codigo

                                    val datos = parsearGuiaPdf417(codigo)

                                    factura = datos.factura
                                    paciente = datos.paciente
                                    direccion = datos.direccion
                                    comuna = datos.comuna
                                    telefono = datos.telefono

                                    reproducirBeep()

                                    mensaje = "Guía escaneada"
                                    mostrandoScanner = false
                                    flashActivo = false
                                },
                                onError = { error ->
                                    mensaje = mensajeCrearRutaLimpio(error)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { flashActivo = !flashActivo },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (flashActivo) "Flash OFF" else "Flash ON")
                            }

                            OutlinedButton(
                                onClick = {
                                    mostrandoScanner = false
                                    flashActivo = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar")
                            }
                        }
                    }
                }
            }
        }
    }
}