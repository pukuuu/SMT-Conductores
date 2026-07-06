package cl.smt.conductores.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.smt.conductores.models.DireccionSmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
fun DireccionDetalleDialog(
    direccion: DireccionSmt,
    onDismiss: () -> Unit,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current

    fun abrirUrl(url: String) {
        if (url.isBlank()) return

        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            onError("No se pudo abrir el enlace")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(direccion.nombre.ifBlank { "Dirección" })
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
                    if (direccion.wazeUrl.isNotBlank()) {
                        Button(
                            onClick = { abrirUrl(direccion.wazeUrl) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF33CCFF),
                                contentColor = Color.White
                            )
                        ) {
                            Text("🚙 Waze", fontWeight = FontWeight.Black)
                        }
                    }

                    if (direccion.mapsUrl.isNotBlank()) {
                        Button(
                            onClick = { abrirUrl(direccion.mapsUrl) },
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
                    text = direccion.notas.ifBlank {
                        "Sin indicaciones registradas."
                    }
                )

                val fotosValidas = direccion.fotos.filter { it.isNotBlank() }

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
                            FotoRemotaDireccion(
                                url = fotoUrl,
                                label = "Foto ${index + 1}",
                                onOpen = { abrirUrl(fotoUrl) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
private fun FotoRemotaDireccion(
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
            } catch (_: Exception) {
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
