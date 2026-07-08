package cl.smt.conductores.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import cl.smt.conductores.components.DireccionDetalleDialog
import cl.smt.conductores.components.RutaMap
import cl.smt.conductores.components.RutaMapaEntrega
import cl.smt.conductores.components.RutaMapaInicio
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.gps.GpsController
import cl.smt.conductores.models.DireccionSmt
import cl.smt.conductores.models.EntregaPendiente
import cl.smt.conductores.models.PedidoSmt
import cl.smt.conductores.storage.ColaEntregas
import cl.smt.conductores.storage.WorkerEnvio
import cl.smt.conductores.routing.RutaGeoPoint
import cl.smt.conductores.routing.RutaOptimizer
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@Composable
fun PanelScreen(
    onCrearRutaClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onHistorialClick: () -> Unit = {},
    onDireccionesClick: () -> Unit = {},
    onCerrarSesionClick: () -> Unit = {},
    onSesionExpirada: () -> Unit = {}
){
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = SessionManager.getUser(context)

    val mostrarAvisoGps = remember { mutableStateOf(false) }
    val pedidos = remember { mutableStateOf<List<PedidoSmt>>(emptyList()) }
    val direcciones = remember { mutableStateOf<List<DireccionSmt>>(emptyList()) }
    val direccionSeleccionada = remember { mutableStateOf<DireccionSmt?>(null) }
    val entregasLocales = remember { mutableStateOf(ColaEntregas.obtenerEntregas(context)) }
    val cargando = remember { mutableStateOf(false) }
    val accionando = remember { mutableStateOf(false) }
    val mensaje = remember { mutableStateOf("") }
    val gpsActivo = remember { mutableStateOf(GpsController.estaActivo(context)) }

    // Orden de la ruta guardado localmente en este teléfono.
    // No modifica el pedido ni crea una vinculación permanente en el backend.
    val modoOrdenManual = remember { mutableStateOf(true) }
    val rutaOptimizadaAplicada = remember { mutableStateOf(false) }
    val ordenManualIds = remember { mutableStateListOf<Int>() }
    val preferenciasOrden = remember {
        context.getSharedPreferences("smt_orden_ruta", 0)
    }
    val claveOrden = "conductor_${user?.id ?: 0}"
    val ordenLocalCargado = remember { mutableStateOf(false) }
    val ultimaFirmaPedidosRuta = remember { mutableStateOf("") }

    // Estado del drag & drop real de la lista de entregas.
    val listaRutaState = rememberLazyListState()
    val pedidoArrastradoId = remember { mutableStateOf<Int?>(null) }
    val desplazamientoArrastreY = remember { mutableStateOf(0f) }

    val hayPendientes = pedidos.value.any {
        it.estado.equals("pendiente", true)
    }

    val hayPedidosEnRuta = pedidos.value.any {
        it.estado.equals("en_ruta", true)
    }

    val mostrarMenu = remember { mutableStateOf(false) }
    val mostrarEntrega = remember { mutableStateOf(false) }
    val mostrarProblema = remember { mutableStateOf(false) }
    val pedidoProblema = remember { mutableStateOf<PedidoSmt?>(null) }
    val motivoProblema = remember { mutableStateOf("") }
    val pedidoEntrega = remember { mutableStateOf<PedidoSmt?>(null) }
    val pedidoDetalle = remember { mutableStateOf<Pair<PedidoSmt, DireccionSmt?>?>(null) }
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

    fun cargarDirecciones() {
        if (user == null) return

        scope.launch {
            val res = SmtApi.cargarDirecciones(user)

            if (res.ok) {
                direcciones.value = res.direcciones
            }
        }
    }

    fun crearArchivoFoto(): Pair<File, Uri> {
        val file = File(
            context.cacheDir,
            "entrega_${System.currentTimeMillis()}_${(1000..9999).random()}.jpg"
        )
        val uri = FileProvider.getUriForFile(
            context,
            "cl.smt.conductores.fileprovider",
            file
        )

        return file to uri
    }

    fun comprimirFotoParaEntrega(file: File): Boolean {
        return try {
            if (!file.exists() || file.length() <= 0L) return false

            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val opcionesBounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeFile(file.absolutePath, opcionesBounds)

            var escala = 1
            val maxLado = 2200

            while (
                opcionesBounds.outWidth / escala > maxLado ||
                opcionesBounds.outHeight / escala > maxLado
            ) {
                escala *= 2
            }

            val opcionesDecode = BitmapFactory.Options().apply {
                inSampleSize = escala
            }

            val bitmapOriginal = BitmapFactory.decodeFile(file.absolutePath, opcionesDecode)
                ?: return false

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

            val bitmapFinal = if (
                bitmapOrientado.width > maxLado ||
                bitmapOrientado.height > maxLado
            ) {
                val ratio = minOf(
                    maxLado.toFloat() / bitmapOrientado.width.toFloat(),
                    maxLado.toFloat() / bitmapOrientado.height.toFloat()
                )

                val nuevoAncho = (bitmapOrientado.width * ratio).toInt()
                val nuevoAlto = (bitmapOrientado.height * ratio).toInt()

                Bitmap.createScaledBitmap(bitmapOrientado, nuevoAncho, nuevoAlto, true)
            } else {
                bitmapOrientado
            }

            FileOutputStream(file, false).use { out ->
                bitmapFinal.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            if (bitmapFinal != bitmapOrientado) {
                bitmapFinal.recycle()
            }

            if (bitmapOrientado != bitmapOriginal) {
                bitmapOrientado.recycle()
            }

            bitmapOriginal.recycle()

            file.exists() && file.length() > 0L
        } catch (e: Exception) {
            false
        }
    }

    val tomarFotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            val file = fotoEntregaFile.value

            if (file != null && comprimirFotoParaEntrega(file)) {
                fotoTomada.value = true
                mensaje.value = "Foto lista (${file.length() / 1024} KB)"
            } else {
                fotoTomada.value = false
                fotoEntregaUri.value = null
                fotoEntregaFile.value = null
                mensaje.value = "No se pudo procesar la foto"
            }
        } else {
            fotoTomada.value = false
            fotoEntregaUri.value = null
            fotoEntregaFile.value = null
            mensaje.value = "Foto cancelada"
        }
    }

    LaunchedEffect(Unit) {
        cargarPedidos()
        cargarDirecciones()
    }

    LaunchedEffect(claveOrden) {
        val idsGuardados = preferenciasOrden
            .getString(claveOrden, "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()

        ordenManualIds.clear()
        ordenManualIds.addAll(idsGuardados)
        ordenLocalCargado.value = true
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

    val sucursalUsuarioCanonica = remember(user?.sucursal) {
        normalizarSucursal(user?.sucursal.orEmpty())
    }

    /*
     * La relación pedido -> destino se recalcula al cargar la pantalla.
     * Nunca copiamos coordenadas desde la dirección textual de la guía.
     * Si un nombre existe en ambas sucursales, se prioriza la del conductor.
     */
    val direccionesPorNombre = remember(
        direcciones.value,
        sucursalUsuarioCanonica
    ) {
        direcciones.value
            .filter { it.nombre.isNotBlank() }
            .groupBy { direccion ->
                normalizarNombreDireccion(direccion.nombre)
            }
            .mapValues { (_, candidatas) ->
                candidatas.firstOrNull { direccion ->
                    val sucursalDireccion = normalizarSucursal(direccion.sucursal)
                    sucursalUsuarioCanonica.isNotBlank() &&
                            sucursalDireccion == sucursalUsuarioCanonica
                } ?: candidatas.first()
            }
    }


    val entregasRuta = pedidosVisibles.mapIndexed { index, pedido ->
        val direccionVinculada = normalizarNombreDireccion(pedido.paciente)
            .takeIf { it.isNotBlank() }
            ?.let { direccionesPorNombre[it] }

        PedidoRutaUi(
            numero = index + 1,
            pedido = pedido,
            direccion = direccionVinculada
        )
    }

    val entregasSinIniciar = entregasRuta
        .filter { item -> item.pedido.estado.equals("pendiente", true) }
        .mapIndexed { index, item -> item.copy(numero = index + 1) }

    val entregasEnRutaBase = entregasRuta.filter { item ->
        item.pedido.estado.equals("en_ruta", true)
    }

    val idsRutaActual = entregasEnRutaBase.map { it.pedido.id }

    LaunchedEffect(idsRutaActual, ordenLocalCargado.value) {
        if (!ordenLocalCargado.value) return@LaunchedEffect

        val firmaActual = idsRutaActual.sorted().joinToString(",")
        if (
            ultimaFirmaPedidosRuta.value.isNotBlank() &&
            ultimaFirmaPedidosRuta.value != firmaActual
        ) {
            rutaOptimizadaAplicada.value = false
        }
        ultimaFirmaPedidosRuta.value = firmaActual

        val idsValidosGuardados = ordenManualIds.filter { it in idsRutaActual }
        val idsNuevos = idsRutaActual.filterNot { it in idsValidosGuardados }
        val ordenActualizado = idsValidosGuardados + idsNuevos

        if (ordenManualIds.toList() != ordenActualizado) {
            ordenManualIds.clear()
            ordenManualIds.addAll(ordenActualizado)
        }

        preferenciasOrden.edit()
            .putString(claveOrden, ordenActualizado.joinToString(","))
            .apply()
    }

    val idsOrdenados = if (ordenManualIds.isEmpty()) {
        idsRutaActual
    } else {
        ordenManualIds.filter { it in idsRutaActual } +
                idsRutaActual.filterNot { it in ordenManualIds }
    }

    val entregasEnRuta = idsOrdenados
        .mapNotNull { id ->
            entregasEnRutaBase.firstOrNull { it.pedido.id == id }
        }
        .mapIndexed { index, item -> item.copy(numero = index + 1) }

    fun moverEntregaManualAIndice(pedidoId: Int, nuevoIndiceSolicitado: Int): Boolean {
        if (!modoOrdenManual.value) return false

        if (ordenManualIds.isEmpty()) {
            ordenManualIds.addAll(idsRutaActual)
        }

        val indiceActual = ordenManualIds.indexOf(pedidoId)
        if (indiceActual == -1 || ordenManualIds.isEmpty()) return false

        val nuevoIndice = nuevoIndiceSolicitado.coerceIn(0, ordenManualIds.lastIndex)
        if (nuevoIndice == indiceActual) return false

        val idMovido = ordenManualIds.removeAt(indiceActual)
        ordenManualIds.add(nuevoIndice, idMovido)

        preferenciasOrden.edit()
            .putString(claveOrden, ordenManualIds.joinToString(","))
            .apply()

        rutaOptimizadaAplicada.value = false
        return true
    }

    fun comenzarArrastre(pedidoId: Int) {
        if (!modoOrdenManual.value) return
        pedidoArrastradoId.value = pedidoId
        desplazamientoArrastreY.value = 0f
    }

    fun finalizarArrastre() {
        pedidoArrastradoId.value = null
        desplazamientoArrastreY.value = 0f
    }

    fun arrastrarEntrega(pedidoId: Int, deltaY: Float) {
        if (!modoOrdenManual.value || pedidoArrastradoId.value != pedidoId) return

        desplazamientoArrastreY.value += deltaY

        val layoutInfo = listaRutaState.layoutInfo
        val itemActual = layoutInfo.visibleItemsInfo.firstOrNull {
            it.key == pedidoId
        } ?: return

        val centroArrastrado = itemActual.offset +
                desplazamientoArrastreY.value +
                itemActual.size / 2f

        val itemObjetivo = layoutInfo.visibleItemsInfo.firstOrNull { visible ->
            visible.key != pedidoId &&
                    centroArrastrado >= visible.offset &&
                    centroArrastrado <= visible.offset + visible.size
        }

        if (itemObjetivo != null) {
            val idObjetivo = itemObjetivo.key as? Int
            val indiceObjetivo = idObjetivo?.let { ordenManualIds.indexOf(it) } ?: -1

            if (indiceObjetivo >= 0) {
                val diferenciaVisual = itemActual.offset - itemObjetivo.offset

                if (moverEntregaManualAIndice(pedidoId, indiceObjetivo)) {
                    // Compensa el salto de posición para que la tarjeta siga debajo del dedo.
                    desplazamientoArrastreY.value += diferenciaVisual
                }
            }
        }

        // Auto-scroll al acercarse a los bordes de la lista.
        val infoActualizada = listaRutaState.layoutInfo
        val arrastradoActualizado = infoActualizada.visibleItemsInfo.firstOrNull {
            it.key == pedidoId
        } ?: itemActual

        val inicioArrastrado = arrastradoActualizado.offset + desplazamientoArrastreY.value
        val finArrastrado = inicioArrastrado + arrastradoActualizado.size
        val limiteSuperior = infoActualizada.viewportStartOffset + 30f
        val limiteInferior = infoActualizada.viewportEndOffset - 30f

        val desplazamientoScroll = when {
            inicioArrastrado < limiteSuperior ->
                (inicioArrastrado - limiteSuperior).coerceAtLeast(-34f)

            finArrastrado > limiteInferior ->
                (finArrastrado - limiteInferior).coerceAtMost(34f)

            else -> 0f
        }

        if (desplazamientoScroll != 0f) {
            scope.launch {
                val consumido = listaRutaState.scrollBy(desplazamientoScroll)
                desplazamientoArrastreY.value += consumido
            }
        }
    }

    // Punto inicial temporal para Santiago.
    // Más adelante puede salir de configuración o de Direcciones API.
    val laboratorioRuta = remember {
        RutaMapaInicio(
            nombre = "Laboratorio",
            lat = -33.47370422204016,
            lng = -70.6274402762916
        )
    }

    val itemsParaMapa = if (entregasEnRuta.isNotEmpty()) {
        entregasEnRuta
    } else {
        entregasSinIniciar
    }

    val entregasMapa = itemsParaMapa.mapNotNull { item ->
        val direccion = item.direccion ?: return@mapNotNull null
        val lat = direccion.lat ?: return@mapNotNull null
        val lng = direccion.lng ?: return@mapNotNull null

        if (!direccion.tieneCoordenadasValidas()) return@mapNotNull null

        RutaMapaEntrega(
            pedidoId = item.pedido.id,
            numero = item.numero,
            nombre = item.pedido.paciente,
            lat = lat,
            lng = lng,
            colorHex = if (item.pedido.estado.equals("pendiente", true)) {
                "#FF8A00"
            } else {
                "#00C853"
            }
        )
    }

    fun optimizarRutaActual() {
        if (entregasEnRuta.isEmpty()) {
            mensaje.value = "No hay entregas en ruta para optimizar"
            return
        }

        val puntosConCoordenadas = entregasEnRuta.mapNotNull { item ->
            val direccion = item.direccion ?: return@mapNotNull null
            val lat = direccion.lat ?: return@mapNotNull null
            val lng = direccion.lng ?: return@mapNotNull null

            if (!direccion.tieneCoordenadasValidas()) return@mapNotNull null

            RutaGeoPoint(
                id = item.pedido.id,
                lat = lat,
                lng = lng
            )
        }

        if (puntosConCoordenadas.size < 2) {
            mensaje.value = "Se necesitan al menos 2 entregas con coordenadas"
            return
        }

        val resultadoOptimizacion = RutaOptimizer.optimizeShortestPath(
            points = puntosConCoordenadas,
            startLat = laboratorioRuta.lat,
            startLng = laboratorioRuta.lng
        )

        val idsOptimizados = resultadoOptimizacion.orderedIds

        val idsSinCoordenadas = entregasEnRuta
            .filterNot { it.direccion.tieneCoordenadasValidas() }
            .map { it.pedido.id }

        val ordenFinal = idsOptimizados + idsSinCoordenadas

        ordenManualIds.clear()
        ordenManualIds.addAll(ordenFinal)

        preferenciasOrden.edit()
            .putString(claveOrden, ordenFinal.joinToString(","))
            .apply()

        rutaOptimizadaAplicada.value = true

        val distanciaAntes = String.format(Locale.US, "%.1f", resultadoOptimizacion.originalDistanceKm)
        val distanciaDespues = String.format(Locale.US, "%.1f", resultadoOptimizacion.optimizedDistanceKm)
        val ahorro = String.format(Locale.US, "%.1f", resultadoOptimizacion.savedDistanceKm)

        mensaje.value = if (resultadoOptimizacion.savedDistanceKm >= 0.05) {
            "Ruta reordenada: $distanciaAntes km → $distanciaDespues km (ahorro aprox. $ahorro km)"
        } else {
            "La ruta ya estaba prácticamente optimizada: $distanciaDespues km"
        }
    }

    val totalEntregas = entregasRuta.size
    val conCoordenadas = entregasRuta.count { it.direccion.tieneCoordenadasValidas() }
    val sinCoordenadas = totalEntregas - conCoordenadas

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(
                        onClick = { mostrarMenu.value = true },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Text(
                            "☰",
                            color = texto,
                            fontSize = 30.sp,
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
                            text = { Text("Ver direcciones") },
                            onClick = {
                                mostrarMenu.value = false
                                onDireccionesClick()
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
                                cargarDirecciones()
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

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Hola ${
                            user?.name
                                ?.split(" ")
                                ?.firstOrNull()
                                .orEmpty()
                                .ifBlank { "Conductor" }
                        } 👋",
                        color = texto,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        "Panel conductor SMT",
                        color = suave,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactGpsCard(
                    gpsActivo = gpsActivo.value,
                    verde = verde,
                    texto = texto,
                    suave = suave,
                    fondoCard = fondoCard,
                    borde = borde,
                    onToggle = { activo ->
                        if (activo) {
                            mostrarAvisoGps.value = true
                        } else {
                            if (hayPedidosEnRuta) {
                                mensaje.value = "No puedes apagar GPS con pedidos en ruta"
                                return@CompactGpsCard
                            }

                            GpsController.detener(context)
                            gpsActivo.value = false
                            mensaje.value = "GPS desactivado"
                        }
                    }
                )


                if (hayPendientes) {
                    Button(
                        onClick = {
                            if (!gpsActivo.value) {
                                mensaje.value = "Debes activar GPS para iniciar ruta"
                                return@Button
                            }

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
                                        mostrarAvisoGps.value = true
                                    }
                                }

                                accionando.value = false
                            }
                        },
                        enabled = !accionando.value && !cargando.value,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = verde)
                    ) {
                        if (accionando.value) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                "▶ Iniciar",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        cargarPedidos()
                        cargarDirecciones()
                    },
                    enabled = !cargando.value && !accionando.value,
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    if (cargando.value) {
                        CircularProgressIndicator(
                            color = texto,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(
                            "↻",
                            fontWeight = FontWeight.Black,
                            fontSize = 30.sp,
                            lineHeight = 30.sp,
                            color = texto
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            RutaStatsRow(
                total = totalEntregas,
                conCoordenadas = conCoordenadas,
                sinCoordenadas = sinCoordenadas,
                texto = texto,
                suave = suave,
                verde = verde,
                fondoCard = fondoCard,
                borde = borde
            )

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

            Spacer(Modifier.height(14.dp))

            RutaMap(
                entregas = entregasMapa,
                laboratorio = laboratorioRuta,
                rutaOptimizada = rutaOptimizadaAplicada.value,
                onEntregaClick = { pedidoId ->
                    val item = itemsParaMapa.firstOrNull {
                        it.pedido.id == pedidoId
                    }

                    if (item != null) {
                        pedidoDetalle.value = item.pedido to item.direccion
                    }
                }
            )

            Spacer(Modifier.height(18.dp))

            if (cargando.value && pedidos.value.isEmpty()) {
                CircularProgressIndicator(color = verde)
            } else if (entregasSinIniciar.isEmpty() && entregasEnRuta.isEmpty()) {
                Text(
                    "No tienes pedidos asignados",
                    color = suave
                )
            } else {
                if (entregasSinIniciar.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sin iniciar (${entregasSinIniciar.size})",
                            color = Color(0xFFFFA000),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )

                        Text(
                            "Pendientes",
                            color = suave,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    entregasSinIniciar.forEach { item ->
                        PedidoRutaCompactCard(
                            item = item,
                            texto = texto,
                            suave = suave,
                            verde = Color(0xFFFFA000),
                            fondoCard = fondoCard,
                            borderColor = Color(0xFF4A3412),
                            onClick = {
                                pedidoDetalle.value = item.pedido to item.direccion
                            },
                            onVerDireccion = { direccion ->
                                direccionSeleccionada.value = direccion
                            },
                            reordenable = false,
                            arrastrando = false,
                            desplazamientoY = 0f,
                            onDragStart = {},
                            onDrag = {},
                            onDragEnd = {}
                        )

                        Spacer(Modifier.height(8.dp))
                    }

                    Spacer(Modifier.height(18.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Entregas de la ruta (${entregasEnRuta.size})",
                        color = texto,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )

                    if (entregasEnRuta.isNotEmpty()) {
                        Text(
                            if (modoOrdenManual.value) {
                                "Orden manual  ☰"
                            } else {
                                "Orden optimizado  ✨"
                            },
                            color = verde,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    modoOrdenManual.value = !modoOrdenManual.value
                                    if (modoOrdenManual.value) {
                                        rutaOptimizadaAplicada.value = false
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        )
                    }
                }

                if (entregasEnRuta.isNotEmpty() && modoOrdenManual.value) {
                    Text(
                        "Mantén presionado ☰ y arrastra para cambiar el orden",
                        color = suave,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (entregasEnRuta.isEmpty()) {
                    Text(
                        "Todavía no has iniciado una ruta",
                        color = suave
                    )
                } else {
                    val cantidadVisible = entregasEnRuta.size.coerceIn(1, 5)
                    val altoLista = (cantidadVisible * 82).dp

                    LazyColumn(
                        state = listaRutaState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(altoLista),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        userScrollEnabled = pedidoArrastradoId.value == null
                    ) {
                        itemsIndexed(
                            items = entregasEnRuta,
                            key = { _, item -> item.pedido.id }
                        ) { _, item ->
                            val tieneCoordenadas = item.direccion.tieneCoordenadasValidas()
                            val estaArrastrando = pedidoArrastradoId.value == item.pedido.id

                            PedidoRutaCompactCard(
                                item = item,
                                texto = texto,
                                suave = suave,
                                verde = if (tieneCoordenadas) verde else Color(0xFFFF8A00),
                                fondoCard = fondoCard,
                                borderColor = if (tieneCoordenadas) {
                                    Color(0xFF1F2937)
                                } else {
                                    Color(0xFF3A2A12)
                                },
                                onClick = {
                                    if (pedidoArrastradoId.value == null) {
                                        pedidoDetalle.value = item.pedido to item.direccion
                                    }
                                },
                                onVerDireccion = { direccion ->
                                    direccionSeleccionada.value = direccion
                                },
                                reordenable = modoOrdenManual.value,
                                arrastrando = estaArrastrando,
                                desplazamientoY = if (estaArrastrando) {
                                    desplazamientoArrastreY.value
                                } else {
                                    0f
                                },
                                onDragStart = {
                                    comenzarArrastre(item.pedido.id)
                                },
                                onDrag = { deltaY ->
                                    arrastrarEntrega(item.pedido.id, deltaY)
                                },
                                onDragEnd = {
                                    finalizarArrastre()
                                }
                            )
                        }
                    }

                    if (!modoOrdenManual.value) {
                        Spacer(Modifier.height(8.dp))

                        Button(
                            onClick = {
                                optimizarRutaActual()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = verde)
                        ) {
                            Text(
                                "✨ Optimizar ruta",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }

            if (entregasLocales.value.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = fondoCard),
                    border = BorderStroke(1.dp, borde)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp)
                    ) {
                        Text(
                            "Cola offline (${entregasLocales.value.size})",
                            color = texto,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )

                        Spacer(Modifier.height(8.dp))

                        entregasLocales.value.forEach { entrega ->
                            val estadoTexto = when (entrega.estado) {
                                "pendiente" -> "Pendiente de envío"
                                "enviando" -> "Enviando..."
                                "error" -> "Error, requiere reintento"
                                else -> entrega.estado
                            }

                            Text(
                                "Factura ${entrega.factura}",
                                color = texto,
                                fontWeight = FontWeight.Bold
                            )

                            Text(
                                "$estadoTexto · Intentos: ${entrega.intentos}",
                                color = when (entrega.estado) {
                                    "error" -> Color(0xFFF87171)
                                    "enviando" -> Color(0xFF60A5FA)
                                    else -> suave
                                },
                                fontSize = 13.sp
                            )

                            Spacer(Modifier.height(10.dp))
                        }

                        Button(
                            onClick = {
                                mensaje.value = "Reintentando entregas pendientes..."

                                WorkerEnvio.procesarCola(context) { ok, msg ->
                                    scope.launch {
                                        mensaje.value = msg
                                        refrescarColaLocal()

                                        if (ok && user != null) {
                                            val nuevos = SmtApi.cargarMisPedidos(user)
                                            if (nuevos.ok) {
                                                pedidos.value = nuevos.pedidos
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !accionando.value && !cargando.value
                        ) {
                            Text("Reintentar envío")
                        }
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            val detalle = pedidoDetalle.value

            if (detalle != null) {
                PedidoAccionesDialog(
                    pedido = detalle.first,
                    direccion = detalle.second,
                    texto = texto,
                    suave = suave,
                    verde = verde,
                    onDismiss = { pedidoDetalle.value = null },
                    onVerDireccion = { direccion ->
                        direccionSeleccionada.value = direccion
                    },
                    onEntregar = {
                        val pedido = detalle.first
                        pedidoDetalle.value = null
                        pedidoEntrega.value = pedido
                        temperaturaEntrega.value = ""
                        horaEntrega.value = ""
                        fotoEntregaUri.value = null
                        fotoEntregaFile.value = null
                        fotoTomada.value = false
                        mostrarEntrega.value = true
                    },
                    onProblema = {
                        val pedido = detalle.first
                        pedidoDetalle.value = null
                        pedidoProblema.value = pedido
                        motivoProblema.value = ""
                        mostrarProblema.value = true
                    }
                )
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
                                    .filter { it.isDigit() || it == '.' || it == ' ' }
                                    .replace(Regex("\\s+"), " ")
                            },
                            label = { Text("Temperatura") },
                            placeholder = { Text("Ej: 2.2 3.1 4.4") },
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
                            if (!gpsActivo.value) {
                                mensaje.value = "GPS activo requerido para cerrar entrega"
                                return@Button
                            }

                            val pedido = pedidoEntrega.value ?: return@Button
                            val foto = fotoEntregaFile.value
                            val temperaturaLimpia = temperaturaEntrega.value.trim()

                            if (temperaturaLimpia.isBlank()) {
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

                            mensaje.value = "Enviando foto ${(foto.length() / 1024)} KB - ${foto.name}"

                            val entrega = EntregaPendiente(
                                postId = pedido.id,
                                factura = pedido.factura,
                                temperatura = temperaturaLimpia,
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

        if (mostrarProblema.value && pedidoProblema.value != null) {
            AlertDialog(
                onDismissRequest = {
                    if (!accionando.value) {
                        mostrarProblema.value = false
                    }
                },
                title = {
                    Text("Reportar problema")
                },
                text = {
                    Column {
                        Text("Factura: ${pedidoProblema.value?.factura ?: ""}")

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = motivoProblema.value,
                            onValueChange = { motivoProblema.value = it },
                            label = { Text("Motivo del problema") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val pedido = pedidoProblema.value ?: return@Button
                            val motivo = motivoProblema.value.trim()

                            if (motivo.length < 4) {
                                mensaje.value = "Debes indicar un motivo válido"
                                return@Button
                            }

                            if (user == null) {
                                mensaje.value = "Sesión inválida"
                                return@Button
                            }

                            scope.launch {
                                accionando.value = true
                                mensaje.value = "Reportando problema..."

                                val res = SmtApi.actualizarPedidoEstado(
                                    user = user,
                                    postId = pedido.id,
                                    estado = "problema",
                                    motivoProblema = motivo
                                )

                                mensaje.value = res.mensaje

                                if (res.ok) {
                                    mostrarProblema.value = false
                                    pedidoProblema.value = null
                                    motivoProblema.value = ""

                                    val nuevos = SmtApi.cargarMisPedidos(user)
                                    if (nuevos.ok) {
                                        pedidos.value = nuevos.pedidos
                                    }
                                }

                                accionando.value = false
                            }
                        },
                        enabled = !accionando.value
                    ) {
                        Text(if (accionando.value) "Enviando..." else "Reportar")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            mostrarProblema.value = false
                        },
                        enabled = !accionando.value
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }

        val direccionDetalle = direccionSeleccionada.value

        if (direccionDetalle != null) {
            DireccionDetalleDialog(
                direccion = direccionDetalle,
                onDismiss = {
                    direccionSeleccionada.value = null
                },
                onError = { error ->
                    mensaje.value = error
                }
            )
        }

        if (mostrarAvisoGps.value) {
            AlertDialog(
                onDismissRequest = {
                    mostrarAvisoGps.value = false
                },
                title = {
                    Text("Uso de ubicación")
                },
                text = {
                    Text(
                        """
                        SMT Conductores recopila y utiliza la ubicación del dispositivo para:

                        • Mostrar la ubicación del conductor en tiempo real.
                        • Registrar recorridos de rutas asignadas.
                        • Permitir seguimiento operativo y control logístico.
                        • Mantener el monitoreo incluso cuando la aplicación está minimizada o la pantalla está bloqueada mientras exista una ruta activa.

                        La ubicación es utilizada exclusivamente para fines operativos de transporte de SM Transportes.
                        """.trimIndent()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            mostrarAvisoGps.value = false

                            gpsActivo.value = GpsController.iniciar(context)

                            mensaje.value = if (gpsActivo.value) {
                                "GPS activado"
                            } else {
                                "GPS no configurado"
                            }
                        }
                    ) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            mostrarAvisoGps.value = false
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

private fun normalizarNombreDireccion(valor: String): String {
    return valor
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
}

private fun DireccionSmt?.tieneCoordenadasValidas(): Boolean {
    val latitud = this?.lat ?: return false
    val longitud = this.lng ?: return false

    return latitud.isFinite() &&
            longitud.isFinite() &&
            latitud in -90.0..90.0 &&
            longitud in -180.0..180.0
}

/**
 * El laboratorio también se obtiene desde Direcciones API.
 *
 * Para activar el punto de inicio, agrega en WP Admin una entrada con alguno de
 * estos nombres y las coordenadas reales:
 * - LABORATORIO SMT
 * - LABORATORIO SMT SANTIAGO
 * - LABORATORIO SMT CONCEPCION
 */
private fun normalizarSucursal(valor: String): String {
    val normalizada = normalizarNombreDireccion(valor)
        .replace("ó", "o")

    return when {
        normalizada.contains("santiago") || normalizada.contains("stgo") ->
            "santiago"

        normalizada.contains("concepcion") || normalizada.contains("conce") ->
            "concepcion"

        else -> normalizada
    }
}

/**
 * El punto inicial también sale de Direcciones API.
 *
 * Para activarlo, crea en WP Admin una entrada con las coordenadas reales y uno
 * de estos nombres:
 *
 * Santiago:
 * - LABORATORIO SMT SANTIAGO
 * - BASE SMT SANTIAGO
 *
 * Concepción:
 * - LABORATORIO SMT CONCEPCION
 * - BASE SMT CONCEPCION
 *
 * También se aceptan LABORATORIO SMT o BASE SMT como respaldo general.
 */
private fun encontrarLaboratorioRuta(
    direcciones: List<DireccionSmt>,
    sucursalUsuario: String
): RutaMapaInicio? {
    val sucursalCanonica = normalizarSucursal(sucursalUsuario)

    val nombresPreferidos = when (sucursalCanonica) {
        "concepcion" -> listOf(
            "laboratorio smt concepcion",
            "laboratorio smt conce",
            "base smt concepcion",
            "base smt conce"
        )

        "santiago" -> listOf(
            "laboratorio smt santiago",
            "laboratorio smt stgo",
            "base smt santiago",
            "base smt stgo"
        )

        else -> emptyList()
    } + listOf("laboratorio smt", "base smt")

    val candidatas = direcciones.filter { direccion ->
        direccion.tieneCoordenadasValidas() &&
                (
                        sucursalCanonica.isBlank() ||
                                normalizarSucursal(direccion.sucursal).isBlank() ||
                                normalizarSucursal(direccion.sucursal) == sucursalCanonica
                        )
    }

    val porNombre = candidatas.associateBy {
        normalizarNombreDireccion(it.nombre)
    }

    val encontrada = nombresPreferidos.firstNotNullOfOrNull { nombre ->
        porNombre[nombre]
    } ?: candidatas.firstOrNull { direccion ->
        val nombre = normalizarNombreDireccion(direccion.nombre)
        nombre.startsWith("laboratorio smt") || nombre.startsWith("base smt")
    }

    val lat = encontrada?.lat ?: return null
    val lng = encontrada.lng ?: return null

    return RutaMapaInicio(
        nombre = encontrada.nombre.ifBlank { "Laboratorio SMT" },
        lat = lat,
        lng = lng
    )
}

data class PedidoRutaUi(
    val numero: Int,
    val pedido: PedidoSmt,
    val direccion: DireccionSmt?
)

@Composable
private fun RowScope.CompactGpsCard(
    gpsActivo: Boolean,
    verde: Color,
    texto: Color,
    suave: Color,
    fondoCard: Color,
    borde: Color,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = fondoCard),
        border = BorderStroke(1.dp, borde)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "GPS",
                    color = texto,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )

                Text(
                    if (gpsActivo) "Activo" else "Off",
                    color = if (gpsActivo) verde else Color(0xFFF87171),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Switch(
                checked = gpsActivo,
                onCheckedChange = onToggle,
                modifier = Modifier.size(46.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = verde
                )
            )
        }
    }
}

@Composable
private fun RutaStatsRow(
    total: Int,
    conCoordenadas: Int,
    sinCoordenadas: Int,
    texto: Color,
    suave: Color,
    verde: Color,
    fondoCard: Color,
    borde: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = fondoCard),
        border = BorderStroke(1.dp, borde)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RutaStatItem("📦", total.toString(), "Total", texto, suave, Modifier.weight(1f))
            RutaStatItem("📍", conCoordenadas.toString(), "Coords", texto, suave, Modifier.weight(1f))
            RutaStatItem("⚠", sinCoordenadas.toString(), "Sin coords", texto, Color(0xFFFF8A00), Modifier.weight(1f))
        }
    }
}

@Composable
private fun RutaStatItem(
    icono: String,
    valor: String,
    label: String,
    texto: Color,
    suave: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icono, fontSize = 19.sp)

        Spacer(Modifier.size(6.dp))

        Column {
            Text(
                valor,
                color = texto,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Text(
                label,
                color = suave,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PedidoRutaCompactCard(
    item: PedidoRutaUi,
    texto: Color,
    suave: Color,
    verde: Color,
    fondoCard: Color,
    borderColor: Color,
    onClick: () -> Unit,
    onVerDireccion: (DireccionSmt) -> Unit,
    reordenable: Boolean,
    arrastrando: Boolean,
    desplazamientoY: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (arrastrando) 10f else 0f)
            .graphicsLayer {
                translationY = desplazamientoY
                scaleX = if (arrastrando) 1.02f else 1f
                scaleY = if (arrastrando) 1.02f else 1f
                shadowElevation = if (arrastrando) 22f else 0f
                alpha = if (arrastrando) 0.96f else 1f
            }
            .clickable(enabled = !arrastrando, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = fondoCard),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .then(
                        if (reordenable) {
                            Modifier.pointerInput(item.pedido.id, reordenable) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        onDragStart()
                                    },
                                    onDragEnd = {
                                        onDragEnd()
                                    },
                                    onDragCancel = {
                                        onDragEnd()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDrag(dragAmount.y)
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (reordenable) "☰" else "•",
                    color = if (reordenable) texto else suave,
                    fontSize = if (reordenable) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.size(10.dp))

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(verde, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.numero.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.size(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    item.pedido.paciente.ifBlank { "Sin paciente" },
                    color = texto,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    "Factura ${item.pedido.factura.ifBlank { "Sin número" }}",
                    color = suave,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.size(8.dp))

            if (item.direccion != null) {
                IconButton(
                    onClick = { onVerDireccion(item.direccion) },
                    modifier = Modifier.size(42.dp)
                ) {
                    Text(
                        "📍",
                        fontSize = 22.sp
                    )
                }
            } else {
                Text(
                    "Sin ubicación",
                    color = suave,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .background(Color(0xFF111827), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Text(
                "›",
                color = suave,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PedidoAccionesDialog(
    pedido: PedidoSmt,
    direccion: DireccionSmt?,
    texto: Color,
    suave: Color,
    verde: Color,
    onDismiss: () -> Unit,
    onVerDireccion: (DireccionSmt) -> Unit,
    onEntregar: () -> Unit,
    onProblema: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                pedido.paciente.ifBlank { "Detalle de entrega" },
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column {
                Text("Factura: ${pedido.factura}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    pedido.direccion.ifBlank { pedido.comuna.ifBlank { "Sin dirección registrada" } },
                    color = suave
                )
                Text("Estado: ${pedido.estado}", color = suave, fontSize = 13.sp)

                if (direccion != null) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onVerDireccion(direccion) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = verde)
                    ) {
                        Text("📍 Ver destino / Waze / Maps", fontWeight = FontWeight.Bold)
                    }
                }

                if (pedido.estado.equals("en_ruta", true)) {
                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onEntregar,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = verde)
                        ) {
                            Text("Entregar", fontWeight = FontWeight.Black)
                        }

                        Button(
                            onClick = onProblema,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Text("Problema", fontWeight = FontWeight.Black)
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Inicia la ruta para habilitar Entregar / Problema.",
                        color = suave,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
