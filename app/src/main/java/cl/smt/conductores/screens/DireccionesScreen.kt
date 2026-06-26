package cl.smt.conductores.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.models.DireccionSmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun DireccionesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = SessionManager.getUser(context)

    val direcciones = remember { mutableStateOf<List<DireccionSmt>>(emptyList()) }
    val busqueda = remember { mutableStateOf("") }
    val cargando = remember { mutableStateOf(false) }
    val mensaje = remember { mutableStateOf("") }
    val direccionSeleccionada = remember { mutableStateOf<DireccionSmt?>(null) }

    val verde = Color(0xFF00C853)
    val texto = Color(0xFFF8FAFC)
    val suave = Color(0xFF9CA3AF)
    val fondoCard = Color(0xEE0B1120)
    val borde = Color(0xFF123D2B)

    fun cargarDirecciones() {
        if (user == null) {
            mensaje.value = "Sesión inválida"
            return
        }

        scope.launch {
            cargando.value = true
            mensaje.value = ""

            val res = SmtApi.cargarDirecciones(user)

            cargando.value = false

            if (res.ok) {
                direcciones.value = res.direcciones
                if (res.direcciones.isEmpty()) {
                    mensaje.value = "No hay direcciones registradas"
                }
            } else {
                mensaje.value = res.mensaje
            }
        }
    }

    fun abrirUrl(url: String) {
        if (url.isBlank()) return

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            mensaje.value = "No se pudo abrir el enlace"
        }
    }

    LaunchedEffect(Unit) {
        cargarDirecciones()
    }

    val direccionesFiltradas = direcciones.value.filter { direccion ->
        val q = busqueda.value.trim()

        if (q.isBlank()) {
            true
        } else {
            direccion.nombre.contains(q, ignoreCase = true)
        }
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
                .padding(top = 42.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Direcciones",
                color = texto,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "Busca por nombre de institución, clínica, hospital o ambulatorio.",
                color = suave,
                fontSize = 14.sp
            )

            OutlinedTextField(
                value = busqueda.value,
                onValueChange = { busqueda.value = it },
                label = { Text("Buscar por nombre") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Volver")
                }

                Button(
                    onClick = { cargarDirecciones() },
                    modifier = Modifier.weight(1f),
                    enabled = !cargando.value
                ) {
                    Text(if (cargando.value) "Cargando..." else "Actualizar")
                }
            }

            if (mensaje.value.isNotBlank()) {
                Text(
                    text = mensaje.value,
                    color = if (
                        mensaje.value.contains("carg", true) ||
                        mensaje.value.contains("registr", true)
                    ) {
                        suave
                    } else {
                        Color(0xFFF87171)
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            if (cargando.value && direcciones.value.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = verde)
                }
            } else if (direccionesFiltradas.isEmpty()) {
                Text(
                    text = "No se encontraron direcciones",
                    color = suave
                )
            } else {
                direccionesFiltradas.forEach { direccion ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                direccionSeleccionada.value = direccion
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = fondoCard),
                        border = BorderStroke(1.dp, borde)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = direccion.nombre.ifBlank { "Sin nombre" },
                                color = texto,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )

                            if (direccion.sucursal.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = "Sucursal: ${direccion.sucursal}",
                                    color = suave,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        val seleccion = direccionSeleccionada.value

        if (seleccion != null) {
            AlertDialog(
                onDismissRequest = {
                    direccionSeleccionada.value = null
                },
                title = {
                    Text(seleccion.nombre.ifBlank { "Dirección" })
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (seleccion.wazeUrl.isNotBlank()) {
                                Button(
                                    onClick = { abrirUrl(seleccion.wazeUrl) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF33CCFF),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("🚙 Waze", fontWeight = FontWeight.Black)
                                }
                            }

                            if (seleccion.mapsUrl.isNotBlank()) {
                                Button(
                                    onClick = { abrirUrl(seleccion.mapsUrl) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00C853),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("📍 Maps", fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Indicaciones:",
                            fontWeight = FontWeight.Black
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = seleccion.notas.ifBlank { "Sin indicaciones registradas." }
                        )

                        val fotosValidas = seleccion.fotos.filter { it.isNotBlank() }

                        if (fotosValidas.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = if (fotosValidas.size > 1) {
                                    "Fotos: ${fotosValidas.size} · desliza para ver más →"
                                } else {
                                    "Foto:"
                                },
                                fontWeight = FontWeight.Black
                            )

                            Spacer(Modifier.height(10.dp))

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(fotosValidas) { index, fotoUrl ->
                                    FotoRemotaDireccionHorizontal(
                                        url = fotoUrl,
                                        label = "Foto ${index + 1}",
                                        onOpen = {
                                            abrirUrl(fotoUrl)
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            direccionSeleccionada.value = null
                        }
                    ) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

@Composable
fun FotoRemotaDireccionHorizontal(
    url: String,
    label: String,
    onOpen: () -> Unit
) {
    val bitmapState = remember { mutableStateOf<Bitmap?>(null) }
    val cargando = remember { mutableStateOf(false) }
    val error = remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        cargando.value = true
        error.value = false

        val bitmap = withContext(Dispatchers.IO) {
            try {
                URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                null
            }
        }

        bitmapState.value = bitmap
        error.value = bitmap == null
        cargando.value = false
    }

    val bitmap = bitmapState.value
    val anchoFoto = 260.dp

    val modifierFoto = if (bitmap != null && bitmap.height > 0) {
        Modifier
            .width(anchoFoto)
            .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
            .clickable { onOpen() }
    } else {
        Modifier
            .width(anchoFoto)
            .height(180.dp)
            .clickable { onOpen() }
    }

    Card(
        modifier = modifierFoto,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x11000000)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                cargando.value -> {
                    CircularProgressIndicator()
                }

                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                error.value -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Tocar para abrir foto")
                    }
                }
            }
        }
    }
}
