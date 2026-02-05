package com.example.guidecam.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Rational
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executor

/**
 * CameraController manages CameraX setup and lifecycle.
 *
 * KEY ARCHITECTURAL DECISIONS:
 *
 * 1. UseCaseGroup with shared ViewPort:
 *    - Preview and ImageCapture MUST share the same ViewPort
 *    - This ensures that what you see in preview is EXACTLY what gets captured
 *    - Without this, CameraX might crop preview and capture differently
 *
 * 2. Explicit AspectRatio (4:3):
 *    - Camera sensors are typically 4:3 aspect ratio
 *    - Using 4:3 avoids unnecessary cropping from the sensor output
 *    - This gives maximum field of view and consistent framing
 *
 * 3. FIT_CENTER ScaleType:
 *    - Shows the entire camera frame without cropping
 *    - May show letterboxing/pillarboxing on screens with different aspect ratios
 *    - Guarantees overlay coordinates map 1:1 to captured image coordinates
 *
 * 4. Rotation handling:
 *    - We lock to portrait mode in the manifest for simplicity
 *    - Target rotation is set to match Surface.ROTATION_0 (portrait)
 *    - This avoids complex rotation math and keeps overlays consistent
 *
 * COMMON BEGINNER MISTAKES THIS CODE AVOIDS:
 *
 * - Mistake: Using different aspect ratios for Preview vs ImageCapture
 *   Solution: Both use the same ViewPort in a UseCaseGroup
 *
 * - Mistake: Using FILL_CENTER which crops the preview
 *   Solution: Using FIT_CENTER to show complete frame
 *
 * - Mistake: Not setting explicit target rotation
 *   Solution: Setting rotation to Surface.ROTATION_0 for portrait
 *
 * - Mistake: Relying on CameraX defaults for aspect ratio
 *   Solution: Explicitly setting RATIO_4_3 on both use cases
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    companion object {
        private const val TAG = "CameraController"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"

        /**
         * We use 4:3 aspect ratio because:
         * 1. Most camera sensors natively output 4:3
         * 2. Using the native ratio avoids cropping sensor output
         * 3. Consistent aspect ratio between preview and capture
         */
        const val TARGET_ASPECT_RATIO = AspectRatio.RATIO_4_3
    }

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Binds camera use cases to the lifecycle.
     *
     * @param previewView The PreviewView to display the camera preview
     * @param onCameraReady Callback when camera is ready (provides actual preview dimensions)
     *
     * IMPORTANT: PreviewView.ScaleType.FIT_CENTER is set here to ensure:
     * - No cropping of the preview
     * - Overlay coordinates map directly to captured image coordinates
     * - Letterboxing may appear on screens with different aspect ratios
     */
    fun bindCamera(
        previewView: PreviewView,
        onCameraReady: (previewWidth: Int, previewHeight: Int, aspectRatio: Float) -> Unit = { _, _, _ -> }
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            // FIT_CENTER ensures:
            // 1. The entire camera frame is visible (no cropping)
            // 2. Aspect ratio is preserved
            // 3. May show letterboxing on non-matching screen ratios
            // This is CRITICAL for overlay alignment - FILL_CENTER would crop the preview
            previewView.scaleType = PreviewView.ScaleType.FIT_CENTER

            // Build Preview use case with explicit rotation and aspect ratio
            val preview = Preview.Builder()
                .setTargetAspectRatio(TARGET_ASPECT_RATIO)
                .setTargetRotation(Surface.ROTATION_0) // Portrait mode
                .build()
                .also {
                    // Connect preview to the PreviewView
                    it.surfaceProvider = previewView.surfaceProvider
                }

            // Build ImageCapture use case with matching settings
            // MUST match Preview settings for consistent framing
            imageCapture = ImageCapture.Builder()
                .setTargetAspectRatio(TARGET_ASPECT_RATIO)
                .setTargetRotation(Surface.ROTATION_0) // Portrait mode
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Select back camera as default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            /**
             * ViewPort Definition:
             * - Defines the visible area that's shared between Preview and ImageCapture
             * - Uses the same aspect ratio as our use cases
             * - Ensures what you see in preview = what gets captured
             *
             * Without ViewPort in a UseCaseGroup:
             * - Preview and ImageCapture could be cropped differently
             * - Overlay coordinates would NOT match between preview and captured image
             */
            val viewPort = ViewPort.Builder(
                Rational(3, 4), // 4:3 in portrait = 3:4 width:height ratio
                Surface.ROTATION_0
            ).build()

            /**
             * UseCaseGroup bundles Preview and ImageCapture together:
             * - They share the same ViewPort
             * - Guarantees identical cropping behavior
             * - This is the KEY to overlay alignment between preview and capture
             */
            val useCaseGroup = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(imageCapture!!)
                .setViewPort(viewPort)
                .build()

            try {
                // Unbind all use cases before rebinding
                provider.unbindAll()

                // Bind use cases to camera lifecycle
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroup
                )

                // Notify that camera is ready with the aspect ratio information
                // The 4:3 aspect ratio in portrait means width/height = 0.75
                val aspectRatio = 3f / 4f // Width/Height for 4:3 in portrait
                val previewWidth = previewView.width
                val previewHeight = previewView.height
                onCameraReady(previewWidth, previewHeight, aspectRatio)

                Log.d(TAG, "Camera bound successfully with 4:3 aspect ratio")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Captures an image and saves it to MediaStore.
     *
     * @param onImageSaved Callback when image is saved successfully (provides URI as string)
     * @param onError Callback when capture fails
     *
     * Images are saved to Pictures/GuideCam directory with timestamp names.
     * The captured image will have the EXACT same framing as the preview
     * because they share the same ViewPort.
     */
    fun captureImage(
        onImageSaved: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val capture = imageCapture ?: run {
            onError("ImageCapture not initialized")
            return
        }

        // Create timestamped filename
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        // Setup MediaStore entry for saving the image
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            // For Android Q and above, specify the relative path
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GuideCam")
            }
        }

        // Configure output options for MediaStore
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // Take the picture
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri?.toString() ?: "Unknown"
                    Log.d(TAG, "Image saved: $savedUri")
                    onImageSaved(savedUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Image capture failed", exception)
                    onError(exception.message ?: "Unknown error")
                }
            }
        )
    }

    /**
     * Releases camera resources.
     * Should be called when the camera is no longer needed.
     */
    fun release() {
        cameraProvider?.unbindAll()
        imageCapture = null
        cameraProvider = null
    }
}
