package com.example.guidecam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.guidecam.ui.CameraScreen
import com.example.guidecam.ui.theme.GuideCamTheme

/**
 * MainActivity is the single entry point for the GuideCam app.
 *
 * ARCHITECTURE DECISIONS:
 *
 * 1. Single Activity Architecture:
 *    - All navigation happens within Compose
 *    - Simplifies lifecycle management
 *    - Better for modern Android development
 *
 * 2. Edge-to-Edge Display:
 *    - Camera preview extends to system bars
 *    - Provides immersive camera experience
 *    - System bars are handled by the theme
 *
 * 3. Compose-Friendly Permission Handling:
 *    - Uses rememberLauncherForActivityResult for permission requests
 *    - State-driven UI updates based on permission status
 *    - Clean integration with Compose lifecycle
 *
 * PERMISSION FLOW:
 *
 * 1. On launch, check if CAMERA permission is granted
 * 2. If not granted, show rationale screen with request button
 * 3. When button pressed, launch permission request dialog
 * 4. On result, update state and show appropriate UI
 *
 * WHY NOT REQUEST ON LAUNCH:
 *
 * Google recommends requesting permissions in context (when needed)
 * rather than immediately on app launch. This provides better UX
 * and higher acceptance rates. However, for a camera app, the camera
 * IS the main feature, so we request early but with a rationale screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display for immersive camera experience
        enableEdgeToEdge()

        setContent {
            GuideCamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GuideCamApp()
                }
            }
        }
    }
}

/**
 * GuideCamApp is the root composable that handles permission state and navigation.
 *
 * UI States:
 * 1. Permission Granted -> Show CameraScreen
 * 2. Permission Denied -> Show PermissionRationale screen
 */
@Composable
fun GuideCamApp() {
    val context = LocalContext.current

    // Track camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission request launcher using Compose's Activity Result API
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Check permission on launch
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Show appropriate UI based on permission state
    if (hasCameraPermission) {
        CameraScreen(modifier = Modifier.fillMaxSize())
    } else {
        PermissionRationale(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    }
}

/**
 * PermissionRationale screen explains why the camera permission is needed.
 *
 * This provides a better UX than just showing the system dialog:
 * - User understands why the permission is needed
 * - User can make an informed decision
 * - Follows Google's permission guidelines
 */
@Composable
fun PermissionRationale(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Text(
                text = "GuideCam needs access to your camera to provide the photo framing guide feature. " +
                        "Your camera feed is only used for preview and capture, and is never uploaded or shared.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Button(onClick = onRequestPermission) {
                Text("Grant Camera Permission")
            }
        }
    }
}
