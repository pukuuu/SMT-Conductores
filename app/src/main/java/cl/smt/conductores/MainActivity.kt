package cl.smt.conductores

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.data.SmtUser
import cl.smt.conductores.screens.CrearRutaScreen
import cl.smt.conductores.screens.LoginScreen
import cl.smt.conductores.screens.PanelScreen
import cl.smt.conductores.screens.PermisosScreen
import cl.smt.conductores.screens.UpdateRequiredScreen
import cl.smt.conductores.ui.theme.SMTConductoresTheme
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.navigationBarColor = AndroidColor.BLACK
        window.statusBarColor = AndroidColor.BLACK
        setContent {
            SMTConductoresTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current

    var user by remember {
        mutableStateOf<SmtUser?>(SessionManager.getUser(context))
    }

    var screen by remember {
        mutableStateOf("version_check")
    }

    var updateMessage by remember {
        mutableStateOf("")
    }

    var playStoreUrl by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {
        val version = SmtApi.checkAppVersion()

        if (
            version.ok &&
            version.forceUpdate &&
            BuildConfig.VERSION_CODE < version.minimumVersionCode
        ) {
            updateMessage = version.message
            playStoreUrl = version.playStoreUrl
            screen = "update_required"
        } else {
            screen = if (user == null) {
                "login"
            } else {
                "permisos"
            }
        }
    }

    BackHandler(enabled = user != null) {
        when (screen) {
            "crear_ruta", "perfil", "historial" -> {
                screen = "panel"
            }

            "permisos", "version_check", "update_required" -> {
                // No cerrar app ni saltar permisos/actualización con botón atrás.
            }

            "panel" -> {
                // Por ahora no cerrar app desde el panel.
            }
        }
    }

    when (screen) {
        "version_check" -> {
            LoadingScreen("Revisando versión...")
        }

        "update_required" -> {
            UpdateRequiredScreen(
                message = updateMessage,
                playStoreUrl = playStoreUrl
            )
        }

        "login" -> {
            LoginScreen(
                onLoginSuccess = {
                    user = SessionManager.getUser(context)
                    screen = "permisos"
                }
            )
        }

        "permisos" -> {
            if (user == null) {
                screen = "login"
            } else {
                PermisosScreen(
                    onPermisosOk = {
                        screen = "panel"
                    }
                )
            }
        }

        "panel" -> {
            if (user == null) {
                screen = "login"
            } else {
                PanelScreen(
                    onCrearRutaClick = { screen = "crear_ruta" },
                    onPerfilClick = { screen = "perfil" },
                    onHistorialClick = { screen = "historial" },
                    onCerrarSesionClick = {
                        SessionManager.clear(context)
                        user = null
                        screen = "login"
                    },
                    onSesionExpirada = {
                        SessionManager.clear(context)
                        user = null
                        screen = "login"
                    }
                )
            }
        }

        "crear_ruta" -> {
            CrearRutaScreen(
                onBack = {
                    screen = "panel"
                }
            )
        }

        "perfil" -> {
            PlaceholderScreen("Perfil pendiente") {
                screen = "panel"
            }
        }

        "historial" -> {
            PlaceholderScreen("Historial pendiente") {
                screen = "panel"
            }
        }

        else -> {
            screen = if (user == null) "login" else "panel"
        }
    }
}
@Composable
fun LoadingScreen(text: String) {
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
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFF00C853)
        )
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("$title\n\nPresiona atrás del sistema para volver")
    }
}