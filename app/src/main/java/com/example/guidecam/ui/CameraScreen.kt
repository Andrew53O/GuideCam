package com.example.guidecam.ui

import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.guidecam.camera.CameraController
import com.example.guidecam.overlay.FramingOverlay

/**
 * CameraScreen is the main camera UI composable.
 *
 * ARCHITECTURE:
 *
 * This composable integrates three key components:
 * 1. PreviewView (via AndroidView) - displays the camera feed
 * 2. FramingOverlay - draws composition guides on top
 * 3. Capture button - triggers image capture
 *
 * LAYOUT STRUCTURE:
 * - Box with fillMaxSize contains all elements
 * - PreviewView fills the entire space
 * - FramingOverlay is layered on top, also filling the space
 * - Capture button is positioned at the bottom center
 *
 * WHY AndroidView FOR PreviewView:
 *
 * PreviewView is an Android View, not a Composable. We use AndroidView
 * to bridge between the View world and Compose world. This is the
 * recommended approach by Google for CameraX with Compose.
 *
 * WHY NOT CameraX's CameraController (the library class):
 *
 * While CameraX provides a CameraController class for simplified camera setup,
 * we use ProcessCameraProvider directly because:
 * 1. We need explicit control over UseCaseGroup and ViewPort
 * 2. UseCaseGroup is required to ensure Preview and ImageCapture share coordinates
 * 3. Direct control is needed for precise aspect ratio management
 *
 * OVERLAY ALIGNMENT:
 *
 * The FramingOverlay uses the same coordinate system as the camera:
 * - CameraController sets up Preview and ImageCapture with shared ViewPort
 * - FramingOverlay calculates camera bounds within the PreviewView
 * - All coordinates are normalized (0-1) and mapped to pixels
 *
 * Result: The overlay guides appear at identical positions in both
 * the preview and the captured image.
 */
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Remember the CameraController instance
    // This persists across recompositions but is cleaned up when the composable leaves
    val cameraController = remember {
        CameraController(context, lifecycleOwner)
    }

    // State for capture feedback
    var isCapturing by remember { mutableStateOf(false) }

    // Clean up camera resources when this composable is disposed
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraController.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black) // Black background for letterboxing areas
    ) {
        // Camera Preview using AndroidView
        // This is the standard way to embed PreviewView in Compose
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Note: scaleType is set in CameraController.bindCamera()
                    // We set it there to ensure it's applied before binding use cases
                }
            },
            update = { previewView ->
                // Bind camera when the PreviewView is ready
                cameraController.bindCamera(previewView)
            }
        )

        // Framing Overlay drawn on top of the camera preview
        // This uses the same coordinate system as the camera
        FramingOverlay(
            modifier = Modifier.fillMaxSize(),
            cameraAspectRatio = 3f / 4f, // 4:3 in portrait = 3:4 width:height
            showGrid = true,
            showCenterRect = true
        )

        // Capture Button at the bottom center
        CaptureButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
            isCapturing = isCapturing,
            onClick = {
                if (!isCapturing) {
                    isCapturing = true
                    cameraController.captureImage(
                        onImageSaved = { uri ->
                            isCapturing = false
                            // Show a more informative toast with the save location
                            Toast.makeText(
                                context,
                                "Image saved to Pictures/GuideCam",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onError = { error ->
                            isCapturing = false
                            Toast.makeText(
                                context,
                                "Capture failed: $error",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        )
    }
}

/**
 * CaptureButton is the shutter button for taking photos.
 *
 * Design:
 * - Large circular button (72dp) for easy tapping
 * - White/gray color scheme similar to camera apps
 * - Shows visual feedback when capturing
 */
@Composable
private fun CaptureButton(
    modifier: Modifier = Modifier,
    isCapturing: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(72.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCapturing) Color.Gray else Color.White,
            contentColor = Color.DarkGray
        ),
        enabled = !isCapturing
    ) {
        // Inner circle for the classic camera button look
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = if (isCapturing) Color.LightGray else Color.White,
                    shape = CircleShape
                )
        )
    }
}
