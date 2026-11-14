package com.jarvis.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvisColors = darkColorScheme(
    primary = Color(0xFF4FD8FF),
    secondary = Color(0xFF9B6BFF),
    background = Color(0xFF070B1F),
    surface = Color(0xFF0E1636)
)

@Composable
fun JarvisAITheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = JarvisColors, content = content)
}
