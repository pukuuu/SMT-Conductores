package cl.smt.conductores

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtUser
import cl.smt.conductores.screens.LoginScreen
import cl.smt.conductores.screens.PanelScreen
import cl.smt.conductores.ui.theme.SMTConductoresTheme
import cl.smt.conductores.screens.CrearRutaScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        mutableStateOf("panel")
    }

    if (user == null) {
        LoginScreen(
            onLoginSuccess = {
                user = SessionManager.getUser(context)
                screen = "panel"
            }
        )
        return
    }

    when (screen) {
        "panel" -> {
            PanelScreen(
                onCrearRutaClick = { screen = "crear_ruta" },
                onPerfilClick = { screen = "perfil" },
                onHistorialClick = { screen = "historial" },
                onCerrarSesionClick = {
                    SessionManager.clear(context)
                    user = null
                    screen = "panel"
                },
                onSesionExpirada = {
                    SessionManager.clear(context)
                    user = null
                    screen = "panel"
                }
            )
        }

        "crear_ruta" -> CrearRutaScreen(
            onBack = {
                screen = "panel"
            }
        )

        "perfil" -> PlaceholderScreen("Perfil pendiente") {
            screen = "panel"
        }

        "historial" -> PlaceholderScreen("Historial pendiente") {
            screen = "panel"
        }
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