package cl.smt.conductores.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import cl.smt.conductores.components.DireccionDetalleDialog
import cl.smt.conductores.components.RutaMap
import cl.smt.conductores.components.RutaMapaCoordenada
import cl.smt.conductores.components.RutaMapaEntrega
import cl.smt.conductores.components.RutaMapaInicio
import cl.smt.conductores.data.RutaBackendPoint
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.gps.GpsController
import cl.smt.conductores.models.DireccionSmt
import cl.smt.conductores.models.PedidoSmt
import cl.smt.conductores.routing.RutaGeoPoint
import cl.smt.conductores.routing.RutaOptimizer
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@Composable
fun EntregasV2Screen(
    onCrearRutaClick: () -> Unit,
    onSesionExpirada: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = SessionManager.getUser(context)

    val verde = Color(0xFF00C853)
    val texto = Color(0xFFF8FAFC)
    val suave = Color(0xFF9CA3AF)
    val fondoSheet = Color(0xF2020617)
    val fondoCard = Color(0xEE0B1120)
    val borde = Color(0xFF1F2937)
    val naranja = Color(0xFFFF8A00)
    val grisEntrega = Color(0xFF6B7280)
    val rojo = Color(0xFFEF4444)

    val pedidos = remember { mutableStateOf<List<PedidoSmt>>(emptyList()) }
    val direcciones = remember { mutableStateOf<List<DireccionSmt>>(emptyList()) }
    val mensaje = remember { mutableStateOf("") }
    val cargando = remember { mutableStateOf(false) }
    val accionando = remember { mutableStateOf(false) }
    val optimizando = remember { mutableStateOf(false) }
    val gpsActivo = remember { mutableStateOf(GpsController.estaActivo(context)) }

    val entregadasLocales = remember { mutableStateListOf<PedidoSmt>() }
    val problemasLocales = remember { mutableStateListOf<PedidoSmt>() }
    val ordenRutaIds = remember { mutableStateListOf<Int>() }
    val rutaGeometriaOsrm = remember { mutableStateOf<List<RutaMapaCoordenada>>(emptyList()) }
    val rutaOptimizada = remember { mutableStateOf(false) }
    val modoManual = remember { mutableStateOf(false) }
    val sheetNivel = remember { mutableStateOf(RutaSheetNivel.MEDIO) }
    val tab = remember { mutableStateOf("pendientes") }

    val direccionSeleccionada = remember { mutableStateOf<DireccionSmt?>(null) }
    val pedidoDetalle = remember { mutableStateOf<EntregaV2Ui?>(null) }

    val mostrarEntrega = remember { mutableStateOf(false) }
    val mostrarProblema = remember { mutableStateOf(false) }
    val pedidoEntrega = remember { mutableStateOf<PedidoSmt?>(null) }
    val pedidoProblema = remember { mutableStateOf<PedidoSmt?>(null) }
    val temperaturaEntrega = remember { mutableStateOf("") }
    val horaEntrega = remember { mutableStateOf("") }
    val motivoProblema = remember { mutableStateOf("") }
    val fotoEntregaUri = remember { mutableStateOf<Uri?>(null) }
    val fotoEntregaFile = remember { mutableStateOf<File?>(null) }
    val fotoTomada = remember { mutableStateOf(false) }

    val preferenciasOrden = remember {
        context.getSharedPreferences("smt_entregas_v2", 0)
    }
    val claveOrden = "orden_conductor_${user?.id ?: 0}"

    fun cargarPedidos() {
        if (user == null) {
            mensaje.value = "Sesión inválida"
            return
        }

        scope.launch {
            cargando.value = true
            val resPedidos = SmtApi.cargarMisPedidos(user)
            if (resPedidos.ok) {
                pedidos.value = resPedidos.pedidos
            } else {
                mensaje.value = resPedidos.mensaje
            }
            cargando.value = false
        }
    }

    fun cargarDirecciones() {
        if (user == null) return
        scope.launch {
            val res = SmtApi.cargarDirecciones(user)
            if (res.ok) direcciones.value = res.direcciones
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

    fun comprimirFoto(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() <= 0L) return false

            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val maxLado = 2200
            var escala = 1
            while (bounds.outWidth / escala > maxLado || bounds.outHeight / escala > maxLado) {
                escala *= 2
            }

            val bitmapOriginal = BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = escala }
            ) ?: return false

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            val bitmapOrientado = if (!matrix.isIdentity) {
                Bitmap.createBitmap(
                    bitmapOriginal,
                    0,
                    0,
                    bitmapOriginal.width,
                    bitmapOriginal.height,
                    matrix,
                    true
                )
            } else {
                bitmapOriginal
            }

            val bitmapFinal = if (bitmapOrientado.width > maxLado || bitmapOrientado.height > maxLado) {
                val ratio = minOf(
                    maxLado.toFloat() / bitmapOrientado.width.toFloat(),
                    maxLado.toFloat() / bitmapOrientado.height.toFloat()
                )
                Bitmap.createScaledBitmap(
                    bitmapOrientado,
                    (bitmapOrientado.width * ratio).toInt(),
                    (bitmapOrientado.height * ratio).toInt(),
                    true
                )
            } else {
                bitmapOrientado
            }

            FileOutputStream(file, false).use { out ->
                bitmapFinal.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            if (bitmapFinal != bitmapOrientado) bitmapFinal.recycle()
            if (bitmapOrientado != bitmapOriginal) bitmapOrientado.recycle()
            bitmapOriginal.recycle()

            file.exists() && file.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    val launcherFoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        val file = fotoEntregaFile.value
        if (ok && file != null && comprimirFoto(file)) {
            fotoTomada.value = true
            mensaje.value = "Foto lista"
        } else {
            fotoTomada.value = false
            fotoEntregaUri.value = null
            fotoEntregaFile.value = null
            mensaje.value = "Foto cancelada"
        }
    }

    LaunchedEffect(Unit) {
        val guardados = preferenciasOrden.getString(claveOrden, "").orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()
        ordenRutaIds.clear()
        ordenRutaIds.addAll(guardados)
        cargarPedidos()
        cargarDirecciones()
    }

    val sucursalUsuarioCanonica = normalizarSucursal(user?.sucursal.orEmpty())
    val direccionesPorNombre = remember(direcciones.value, sucursalUsuarioCanonica) {
        direcciones.value
            .filter { it.nombre.isNotBlank() }
            .groupBy { normalizarNombreDireccion(it.nombre) }
            .mapValues { (_, candidatas) ->
                candidatas.firstOrNull { direccion ->
                    sucursalUsuarioCanonica.isNotBlank() &&
                            normalizarSucursal(direccion.sucursal) == sucursalUsuarioCanonica
                } ?: candidatas.first()
            }
    }

    fun direccionPara(pedido: PedidoSmt): DireccionSmt? {
        val key = normalizarNombreDireccion(pedido.paciente)
        return direccionesPorNombre[key]
    }

    val idsCompletadosLocales = (entregadasLocales + problemasLocales).map { it.id }.toSet()
    val pedidosActivos = pedidos.value.filter { pedido ->
        pedido.id !in idsCompletadosLocales &&
                (pedido.estado.equals("pendiente", true) || pedido.estado.equals("en_ruta", true))
    }

    LaunchedEffect(pedidosActivos.map { it.id }, entregadasLocales.map { it.id }, problemasLocales.map { it.id }) {
        val idsReales = (ordenRutaIds + pedidosActivos.map { it.id } + entregadasLocales.map { it.id } + problemasLocales.map { it.id })
            .distinct()
            .filter { id ->
                pedidosActivos.any { it.id == id } ||
                        entregadasLocales.any { it.id == id } ||
                        problemasLocales.any { it.id == id }
            }

        if (ordenRutaIds.toList() != idsReales) {
            ordenRutaIds.clear()
            ordenRutaIds.addAll(idsReales)
            preferenciasOrden.edit().putString(claveOrden, idsReales.joinToString(",")).apply()
        }
    }

    fun numeroPedido(id: Int): Int {
        val idx = ordenRutaIds.indexOf(id)
        return if (idx >= 0) idx + 1 else ordenRutaIds.size + 1
    }

    fun itemUi(pedido: PedidoSmt, forzarEstado: String? = null): EntregaV2Ui {
        return EntregaV2Ui(
            numero = numeroPedido(pedido.id),
            pedido = pedido,
            direccion = direccionPara(pedido),
            estadoVisual = forzarEstado ?: pedido.estado
        )
    }

    val pendientesUi = ordenRutaIds.mapNotNull { id -> pedidosActivos.firstOrNull { it.id == id } }
        .ifEmpty { pedidosActivos }
        .map { itemUi(it) }

    val entregadasUi = entregadasLocales.map { itemUi(it, "entregado") }
        .sortedBy { it.numero }

    val problemasUi = problemasLocales.map { itemUi(it, "problema") }
        .sortedBy { it.numero }

    val hayPendientesSinIniciar = pedidosActivos.any { it.estado.equals("pendiente", true) }
    val hayEnRuta = pedidosActivos.any { it.estado.equals("en_ruta", true) }
    val todoProcesado = pedidosActivos.isEmpty() && (entregadasLocales.isNotEmpty() || problemasLocales.isNotEmpty())

    val laboratorioRuta = remember {
        RutaMapaInicio(
            nombre = "Laboratorio",
            lat = -33.47370422204016,
            lng = -70.6274402762916
        )
    }

    fun guardarOrden(ids: List<Int>) {
        val completados = ordenRutaIds.filter { id ->
            entregadasLocales.any { it.id == id } || problemasLocales.any { it.id == id }
        }
        val resto = ids.filterNot { it in completados }
        val final = (completados + resto).distinct()
        ordenRutaIds.clear()
        ordenRutaIds.addAll(final)
        preferenciasOrden.edit().putString(claveOrden, final.joinToString(",")).apply()
    }

    fun optimizarRuta() {
        if (user == null) {
            mensaje.value = "Sesión inválida"
            return
        }

        val puntosBackend = pendientesUi.mapNotNull { item ->
            val direccion = item.direccion ?: return@mapNotNull null
            val lat = direccion.lat ?: return@mapNotNull null
            val lng = direccion.lng ?: return@mapNotNull null
            if (!direccion.tieneCoordenadasValidas()) return@mapNotNull null
            RutaBackendPoint(
                pedidoId = item.pedido.id,
                nombre = item.pedido.paciente,
                lat = lat,
                lng = lng
            )
        }

        if (puntosBackend.size < 2) {
            mensaje.value = "Se necesitan al menos 2 entregas pendientes con coordenadas"
            return
        }

        scope.launch {
            optimizando.value = true
            val backend = SmtApi.optimizarRuta(
                user = user,
                inicioLat = laboratorioRuta.lat,
                inicioLng = laboratorioRuta.lng,
                entregas = puntosBackend
            )

            if (backend.ok && backend.orderedIds.isNotEmpty()) {
                val sinCoords = pendientesUi
                    .filterNot { it.direccion.tieneCoordenadasValidas() }
                    .map { it.pedido.id }
                guardarOrden(backend.orderedIds + sinCoords)
                rutaGeometriaOsrm.value = backend.geometry.map { RutaMapaCoordenada(it.lat, it.lng) }
                rutaOptimizada.value = true

                val km = backend.distanceMeters?.let { String.format(Locale.US, "%.1f", it / 1000.0) }
                val min = backend.durationSeconds?.let { kotlin.math.round(it / 60.0).toInt() }
                mensaje.value = buildString {
                    append("Ruta optimizada")
                    if (km != null) append(": $km km")
                    if (min != null) append(" · $min min")
                }
            } else {
                val local = RutaOptimizer.optimizeShortestPath(
                    points = puntosBackend.map { RutaGeoPoint(it.pedidoId, it.lat, it.lng) },
                    startLat = laboratorioRuta.lat,
                    startLng = laboratorioRuta.lng
                )
                guardarOrden(local.orderedIds)
                rutaGeometriaOsrm.value = emptyList()
                rutaOptimizada.value = true
                mensaje.value = "OSRM no respondió; se aplicó respaldo local"
            }
            optimizando.value = false
        }
    }

    fun recalcularDespuesDeCambio() {
        rutaGeometriaOsrm.value = emptyList()
        rutaOptimizada.value = false
    }

    fun moverPendienteManual(pedidoId: Int, direccion: Int) {
        val actuales = pendientesUi.map { it.pedido.id }.toMutableList()
        val desde = actuales.indexOf(pedidoId)
        if (desde < 0) return

        val hasta = (desde + direccion).coerceIn(0, actuales.lastIndex)
        if (hasta == desde) return

        val movido = actuales.removeAt(desde)
        actuales.add(hasta, movido)
        guardarOrden(actuales)
        recalcularDespuesDeCambio()
    }

    val mapaEntregas = buildList {
        (pendientesUi + entregadasUi + problemasUi).forEach { item ->
            val direccion = item.direccion ?: return@forEach
            val lat = direccion.lat ?: return@forEach
            val lng = direccion.lng ?: return@forEach
            if (!direccion.tieneCoordenadasValidas()) return@forEach
            add(
                RutaMapaEntrega(
                    pedidoId = item.pedido.id,
                    numero = item.numero,
                    nombre = item.pedido.paciente,
                    lat = lat,
                    lng = lng,
                    colorHex = when {
                        item.estadoVisual.equals("entregado", true) -> "#6B7280"
                        item.estadoVisual.equals("problema", true) -> "#EF4444"
                        item.pedido.estado.equals("pendiente", true) -> "#FF8A00"
                        else -> "#00C853"
                    }
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF00140D))
    ) {
        RutaMap(
            entregas = mapaEntregas,
            laboratorio = laboratorioRuta,
            rutaOptimizada = rutaOptimizada.value,
            rutaGeometria = rutaGeometriaOsrm.value,
            modifier = Modifier.fillMaxSize(),
            onEntregaClick = { pedidoId ->
                val item = (pendientesUi + entregadasUi + problemasUi).firstOrNull { it.pedido.id == pedidoId }
                if (item != null) pedidoDetalle.value = item
            }
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 0.dp)
                .padding(top = 58.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniStatusPillV2(
                text = if (rutaOptimizada.value) "Ruta optim." else "Orden actual",
                active = rutaOptimizada.value
            )

            GpsPillV2(
                activo = gpsActivo.value,
                onClick = {
                    if (gpsActivo.value) {
                        if (hayEnRuta) {
                            mensaje.value = "No puedes apagar GPS con pedidos en ruta"
                        } else {
                            GpsController.detener(context)
                            gpsActivo.value = false
                            mensaje.value = "GPS desactivado"
                        }
                    } else {
                        GpsController.iniciar(context)
                        gpsActivo.value = true
                        mensaje.value = "GPS activado"
                    }
                }
            )

            MiniActionPillV2(
                text = if (cargando.value) "..." else "↻",
                onClick = {
                    cargarPedidos()
                    cargarDirecciones()
                }
            )
        }

        RutaSheetV2(
            modifier = Modifier.align(Alignment.BottomCenter),
            nivel = sheetNivel.value,
            onNivelChange = { nuevoNivel ->
                sheetNivel.value = nuevoNivel
            },
            onHandleTap = {
                sheetNivel.value = when (sheetNivel.value) {
                    RutaSheetNivel.MAPA -> RutaSheetNivel.MEDIO
                    RutaSheetNivel.MEDIO -> RutaSheetNivel.FULL
                    RutaSheetNivel.FULL -> RutaSheetNivel.MAPA
                }
            },
            texto = texto,
            suave = suave,
            verde = verde,
            fondoSheet = fondoSheet,
            fondoCard = fondoCard,
            borde = borde,
            tab = tab.value,
            onTab = { tab.value = it },
            pendientes = pendientesUi,
            entregadas = entregadasUi,
            problemas = problemasUi,
            total = pendientesUi.size + entregadasUi.size + problemasUi.size,
            optimizando = optimizando.value,
            iniciandoRuta = accionando.value,
            modoManual = modoManual.value,
            hayPendientesSinIniciar = hayPendientesSinIniciar,
            todoProcesado = todoProcesado,
            onModoManual = { modoManual.value = !modoManual.value },
            onOptimizar = { optimizarRuta() },
            onIniciarRuta = iniciarRuta@{
                if (user == null) {
                    mensaje.value = "Sesión inválida"
                    return@iniciarRuta
                }
                if (!gpsActivo.value) {
                    mensaje.value = "Debes activar GPS para iniciar ruta"
                    return@iniciarRuta
                }
                scope.launch {
                    accionando.value = true
                    val res = SmtApi.iniciarRuta(user)
                    mensaje.value = res.mensaje
                    cargarPedidos()
                    accionando.value = false
                }
            },
            onLimpiar = {
                entregadasLocales.clear()
                problemasLocales.clear()
                ordenRutaIds.clear()
                rutaGeometriaOsrm.value = emptyList()
                rutaOptimizada.value = false
                preferenciasOrden.edit().remove(claveOrden).apply()
                mensaje.value = "Ruta limpiada"
                cargarPedidos()
            },
            onItemClick = { item -> pedidoDetalle.value = item },
            onVerDireccion = { direccion -> direccionSeleccionada.value = direccion },
            onMoverPendiente = { pedidoId, direccion -> moverPendienteManual(pedidoId, direccion) }
        )

        if (mensaje.value.isNotBlank()) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 98.dp, start = 18.dp, end = 18.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xEE020617)),
                border = BorderStroke(1.dp, Color(0x3300C853))
            ) {
                Text(
                    mensaje.value,
                    color = texto,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }

    val detalle = pedidoDetalle.value
    if (detalle != null) {
        EntregaDetalleDialogV2(
            item = detalle,
            puedeCerrar = detalle.pedido.estado.equals("en_ruta", true),
            onDismiss = { pedidoDetalle.value = null },
            onVerDestino = { direccion -> direccionSeleccionada.value = direccion },
            onEntregar = {
                pedidoDetalle.value = null
                pedidoEntrega.value = detalle.pedido
                temperaturaEntrega.value = ""
                horaEntrega.value = ""
                fotoTomada.value = false
                fotoEntregaFile.value = null
                fotoEntregaUri.value = null
                mostrarEntrega.value = true
            },
            onProblema = {
                pedidoDetalle.value = null
                pedidoProblema.value = detalle.pedido
                motivoProblema.value = ""
                mostrarProblema.value = true
            }
        )
    }

    if (mostrarEntrega.value && pedidoEntrega.value != null) {
        AlertDialog(
            onDismissRequest = { if (!accionando.value) mostrarEntrega.value = false },
            title = { Text("Cerrar entrega") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Factura: ${pedidoEntrega.value?.factura.orEmpty()}")
                    OutlinedTextField(
                        value = temperaturaEntrega.value,
                        onValueChange = { temperaturaEntrega.value = it },
                        label = { Text("Temperatura") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = horaEntrega.value,
                        onValueChange = { horaEntrega.value = it },
                        label = { Text("Hora guía") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val (file, uri) = crearArchivoFoto()
                            fotoEntregaFile.value = file
                            fotoEntregaUri.value = uri
                            launcherFoto.launch(uri)
                        }
                    ) {
                        Text(if (fotoTomada.value) "Foto lista ✓" else "Tomar foto")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !accionando.value,
                    onClick = {
                        val pedido = pedidoEntrega.value ?: return@Button
                        val temp = temperaturaEntrega.value.trim()
                        val hora = horaEntrega.value.trim()
                        val foto = fotoEntregaFile.value
                        if (temp.isBlank()) {
                            mensaje.value = "Ingresa temperatura"
                            return@Button
                        }
                        if (foto == null || !foto.exists()) {
                            mensaje.value = "Falta foto"
                            return@Button
                        }
                        if (user == null) {
                            mensaje.value = "Sesión inválida"
                            onSesionExpirada()
                            return@Button
                        }
                        scope.launch {
                            accionando.value = true
                            val res = SmtApi.cerrarEntrega(
                                user = user,
                                postId = pedido.id,
                                temperatura = temp,
                                horaGuia = hora,
                                foto = foto
                            )
                            mensaje.value = res.mensaje
                            if (res.ok) {
                                if (entregadasLocales.none { it.id == pedido.id }) {
                                    entregadasLocales.add(pedido.copy(estado = "entregado"))
                                }
                                mostrarEntrega.value = false
                                pedidoEntrega.value = null
                                recalcularDespuesDeCambio()
                                cargarPedidos()
                            }
                            accionando.value = false
                        }
                    }
                ) {
                    Text(if (accionando.value) "Enviando..." else "Aceptar")
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !accionando.value,
                    onClick = { mostrarEntrega.value = false }
                ) { Text("Cancelar") }
            }
        )
    }

    if (mostrarProblema.value && pedidoProblema.value != null) {
        AlertDialog(
            onDismissRequest = { if (!accionando.value) mostrarProblema.value = false },
            title = { Text("Reportar problema") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Factura: ${pedidoProblema.value?.factura.orEmpty()}")
                    OutlinedTextField(
                        value = motivoProblema.value,
                        onValueChange = { motivoProblema.value = it },
                        label = { Text("Motivo") },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !accionando.value,
                    onClick = {
                        val pedido = pedidoProblema.value ?: return@Button
                        val motivo = motivoProblema.value.trim()
                        if (motivo.isBlank()) {
                            mensaje.value = "Ingresa motivo"
                            return@Button
                        }
                        if (user == null) {
                            mensaje.value = "Sesión inválida"
                            onSesionExpirada()
                            return@Button
                        }
                        scope.launch {
                            accionando.value = true
                            val res = SmtApi.actualizarPedidoEstado(
                                user = user,
                                postId = pedido.id,
                                estado = "problema",
                                motivoProblema = motivo
                            )
                            mensaje.value = res.mensaje
                            if (res.ok) {
                                if (problemasLocales.none { it.id == pedido.id }) {
                                    problemasLocales.add(pedido.copy(estado = "problema", motivoProblema = motivo))
                                }
                                mostrarProblema.value = false
                                pedidoProblema.value = null
                                recalcularDespuesDeCambio()
                                cargarPedidos()
                            }
                            accionando.value = false
                        }
                    }
                ) { Text(if (accionando.value) "Enviando..." else "Reportar") }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !accionando.value,
                    onClick = { mostrarProblema.value = false }
                ) { Text("Cancelar") }
            }
        )
    }

    val direccionDetalle = direccionSeleccionada.value
    if (direccionDetalle != null) {
        DireccionDetalleDialog(
            direccion = direccionDetalle,
            onDismiss = { direccionSeleccionada.value = null },
            onError = { mensaje.value = it }
        )
    }
}


