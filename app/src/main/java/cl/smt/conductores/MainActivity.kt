package cl.smt.conductores

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtUser
import cl.smt.conductores.screens.LoginScreen
import cl.smt.conductores.screens.PanelScreen
import cl.smt.conductores.ui.theme.SMTConductoresTheme

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

    if (user == null) {
        LoginScreen(
            onLoginSuccess = {
                user = SessionManager.getUser(context)
            }
        )
    } else {
        PanelScreen(
            user = user!!,
            onLogout = {
                SessionManager.clear(context)
                user = null
            }
        )
    }
}