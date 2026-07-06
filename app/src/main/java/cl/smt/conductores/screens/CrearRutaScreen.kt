package cl.smt.conductores.screens

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.smt.conductores.components.Camera2BarcodeScanner
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.models.DireccionSmt
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch
import java.text.Normalizer

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
        factura = partes.getOrNull(2).orEmpty(),
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

fun normalizarBusquedaPaciente(valor: String): String {
    return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
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

    var mostrarPreguntaVinculacion by remember { mutableStateOf(false) }
    var mostrarSelectorPaciente by remember { mutableStateOf(false) }
    var nombreOriginalPendiente by remember { mutableStateOf("") }

    var direccionesDisponibles by remember { mutableStateOf<List<DireccionSmt>>(emptyList()) }
    var direccionSeleccionada by remember { mutableStateOf<DireccionSmt?>(null) }
    var busquedaPaciente by remember { mutableStateOf("") }
    var cargandoDirecciones by remember { mutableStateOf(false) }
    var errorDirecciones by remember { mutableStateOf("") }

    fun limpiarFormulario() {
        codigoEscaneado = ""
        factura = ""
        paciente = ""
        direccion = ""
        comuna = ""
        telefono = ""
        nombreOriginalPendiente = ""
        direccionSeleccionada = null
        busquedaPaciente = ""
    }

    fun enviarPedido(nombrePacienteFinal: String) {
        val usuarioActual = user

        if (usuarioActual == null) {
            mensaje = "Sesión inválida"
            return
        }

        scope.launch {
            cargando = true
            mensaje = ""

            val res = SmtApi.crearPedido(
                user = usuarioActual,
                factura = factura,
                paciente = nombrePacienteFinal,
                direccion = direccion,
                comuna = comuna,
                telefono = telefono,
                tipoEnvio = tipoEnvio,
                patente = patente
            )

            cargando = false
            mensaje = mensajeCrearRutaLimpio(res.mensaje)

            if (res.ok) {
                limpiarFormulario()
            } else {
                paciente = nombrePacienteFinal
            }
        }
    }

    fun abrirSelectorPacientes() {
        val usuarioActual = user

        if (usuarioActual == null) {
            mensaje = "Sesión inválida"
            return
        }

        mostrarPreguntaVinculacion = false
        mostrarSelectorPaciente = true
        busquedaPaciente = ""
        direccionSeleccionada = null
        errorDirecciones = ""

        scope.launch {
            cargandoDirecciones = true

            val res = SmtApi.cargarDirecciones(usuarioActual)

            cargandoDirecciones = false

            if (res.ok) {
                direccionesDisponibles = res.direcciones
            } else {
                direccionesDisponibles = emptyList()
                errorDirecciones = res.mensaje
            }
        }
    }

    fun continuarSinVincular() {
        val nombreOriginal = nombreOriginalPendiente.ifBlank { paciente }

        mostrarPreguntaVinculacion = false
        mostrarSelectorPaciente = false
        enviarPedido(nombreOriginal)
    }

    fun crearPedidoDesdeFormulario() {
        val usuarioActual = user

        if (usuarioActual == null) {
            mensaje = "Sesión inválida"
            return
        }

        if (factura.isBlank() || direccion.isBlank() || patente.isBlank()) {
            mensaje = "Faltan datos"
            return
        }

        scope.launch {
            cargando = true
            mensaje = "Verificando guía..."

            val verificacion = SmtApi.verificarNombreVinculacion(
                user = usuarioActual,
                nombre = paciente
            )

            cargando = false

            if (verificacion.ok && verificacion.requiereVinculacion) {
                nombreOriginalPendiente = paciente.trim()
                mensaje = ""
                mostrarPreguntaVinculacion = true
            } else {
                // La verificación nunca debe bloquear la operación.
                // Si el endpoint falla, el pedido se crea normalmente.
                enviarPedido(paciente.trim())
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

        if (mostrarPreguntaVinculacion) {
            AlertDialog(
                onDismissRequest = {
                    mostrarPreguntaVinculacion = false
                },
                title = {
                    Text(
                        text = "Vincular paciente",
                        fontWeight = FontWeight.Black
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Se detectó que la guía contiene una dirección o ambulatorio erróneos."
                        )

                        Text(
                            text = "¿Desea vincularlo a un paciente?",
                            fontWeight = FontWeight.Bold
                        )

                        if (nombreOriginalPendiente.isNotBlank()) {
                            Text(
                                text = "Nombre detectado: $nombreOriginalPendiente",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            abrirSelectorPacientes()
                        }
                    ) {
                        Text("Sí")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            continuarSinVincular()
                        }
                    ) {
                        Text("No")
                    }
                }
            )
        }

        if (mostrarSelectorPaciente) {
            val busquedaNormalizada = normalizarBusquedaPaciente(busquedaPaciente)

            val direccionesFiltradas = direccionesDisponibles.filter { item ->
                busquedaNormalizada.isBlank() ||
                        normalizarBusquedaPaciente(item.nombre)
                            .contains(busquedaNormalizada)
            }

            AlertDialog(
                onDismissRequest = {
                    mostrarSelectorPaciente = false
                },
                title = {
                    Text(
                        text = "Seleccionar paciente",
                        fontWeight = FontWeight.Black
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = busquedaPaciente,
                            onValueChange = {
                                busquedaPaciente = it
                                direccionSeleccionada = null
                            },
                            label = { Text("Buscar paciente") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        when {
                            cargandoDirecciones -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }

                            errorDirecciones.isNotBlank() -> {
                                Text(
                                    text = errorDirecciones,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            direccionesFiltradas.isEmpty() -> {
                                Text(
                                    text = "No se encontraron pacientes en Direcciones.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 350.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(direccionesFiltradas) { item ->
                                        val seleccionado = direccionSeleccionada?.id == item.id

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    direccionSeleccionada = item
                                                },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (seleccionado) {
                                                    Color(0xFF123D2A)
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                }
                                            ),
                                            border = BorderStroke(
                                                width = if (seleccionado) 2.dp else 1.dp,
                                                color = if (seleccionado) {
                                                    Color(0xFF00C853)
                                                } else {
                                                    MaterialTheme.colorScheme.outlineVariant
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = seleccionado,
                                                    onClick = {
                                                        direccionSeleccionada = item
                                                    }
                                                )

                                                Spacer(Modifier.width(8.dp))

                                                Text(
                                                    text = item.nombre.ifBlank { "Sin nombre" },
                                                    modifier = Modifier.weight(1f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val seleccion = direccionSeleccionada

                            if (seleccion != null) {
                                paciente = seleccion.nombre
                                mostrarSelectorPaciente = false
                                enviarPedido(seleccion.nombre)
                            }
                        },
                        enabled = direccionSeleccionada != null && !cargandoDirecciones
                    ) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            continuarSinVincular()
                        },
                        enabled = !cargandoDirecciones
                    ) {
                        Text("No vincular a un paciente")
                    }
                }
            )
        }

    }
}