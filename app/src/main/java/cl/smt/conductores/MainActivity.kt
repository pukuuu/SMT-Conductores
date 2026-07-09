package cl.smt.conductores

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import cl.smt.conductores.data.SessionManager
import cl.smt.conductores.data.SmtApi
import cl.smt.conductores.data.SmtUser
import cl.smt.conductores.screens.CrearRutaScreen
import cl.smt.conductores.screens.DireccionesScreen
import cl.smt.conductores.screens.EntregasV1Screen
import cl.smt.conductores.screens.EntregasV2Screen
import cl.smt.conductores.screens.LoginScreen
import cl.smt.conductores.screens.MenuScreen
import cl.smt.conductores.screens.PermisosScreen
import cl.smt.conductores.screens.UpdateRequiredScreen
import cl.smt.conductores.ui.theme.SMTConductoresTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.navigationBarColor = AndroidColor.BLACK
        window.statusBarColor = AndroidColor.parseColor("#00140D")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

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
            "direcciones",
            "perfil",
            "historial" -> {
                screen = "main"
            }

            "permisos",
            "version_check",
            "update_required" -> {
                // No cerrar app ni saltar permisos/actualización con botón atrás.
            }

            "main" -> {
                // Por ahora no cerrar app desde el panel principal.
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
                        screen = "main"
                    }
                )
            }
        }

        "main" -> {
            if (user == null) {
                screen = "login"
            } else {
                MainTabsScreen(
                    onDireccionesClick = {
                        screen = "direcciones"
                    },
                    onPerfilClick = {
                        screen = "perfil"
                    },
                    onHistorialClick = {
                        screen = "historial"
                    },
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

        "direcciones" -> {
            DireccionesScreen(
                onBack = {
                    screen = "main"
                }
            )
        }

        "perfil" -> {
            PlaceholderScreen("Perfil pendiente") {
                screen = "main"
            }
        }

        "historial" -> {
            PlaceholderScreen("Historial pendiente") {
                screen = "main"
            }
        }

        else -> {
            screen = if (user == null) {
                "login"
            } else {
                "main"
            }
        }
    }
}

@Composable
private fun MainTabsScreen(
    onDireccionesClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onHistorialClick: () -> Unit,
    onCerrarSesionClick: () -> Unit,
    onSesionExpirada: () -> Unit
) {
    var tab by remember { mutableStateOf(MainTab.ENTREGAS_V1) }

    Scaffold(
        containerColor = Color(0xFF00140D),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
                    .navigationBarsPadding()
            ) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    containerColor = Color(0xFF020617),
                    contentColor = Color.White,
                    windowInsets = WindowInsets(0.dp)
                ) {
                    MainTab.values().forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = {
                                Text(
                                    text = item.icon,
                                    fontSize = 14.sp,
                                    color = if (tab == item) Color(0xFF00C853) else Color(0xFF9CA3AF)
                                )
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 8.sp,
                                    color = if (tab == item) Color(0xFF00C853) else Color(0xFF9CA3AF)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF00C853),
                                selectedTextColor = Color(0xFF00C853),
                                unselectedIconColor = Color(0xFF9CA3AF),
                                unselectedTextColor = Color(0xFF9CA3AF),
                                indicatorColor = Color(0x2200C853)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (tab) {
                MainTab.ENTREGAS_V1 -> {
                    EntregasV1Screen(
                        onCrearRutaClick = { tab = MainTab.CREAR_RUTA },
                        onPerfilClick = onPerfilClick,
                        onHistorialClick = onHistorialClick,
                        onDireccionesClick = onDireccionesClick,
                        onCerrarSesionClick = onCerrarSesionClick,
                        onSesionExpirada = onSesionExpirada
                    )
                }

                MainTab.ENTREGAS_V2 -> {
                    EntregasV2Screen(
                        onCrearRutaClick = { tab = MainTab.CREAR_RUTA },
                        onSesionExpirada = onSesionExpirada
                    )
                }

                MainTab.CREAR_RUTA -> {
                    CrearRutaScreen(
                        onBack = {
                            tab = MainTab.ENTREGAS_V2
                        }
                    )
                }

                MainTab.MENU -> {
                    MenuScreen(
                        onDireccionesClick = onDireccionesClick,
                        onPerfilClick = onPerfilClick,
                        onHistorialClick = onHistorialClick,
                        onCerrarSesionClick = onCerrarSesionClick
                    )
                }
            }
        }
    }
}

private enum class MainTab(
    val label: String,
    val icon: String
) {
    ENTREGAS_V1("Entregas v1", "📦"),
    ENTREGAS_V2("Entregas v2", "🛣️"),
    CREAR_RUTA("Crear ruta", "➕"),
    MENU("Menú", "☰")
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
