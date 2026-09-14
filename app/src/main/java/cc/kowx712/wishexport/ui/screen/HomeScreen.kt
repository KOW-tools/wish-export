package cc.kowx712.wishexport.ui.screen

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.kowx712.wishexport.MainActivity
import cc.kowx712.wishexport.R
import cc.kowx712.wishexport.model.AccessMode
import cc.kowx712.wishexport.model.CaptureState
import cc.kowx712.wishexport.ui.component.AboutDialog
import cc.kowx712.wishexport.ui.component.PermissionDialog

/**
 * Main home screen with capture functionality
 */
@Composable
fun HomeScreen(
    captureState: CaptureState,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onCopyToClipboard: (String) -> Unit,
    onReset: () -> Unit,
    onPermissionDenied: () -> Unit,
    accessMode: AccessMode? = null
) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.copied_to_clipboard)
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    // Request Shizuku permission when HomeScreen loads with Shizuku mode
    LaunchedEffect(Unit) {
        if (accessMode == AccessMode.SHIZUKU) {
            (activity as? MainActivity)?.requestShizukuPermissionIfNeeded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = stringResource(R.string.about)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                onClick = {
                    when (captureState) {
                        is CaptureState.Capturing -> onStopCapture()
                        else -> onStartCapture()
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (captureState is CaptureState.Capturing) {
                            Icons.Default.Stop
                        } else {
                            Icons.Default.PlayArrow
                        },
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = if (captureState is CaptureState.Capturing) {
                            stringResource(R.string.stop_capture)
                        } else {
                            stringResource(R.string.start_capture)
                        }
                    )
                },
                containerColor = when (captureState) {
                    is CaptureState.Capturing -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = when (captureState) {
                    is CaptureState.Capturing -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (captureState) {
                    is CaptureState.Idle -> {
                        IdleContent()
                    }
                    is CaptureState.Capturing -> {
                        CapturingContent()
                    }
                    is CaptureState.Success -> {
                        SuccessContent(
                            url = captureState.url,
                            onCopy = { url ->
                                onCopyToClipboard(url)
                                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                            },
                            onReset = onReset
                        )
                    }
                    is CaptureState.Error -> {
                        ErrorContent(
                            message = captureState.message,
                            onTryAgain = onStartCapture
                        )
                    }
                }
            }
        }
    }

    // About Dialog
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    if (showPermissionDialog) {
        PermissionDialog(onConfirm = {
            showPermissionDialog = false
            onPermissionDenied()
        })
    }

    // Check for permission errors
    if (captureState is CaptureState.Error &&
        (captureState.message.contains("not running", ignoreCase = true) ||
         captureState.message.contains("not available", ignoreCase = true))) {
        showPermissionDialog = true
    }
}

@Composable
private fun IdleContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.ready_to_capture),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.start_capture_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CapturingContent() {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(80.dp)
                .rotate(rotation),
            strokeWidth = 6.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.capturing),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.capturing_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SuccessContent(
    url: String,
    onCopy: (String) -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.url_captured),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onCopy(url) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.copy_to_clipboard))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.capture_another))
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onTryAgain: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "⚠",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.error),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onTryAgain,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.try_again))
        }
    }
}