@Composable
private fun MiniStatusPillV2(
    text: String,
    active: Boolean
) {
    Card(
        modifier = Modifier.height(42.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD020617)),
        border = BorderStroke(1.dp, Color(0x55374151))
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 11.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (active) Color(0xFF00E676) else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun GpsPillV2(
    activo: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD020617)),
        border = BorderStroke(1.dp, Color(0x55374151))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(if (activo) Color(0xFF00C853) else Color(0xFFEF4444), CircleShape)
            )
            Text(
                if (activo) "GPS Activo" else "GPS Inactivo",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MiniActionPillV2(
    text: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xDD020617)),
        border = BorderStroke(1.dp, Color(0x55374151))
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun RutaSheetV2(
    modifier: Modifier,
    nivel: RutaSheetNivel,
    onNivelChange: (RutaSheetNivel) -> Unit,
    onHandleTap: () -> Unit,
    texto: Color,
    suave: Color,
    verde: Color,
    fondoSheet: Color,
    fondoCard: Color,
    borde: Color,
    tab: String,
    onTab: (String) -> Unit,
    pendientes: List<EntregaV2Ui>,
    entregadas: List<EntregaV2Ui>,
    problemas: List<EntregaV2Ui>,
    total: Int,
    optimizando: Boolean,
    iniciandoRuta: Boolean,
    modoManual: Boolean,
    hayPendientesSinIniciar: Boolean,
    todoProcesado: Boolean,
    onModoManual: () -> Unit,
    onOptimizar: () -> Unit,
    onIniciarRuta: () -> Unit,
    onLimpiar: () -> Unit,
    onItemClick: (EntregaV2Ui) -> Unit,
    onVerDireccion: (DireccionSmt) -> Unit,
    onMoverPendiente: (Int, Int) -> Unit
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val dragTotal = remember { mutableStateOf(0f) }

    // Tres anclas reales del sheet.
    // FULL no llega hasta el borde superior: deja aire para que el mapa y los controles sigan respirando.
    val altoMapa = 96.dp
    val altoMedio = minOf(330.dp, screenHeight * 0.42f)
    val altoFull = minOf(560.dp, screenHeight * 0.70f)

    val altoObjetivo = when (nivel) {
        RutaSheetNivel.MAPA -> altoMapa
        RutaSheetNivel.MEDIO -> altoMedio
        RutaSheetNivel.FULL -> altoFull
    }

    val altoAnimado by animateDpAsState(
        targetValue = altoObjetivo,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ruta_sheet_height"
    )

    val altoActual = altoAnimado

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(altoActual)
            .pointerInput(nivel) {
                detectVerticalDragGestures(
                    onDragStart = {
                        dragTotal.value = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal.value += dragAmount
                    },
                    onDragEnd = {
                        val arrastre = dragTotal.value

                        val nuevoNivel = when {
                            kotlin.math.abs(arrastre) < 28f -> nivel

                            // En Compose, arrastrar hacia arriba entrega valores negativos.
                            // Si el usuario tira hacia arriba, subimos el sheet un nivel.
                            arrastre < 0f -> when (nivel) {
                                RutaSheetNivel.MAPA -> RutaSheetNivel.MEDIO
                                RutaSheetNivel.MEDIO -> RutaSheetNivel.FULL
                                RutaSheetNivel.FULL -> RutaSheetNivel.FULL
                            }

                            // Si tira hacia abajo, bajamos el sheet un nivel.
                            else -> when (nivel) {
                                RutaSheetNivel.MAPA -> RutaSheetNivel.MAPA
                                RutaSheetNivel.MEDIO -> RutaSheetNivel.MAPA
                                RutaSheetNivel.FULL -> RutaSheetNivel.MEDIO
                            }
                        }

                        onNivelChange(nuevoNivel)
                        dragTotal.value = 0f
                    },
                    onDragCancel = {
                        dragTotal.value = 0f
                    }
                )
            },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        colors = CardDefaults.cardColors(containerColor = fondoSheet),
        border = BorderStroke(1.dp, Color(0x55374151))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 52.dp, height = 5.dp)
                    .background(Color(0xFF6B7280), RoundedCornerShape(99.dp))
                    .clickable { onHandleTap() }
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ruta de hoy", color = texto, fontSize = 17.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (pendientes.isNotEmpty()) "En ruta" else "Completa",
                            color = verde,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .background(Color(0x3300C853), RoundedCornerShape(99.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        "$total entregas · ${pendientes.size} pendientes · ${entregadas.size} entregadas",
                        color = suave,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    "${entregadas.size}/${total.coerceAtLeast(1)}",
                    color = verde,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(Modifier.height(8.dp))

            if (nivel != RutaSheetNivel.MAPA) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TabChipV2("Pendientes", pendientes.size, tab == "pendientes", verde) { onTab("pendientes") }
                    TabChipV2("Entregadas", entregadas.size, tab == "entregadas", verde) { onTab("entregadas") }
                    TabChipV2("Problemas", problemas.size, tab == "problemas", Color(0xFFEF4444)) { onTab("problemas") }
                }

                Spacer(Modifier.height(8.dp))

                val lista = when (tab) {
                    "entregadas" -> entregadas
                    "problemas" -> problemas
                    else -> pendientes
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(lista, key = { _, item -> item.pedido.id }) { _, item ->
                        val puedeMoverManual = modoManual && tab == "pendientes"

                        EntregaRowV2(
                            item = item,
                            texto = texto,
                            suave = suave,
                            fondoCard = fondoCard,
                            borderColor = when {
                                item.estadoVisual.equals("entregado", true) -> Color(0xFF374151)
                                item.estadoVisual.equals("problema", true) -> Color(0xFF7F1D1D)
                                item.pedido.estado.equals("pendiente", true) -> Color(0xFF4A3412)
                                else -> borde
                            },
                            markerColor = when {
                                item.estadoVisual.equals("entregado", true) -> Color(0xFF6B7280)
                                item.estadoVisual.equals("problema", true) -> Color(0xFFEF4444)
                                item.pedido.estado.equals("pendiente", true) -> Color(0xFFFF8A00)
                                else -> verde
                            },
                            modoManual = puedeMoverManual,
                            onMoveDrag = { direccion -> onMoverPendiente(item.pedido.id, direccion) },
                            onClick = { onItemClick(item) },
                            onVerDireccion = onVerDireccion
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (todoProcesado) {
                Button(
                    onClick = onLimpiar,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = verde)
                ) {
                    Text("Limpiar ruta", color = Color.White, fontWeight = FontWeight.Black)
                }
            } else {
                if (hayPendientesSinIniciar && nivel != RutaSheetNivel.MAPA) {
                    Button(
                        onClick = onIniciarRuta,
                        enabled = !iniciandoRuta,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0EA5E9),
                            disabledContainerColor = Color(0xFF075985)
                        )
                    ) {
                        if (iniciandoRuta) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Iniciando ruta...", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        } else {
                            Text("Iniciar ruta", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onModoManual,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, verde)
                    ) {
                        Text(
                            if (modoManual) "Orden manual" else "Orden optimizado",
                            color = verde,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onOptimizar,
                        enabled = !optimizando,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde)
                    ) {
                        if (optimizando) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                "Optimizar ruta",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabChipV2(
    label: String,
    count: Int,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(99.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color else Color(0x22000000)
        ),
        border = BorderStroke(1.dp, if (selected) color else Color(0x55374151))
    ) {
        Text(
            "$label  $count",
            color = if (selected) Color.White else Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun EntregaRowV2(
    item: EntregaV2Ui,
    texto: Color,
    suave: Color,
    fondoCard: Color,
    borderColor: Color,
    markerColor: Color,
    modoManual: Boolean = false,
    onMoveDrag: (Int) -> Unit = {},
    onClick: () -> Unit,
    onVerDireccion: (DireccionSmt) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = fondoCard),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (modoManual) {
                val dragAcumulado = remember(item.pedido.id) { mutableStateOf(0f) }
                Box(
                    modifier = Modifier
                        .size(width = 24.dp, height = 34.dp)
                        .pointerInput(item.pedido.id) {
                            detectVerticalDragGestures(
                                onDragStart = { dragAcumulado.value = 0f },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    dragAcumulado.value += dragAmount

                                    val umbral = 46f
                                    while (dragAcumulado.value > umbral) {
                                        onMoveDrag(1)
                                        dragAcumulado.value -= umbral
                                    }
                                    while (dragAcumulado.value < -umbral) {
                                        onMoveDrag(-1)
                                        dragAcumulado.value += umbral
                                    }
                                },
                                onDragEnd = { dragAcumulado.value = 0f },
                                onDragCancel = { dragAcumulado.value = 0f }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("≡", color = suave, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(markerColor, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(item.numero.toString(), color = Color.White, fontWeight = FontWeight.Black)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.pedido.paciente.ifBlank { "Sin paciente" },
                    color = texto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text("Factura ${item.pedido.factura}", color = suave, fontSize = 12.sp)
            }

            if (item.direccion.tieneCoordenadasValidas()) {
                Text(
                    "📍",
                    fontSize = 20.sp,
                    modifier = Modifier.clickable { item.direccion?.let(onVerDireccion) }
                )
            } else {
                Text("Sin ubicación", color = suave, fontSize = 11.sp)
            }

            Text("›", color = suave, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun EntregaDetalleDialogV2(
    item: EntregaV2Ui,
    puedeCerrar: Boolean,
    onDismiss: () -> Unit,
    onVerDestino: (DireccionSmt) -> Unit,
    onEntregar: () -> Unit,
    onProblema: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(item.pedido.paciente.ifBlank { "Detalle" })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Factura: ${item.pedido.factura}")
                Text("Estado: ${item.estadoVisual}")
                Text("Orden: ${item.numero}")

                if (item.direccion.tieneCoordenadasValidas()) {
                    Button(onClick = { item.direccion?.let(onVerDestino) }) {
                        Text("Ver destino / Waze / Maps")
                    }
                } else {
                    Text("Este pedido no tiene coordenadas vinculadas")
                }

                if (!puedeCerrar && item.estadoVisual.equals("pendiente", true)) {
                    Text("Inicia la ruta para habilitar Entregar / Problema")
                }
            }
        },
        confirmButton = {
            if (puedeCerrar) {
                Button(onClick = onEntregar) { Text("Entregar") }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = {
            if (puedeCerrar) {
                OutlinedButton(onClick = onProblema) { Text("Problema") }
            }
        }
    )
}

private enum class RutaSheetNivel { MAPA, MEDIO, FULL }

private data class EntregaV2Ui(
    val numero: Int,
    val pedido: PedidoSmt,
    val direccion: DireccionSmt?,
    val estadoVisual: String
)

private fun DireccionSmt?.tieneCoordenadasValidas(): Boolean {
    val latitud = this?.lat ?: return false
    val longitud = this.lng ?: return false
    return latitud in -90.0..90.0 && longitud in -180.0..180.0
}

private fun normalizarNombreDireccion(valor: String): String {
    return valor
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}

private fun normalizarSucursal(valor: String): String {
    return valor
        .trim()
        .lowercase(Locale.ROOT)
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(Regex("\\s+"), " ")
}
