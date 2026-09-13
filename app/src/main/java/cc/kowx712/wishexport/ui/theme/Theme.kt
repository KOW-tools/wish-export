package cc.kowx712.wishexport.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.DynamicMaterialExpressiveTheme

/**
 * Material 3 Expressive Theme with MaterialKolor
 * Uses SPEC_2025 and Expressive palette style for vibrant, playful colors
 */
@Composable
fun WishExportTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val seedColor = Color(0xFF6750A4) // Vibrant purple

    DynamicMaterialExpressiveTheme(
        seedColor = seedColor,
        motionScheme = MotionScheme.expressive(),
        isDark = darkTheme,
        animate = true,
        content = content,
    )
}
