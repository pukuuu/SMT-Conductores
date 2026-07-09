package cl.smt.conductores.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MenuScreen(
    onDireccionesClick: () -> Unit,
    onPerfilClick: () -> Unit,
    onHistorialClick: () -> Unit,
    onCerrarSesionClick: () -> Unit
) {
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
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Menú",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Panel conductor SMT",
                color = Color(0xFF9CA3AF),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            MenuButton("📍 Direcciones", onDireccionesClick)
            MenuButton("👤 Perfil", onPerfilClick)
            MenuButton("🕘 Historial", onHistorialClick)
            MenuButton("🚪 Cerrar sesión", onCerrarSesionClick, danger = true)
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) Color(0xFFFF6B6B) else Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
