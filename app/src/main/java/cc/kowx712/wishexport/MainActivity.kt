package cc.kowx712.wishexport

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.kowx712.wishexport.model.AccessMode
import cc.kowx712.wishexport.ui.screen.HomeScreen
import cc.kowx712.wishexport.ui.screen.WelcomeScreen
import cc.kowx712.wishexport.ui.theme.WishExportTheme
import cc.kowx712.wishexport.viewmodel.MainViewModel
import rikka.shizuku.Shizuku

/**
 * Main Activity that hosts the Compose UI
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val SHIZUKU_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            WishExportTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WishExportApp()
                }
            }
        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            return false
        }

        return try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                true
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                false
            } else {
                if (Shizuku.pingBinder()) {
                    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
                }
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun requestShizukuPermissionIfNeeded() {
        checkShizukuPermission()
    }
}

@Composable
fun WishExportApp(
    viewModel: MainViewModel = viewModel()
) {
    val accessMode by viewModel.accessMode.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    if (accessMode == null) {
        WelcomeScreen(
            onModeSelected = { mode ->
                viewModel.setAccessMode(mode)
                // Request Shizuku permission if Shizuku mode is selected
                if (mode == AccessMode.SHIZUKU) {
                    (activity as? MainActivity)?.requestShizukuPermissionIfNeeded()
                }
            }
        )
    } else {
        HomeScreen(
            captureState = captureState,
            onStartCapture = { viewModel.startCapture() },
            onStopCapture = { viewModel.stopCapture() },
            onCopyToClipboard = { url -> viewModel.copyToClipboard(url) },
            onReset = { viewModel.resetState() },
            onPermissionDenied = { viewModel.clearAccessMode() },
            accessMode = accessMode
        )
    }
}
