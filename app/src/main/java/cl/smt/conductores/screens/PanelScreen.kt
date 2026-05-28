package cl.smt.conductores.screens

import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.gps.GpsController
import cl.smt.conductores.models.EntregaPendiente
import cl.smt.conductores.models.PedidoSmt
import cl.smt.conductores.storage.ColaEntregas
import cl.smt.conductores.storage.WorkerEnvio
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PanelScreen(
    onCrearRutaClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onHistorialClick: () -> Unit = {},
    onCerrarSesionClick: () -> Unit = {},
    onSesionExpirada: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = SessionManager.getUser(context)

    val pedidos = remember { mutableStateOf<List<PedidoSmt>>(emptyList()) }
    val entregasLocales = remember { mutableStateOf(ColaEntregas.obtenerEntregas(context)) }
    val cargando = remember { mutableStateOf(false) }
    val accionando = remember { mutableStateOf(false) }
    val mensaje = remember { mutableStateOf("") }
    val gpsActivo = remember { mutableStateOf(GpsController.estaActivo(context)) }

    val hayPendientes = pedidos.value.any {
        it.estado.equals("pendiente", true)
    }

    val mostrarMenu = remember { mutableStateOf(false) }
    val mostrarEntrega = remember { mutableStateOf(false) }
    val pedidoEntrega = remember { mutableStateOf<PedidoSmt?>(null) }
    val temperaturaEntrega = remember { mutableStateOf("") }
    val horaEntrega = remember { mutableStateOf("") }
    val fotoEntregaUri = remember { mutableStateOf<Uri?>(null) }
    val fotoEntregaFile = remember { mutableStateOf<File?>(null) }
    val fotoTomada = remember { mutableStateOf(false) }

    val verde = Color(0xFF00C853)
    val texto = Color(0xFFF8FAFC)
    val suave = Color(0xFF9CA3AF)
    val fondoCard = Color(0xEE0B1120)
    val borde = Color(0xFF123D2B)

    fun refrescarColaLocal() {
        entregasLocales.value = ColaEntregas.obtenerEntregas(context)
    }

    fun cargarPedidos() {
        if (user == null) {
            mensaje.value = "Sesión inválida"
            return
        }

        refrescarColaLocal()

        scope.launch {
            cargando.value = true
            mensaje.value = ""

            val res = SmtApi.cargarMisPedidos(user)

            cargando.value = false

            if (res.ok) {
                pedidos.value = res.pedidos
            } else {
                mensaje.value = res.mensaje
            }
        }
    }

    fun crearArchivoFoto(): Pair<File, Uri> {
        val file = File(context.cacheDir, "entrega_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "cl.smt.conductores.fileprovider",
            file
        )

        return file to uri
    }

    val tomarFotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            fotoTomada.value = true
        } else {
            fotoTomada.value = false
            fotoEntregaUri.value = null
            fotoEntregaFile.value = null
            mensaje.value = "Foto cancelada"
        }
    }

    LaunchedEffect(Unit) {
        cargarPedidos()
    }

    val pedidosVisibles = pedidos.value.filter { pedido ->
        val estaEnColaLocal = entregasLocales.value.any {
            it.postId == pedido.id
        }

        !estaEnColaLocal &&
                (
                        pedido.estado.equals("pendiente", true) ||
                                pedido.estado.equals("en_ruta", true)
                        )
    }

    Box(
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 42.dp, bottom = 120.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "Hola ${
                            user?.name
                                ?.split(" ")
                                ?.firstOrNull()
                                .orEmpty()
                                .ifBlank { "Conductor" }
                        } 👋",
                        color = texto,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )

                    Text(
                        "Panel conductor SMT",
                        color = suave,
                        fontSize = 14.sp
                    )
                }

                Box {
                    IconButton(
                        onClick = { mostrarMenu.value = true },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Text(
                            "☰",
                            color = texto,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    DropdownMenu(
                        expanded = mostrarMenu.value,
                        onDismissRequest = { mostrarMenu.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Crear ruta") },
                            onClick = {
                                mostrarMenu.value = false
                                onCrearRutaClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Historial") },
                            onClick = {
                                mostrarMenu.value = false
                                onHistorialClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Perfil") },
                            onClick = {
                                mostrarMenu.value = false
                                onPerfilClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Actualizar") },
                            onClick = {
                                mostrarMenu.value = false
                                cargarPedidos()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Cerrar sesión") },
                            onClick = {
                                mostrarMenu.value = false
                                onCerrarSesionClick()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = fondoCard),
                border = BorderStroke(1.dp, borde)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "GPS",
                            color = texto,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            if (gpsActivo.value) "Activo" else "Apagado",
                            color = if (gpsActivo.value) verde else Color(0xFFF87171),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Switch(
                        checked = gpsActivo.value,
                        onCheckedChange = { activo ->
                            if (activo) {
                                gpsActivo.value = GpsController.iniciar(context)
                                mensaje.value = if (gpsActivo.value) {
                                    "GPS activado"
                                } else {
                                    "GPS no configurado"
                                }
                            } else {
                                GpsController.detener(context)
                                gpsActivo.value = false
                                mensaje.value = "GPS desactivado"
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1E3A8A),
                            checkedTrackColor = Color(0xFFAEC5FF)
                        )
                    )
                }
            }

            if (hayPendientes) {
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (user == null) {
                            mensaje.value = "Sesión inválida"
                            return@Button
                        }

                        scope.launch {
                            accionando.value = true
                            mensaje.value = "Iniciando ruta..."

                            val res = SmtApi.iniciarRuta(user)

                            mensaje.value = res.mensaje

                            if (res.ok) {
                                val nuevos = SmtApi.cargarMisPedidos(user)

                                if (nuevos.ok) {
                                    pedidos.value = nuevos.pedidos
                                }

                                if (!gpsActivo.value) {
                                    gpsActivo.value = GpsController.iniciar(context)
                                }
                            }

                            accionando.value = false
                        }
                    },
                    enabled = !accionando.value && !cargando.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (accionando.value) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            "Iniciar ruta",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            OutlinedButton(
                onClick = { cargarPedidos() },
                enabled = !cargando.value && !accionando.value,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(
                    if (cargando.value) "Cargando..." else "Actualizar pedidos",
                    fontWeight = FontWeight.Bold
                )
            }

            if (mensaje.value.isNotBlank()) {
                Spacer(Modifier.height(12.dp))

                Text(
                    mensaje.value,
                    color = if (
                        mensaje.value.contains("guardada", true) ||
                        mensaje.value.contains("activado", true) ||
                        mensaje.value.contains("iniciada", true) ||
                        mensaje.value.contains("iniciado", true)
                    ) {
                        verde
                    } else {
                        Color(0xFFF87171)
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                "Tus pedidos (${pedidosVisibles.size})",
                color = texto,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(14.dp))

            if (cargando.value && pedidos.value.isEmpty()) {
                CircularProgressIndicator(color = verde)
            } else if (pedidosVisibles.isEmpty()) {
                Text(
                    "No tienes pedidos asignados",
                    color = suave
                )
            } else {
                pedidosVisibles.forEach { pedido ->
                    PedidoCardPanel(
                        pedido = pedido,
                        mostrarAcciones = pedido.estado.equals("en_ruta", true),
                        onEntregar = {
                            pedidoEntrega.value = pedido
                            temperaturaEntrega.value = ""
                            horaEntrega.value = ""
                            fotoEntregaUri.value = null
                            fotoEntregaFile.value = null
                            fotoTomada.value = false
                            mostrarEntrega.value = true
                        },
                        onProblema = {
                            mensaje.value = "Problema: pendiente de reconstruir"
                        }
                    )

                    Spacer(Modifier.height(14.dp))
                }
            }
        }

        if (mostrarEntrega.value && pedidoEntrega.value != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!accionando.value) {
                        mostrarEntrega.value = false
                    }
                },
                title = {
                    Text("Cerrar entrega")
                },
                text = {
                    Column {
                        Text("Factura: ${pedidoEntrega.value?.factura ?: ""}")

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = temperaturaEntrega.value,
                            onValueChange = { input ->
                                temperaturaEntrega.value = input
                                    .replace(",", ".")
                                    .filter { it.isDigit() || it == '.' }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            label = { Text("Temperatura") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(14.dp))

                        OutlinedTextField(
                            value = horaEntrega.value,
                            onValueChange = { input ->
                                var limpio = input.filter {
                                    it.isDigit() || it == ':'
                                }

                                if (limpio.length == 2 && !limpio.contains(":")) {
                                    limpio += ":"
                                }

                                if (limpio.length > 5) {
                                    limpio = limpio.substring(0, 5)
                                }

                                horaEntrega.value = limpio
                            },
                            label = { Text("Hora guía (HH:MM)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(Modifier.height(14.dp))

                        Button(
                            onClick = {
                                val (file, uri) = crearArchivoFoto()

                                fotoTomada.value = false
                                fotoEntregaFile.value = file
                                fotoEntregaUri.value = uri

                                tomarFotoLauncher.launch(uri)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !accionando.value
                        ) {
                            Text(
                                if (fotoEntregaUri.value == null) {
                                    "Tomar foto"
                                } else {
                                    "Volver a tomar foto"
                                }
                            )
                        }

                        if (fotoTomada.value && fotoEntregaFile.value != null) {
                            Spacer(Modifier.height(14.dp))

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        ImageView(ctx).apply {
                                            scaleType = ImageView.ScaleType.CENTER_CROP
                                        }
                                    },
                                    update = { imageView ->
                                        fotoEntregaUri.value?.let {
                                            imageView.setImageURI(it)
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pedido = pedidoEntrega.value ?: return@Button
                            val foto = fotoEntregaFile.value

                            if (temperaturaEntrega.value.toDoubleOrNull() == null) {
                                mensaje.value = "Temperatura inválida"
                                return@Button
                            }

                            if (!Regex("^\\d{2}:\\d{2}$").matches(horaEntrega.value.trim())) {
                                mensaje.value = "Hora inválida"
                                return@Button
                            }

                            if (foto == null || !foto.exists()) {
                                mensaje.value = "Debes tomar una foto"
                                return@Button
                            }

                            val entrega = EntregaPendiente(
                                postId = pedido.id,
                                factura = pedido.factura,
                                temperatura = temperaturaEntrega.value.trim(),
                                horaGuia = horaEntrega.value.trim(),
                                fotoPath = foto.absolutePath
                            )

                            ColaEntregas.guardarEntrega(context, entrega)

                            WorkerEnvio.procesarCola(context) { ok, msg ->
                                scope.launch {
                                    mensaje.value = msg

                                    if (ok && user != null) {
                                        val nuevos = SmtApi.cargarMisPedidos(user)

                                        if (nuevos.ok) {
                                            pedidos.value = nuevos.pedidos
                                        }

                                        refrescarColaLocal()
                                    }
                                }
                            }

                            refrescarColaLocal()

                            mostrarEntrega.value = false
                            mensaje.value = "Entrega guardada. Se enviará automáticamente."
                        },
                        enabled = !accionando.value
                    ) {
                        Text("Aceptar entrega")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            mostrarEntrega.value = false
                        },
                        enabled = !accionando.value
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

@Composable
fun PedidoCardPanel(
    pedido: PedidoSmt,
    mostrarAcciones: Boolean,
    onEntregar: () -> Unit,
    onProblema: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xEE0B1120)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Text(
                "Factura ${pedido.factura}",
                color = Color(0xFFF8FAFC),
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )

            Spacer(Modifier.height(8.dp))

            Text(
                pedido.paciente.ifBlank { "Sin paciente" },
                color = Color(0xFFF8FAFC)
            )

            Text(
                pedido.direccion.ifBlank {
                    pedido.comuna.ifBlank { "Sin dirección" }
                },
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp
            )

            Text(
                "Estado: ${pedido.estado}",
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp
            )

            if (mostrarAcciones) {
                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onEntregar,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00C853)
                        )
                    ) {
                        Text(
                            "Entregar",
                            fontWeight = FontWeight.Black
                        )
                    }

                    Button(
                        onClick = onProblema,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text(
                            "Problema",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}