package cl.smt.conductores.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import cl.smt.conductores.components.Camera2BarcodeScanner
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var usuario by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var mostrarScanner by remember { mutableStateOf(false) }

    val verde = Color(0xFF00C853)
    val texto = Color(0xFFF8FAFC)
    val suave = Color(0xFF9CA3AF)
    val fondoCard = Color(0xEE0B1120)
    val borde = Color(0xFF123D2B)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            mostrarScanner = true
        } else {
            mensaje = "Debes permitir la cámara para usar login QR."
        }
    }

    fun iniciarLogin() {
        if (loading) return

        if (usuario.trim().isBlank() || password.isBlank()) {
            mensaje = "Ingresa usuario y contraseña"
            return
        }

        loading = true
        mensaje = ""

        scope.launch {
            val res = SmtApi.login(usuario.trim(), password)

            loading = false

            if (!res.ok || res.user == null) {
                mensaje = res.mensaje.ifBlank { "Error al iniciar sesión" }
                return@launch
            }

            SessionManager.saveUser(context, res.user)
            onLoginSuccess()
        }
    }

    fun iniciarLoginQr() {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted) {
            mostrarScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = fondoCard),
            border = BorderStroke(1.dp, borde)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SMT",
                    color = verde,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "Conductores",
                    color = texto,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )

                Text(
                    text = "Escanea tu QR o ingresa manualmente",
                    color = suave,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { iniciarLoginQr() },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = verde,
                        contentColor = Color(0xFF00140D)
                    )
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text(
                            "Entrar con QR",
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                OutlinedTextField(
                    value = usuario,
                    onValueChange = { usuario = it },
                    label = { Text("Usuario") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                OutlinedButton(
                    onClick = { iniciarLogin() },
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        "Ingresar manualmente",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (mensaje.isNotBlank()) {
                    Text(
                        text = mensaje,
                        color = if (
                            mensaje.contains("concedido", true) ||
                            mensaje.contains("correct", true)
                        ) verde else Color(0xFFF87171),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = "SM Transportes",
                    color = suave,
                    fontSize = 12.sp
                )
            }
        }

        if (mostrarScanner) {
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
                    border = BorderStroke(1.dp, verde)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Escanear QR de acceso",
                            color = texto,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .background(Color.Black, RoundedCornerShape(18.dp))
                        ) {
                            Camera2BarcodeScanner(
                                barcodeFormat = Barcode.FORMAT_QR_CODE,
                                flashEnabled = false,
                                modifier = Modifier.fillMaxSize(),
                                onCodeScanned = { codigo ->
                                    mostrarScanner = false
                                    loading = true
                                    mensaje = ""

                                    scope.launch {
                                        val res = SmtApi.loginBarcode(codigo)

                                        loading = false

                                        if (!res.ok || res.user == null) {
                                            mensaje = res.mensaje.ifBlank { "QR inválido" }
                                            return@launch
                                        }

                                        SessionManager.saveUser(context, res.user)
                                        onLoginSuccess()
                                    }
                                },
                                onError = { error ->
                                    mensaje = error
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                mostrarScanner = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        }
    }
}