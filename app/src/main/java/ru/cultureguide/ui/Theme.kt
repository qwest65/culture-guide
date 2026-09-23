package ru.cultureguide.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Primary = Color(0xFF1F4FD1)
    val PrimarySoft = Color(0xFFE6EDFD)
    val Accent = Color(0xFFFF8F00)
    val AccentSoft = Color(0xFFFFF3E0)
    val Success = Color(0xFF2E9E5B)
    val Ink = Color(0xFF151922)
    val Muted = Color(0xFF6D7380)
    val Line = Color(0xFFE3E5E9)
    val Field = Color(0xFFF3F5F9)
    val Upcoming = Color(0xFFE0E3E8)
}

@Composable
fun GuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Palette.Primary,
            secondary = Palette.Accent,
            background = Color(0xFFF2F4F7),
            surface = Color.White,
            onSurface = Palette.Ink
        ),
        content = content
    )
}
