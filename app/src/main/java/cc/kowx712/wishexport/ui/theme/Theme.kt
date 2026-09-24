package cc.kowx712.wishexport.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle

private val Purple = Color(0xFF6750A4)

@Composable
fun WishExportTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    DynamicMaterialExpressiveTheme(
        seedColor = Purple,
        style = PaletteStyle.TonalSpot,
        motionScheme = MotionScheme.expressive(),
        isDark = darkTheme,
        animate = true,
        content = content,
    )
}
