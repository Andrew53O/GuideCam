package com.example.guidecam.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size

/**
 * FramingMath handles coordinate transformations between:
 * - Normalized coordinates (0.0 to 1.0) for overlay definitions
 * - Pixel coordinates for rendering on the actual screen
 *
 * COORDINATE SYSTEM EXPLANATION:
 *
 * Normalized Coordinates (0.0 - 1.0):
 * - Origin (0, 0) is at TOP-LEFT of the visible camera area
 * - (1, 1) is at BOTTOM-RIGHT of the visible camera area
 * - Overlay elements are defined in these coordinates
 * - This makes overlays resolution-independent and device-agnostic
 *
 * Example:
 * - A rectangle at normalized (0.1, 0.1) with size (0.8, 0.8)
 * - Occupies 80% of width and height, with 10% margin on all sides
 * - Will look identical on any screen/camera resolution
 *
 * Pixel Coordinates:
 * - Actual screen pixels where drawing occurs
 * - Depends on the PreviewView dimensions and aspect ratio
 *
 * WHY FIT_CENTER MATTERS FOR COORDINATE MAPPING:
 *
 * When using FIT_CENTER with a 4:3 aspect ratio camera on a different screen ratio:
 * - The camera preview is letterboxed (black bars on sides or top/bottom)
 * - The "visible camera area" is smaller than the PreviewView dimensions
 * - We need to calculate where the actual camera image is within the view
 *
 * For a 4:3 camera in portrait mode:
 * - Camera aspect ratio = 3/4 = 0.75 (width/height)
 * - If screen is taller than 4:3, letterboxing appears on top/bottom
 * - If screen is wider than 3:4, pillarboxing appears on left/right
 *
 * This class calculates the exact camera bounds within the PreviewView
 * and maps normalized coordinates to those bounds.
 */
object FramingMath {

    /**
     * Data class representing the visible camera area within the PreviewView.
     *
     * @param offset Top-left corner of the camera area in pixels
     * @param size Dimensions of the camera area in pixels
     */
    data class CameraBounds(
        val offset: Offset,
        val size: Size
    ) {
        /**
         * The rectangle representing the camera bounds.
         */
        val rect: Rect
            get() = Rect(offset, size)
    }

    /**
     * Calculates where the camera preview is actually displayed within the PreviewView.
     *
     * When using FIT_CENTER, the camera image is scaled to fit entirely within
     * the view while maintaining aspect ratio. This may result in letterboxing.
     *
     * @param viewWidth Width of the PreviewView in pixels
     * @param viewHeight Height of the PreviewView in pixels
     * @param cameraAspectRatio The camera's aspect ratio (width/height)
     *                          For 4:3 in portrait, this is 0.75 (3/4)
     *
     * @return CameraBounds indicating where the camera image is displayed
     *
     * CALCULATION LOGIC:
     *
     * 1. Compare view's aspect ratio with camera's aspect ratio
     * 2. Scale camera to fit within view (preserving ratio)
     * 3. Center the scaled camera within the view
     * 4. Return the offset and size of the visible camera area
     */
    fun calculateCameraBounds(
        viewWidth: Float,
        viewHeight: Float,
        cameraAspectRatio: Float
    ): CameraBounds {
        val viewAspectRatio = viewWidth / viewHeight

        return if (viewAspectRatio > cameraAspectRatio) {
            // View is WIDER than camera
            // Camera will be pillarboxed (black bars on left and right)
            // Scale camera to fill height, calculate width
            val cameraHeight = viewHeight
            val cameraWidth = viewHeight * cameraAspectRatio
            val offsetX = (viewWidth - cameraWidth) / 2f
            val offsetY = 0f

            CameraBounds(
                offset = Offset(offsetX, offsetY),
                size = Size(cameraWidth, cameraHeight)
            )
        } else {
            // View is TALLER than camera (or equal)
            // Camera will be letterboxed (black bars on top and bottom)
            // Scale camera to fill width, calculate height
            val cameraWidth = viewWidth
            val cameraHeight = viewWidth / cameraAspectRatio
            val offsetX = 0f
            val offsetY = (viewHeight - cameraHeight) / 2f

            CameraBounds(
                offset = Offset(offsetX, offsetY),
                size = Size(cameraWidth, cameraHeight)
            )
        }
    }

    /**
     * Converts a normalized coordinate (0-1) to a pixel coordinate within the camera bounds.
     *
     * @param normalizedX X coordinate in normalized space (0 = left edge, 1 = right edge)
     * @param normalizedY Y coordinate in normalized space (0 = top edge, 1 = bottom edge)
     * @param cameraBounds The calculated camera bounds within the view
     *
     * @return Offset in pixel coordinates ready for drawing
     *
     * FORMULA:
     * pixelX = cameraBounds.offset.x + (normalizedX * cameraBounds.size.width)
     * pixelY = cameraBounds.offset.y + (normalizedY * cameraBounds.size.height)
     */
    fun normalizedToPixel(
        normalizedX: Float,
        normalizedY: Float,
        cameraBounds: CameraBounds
    ): Offset {
        return Offset(
            x = cameraBounds.offset.x + (normalizedX * cameraBounds.size.width),
            y = cameraBounds.offset.y + (normalizedY * cameraBounds.size.height)
        )
    }

    /**
     * Converts normalized rectangle coordinates to pixel rectangle.
     *
     * @param normalizedRect Rectangle in normalized coordinates (0-1 range)
     * @param cameraBounds The calculated camera bounds within the view
     *
     * @return Rect in pixel coordinates ready for drawing
     */
    fun normalizedRectToPixel(
        normalizedRect: Rect,
        cameraBounds: CameraBounds
    ): Rect {
        val topLeft = normalizedToPixel(normalizedRect.left, normalizedRect.top, cameraBounds)
        val bottomRight = normalizedToPixel(normalizedRect.right, normalizedRect.bottom, cameraBounds)
        return Rect(topLeft, bottomRight)
    }

    /**
     * Converts a normalized size to pixel size.
     *
     * @param normalizedWidth Width in normalized space (0-1)
     * @param normalizedHeight Height in normalized space (0-1)
     * @param cameraBounds The calculated camera bounds within the view
     *
     * @return Size in pixels
     */
    fun normalizedSizeToPixel(
        normalizedWidth: Float,
        normalizedHeight: Float,
        cameraBounds: CameraBounds
    ): Size {
        return Size(
            width = normalizedWidth * cameraBounds.size.width,
            height = normalizedHeight * cameraBounds.size.height
        )
    }

    /**
     * Creates a centered rectangle in normalized coordinates.
     *
     * This is a convenience function for creating overlay rectangles
     * that are centered within the camera view.
     *
     * @param widthPercent Width as a percentage (0.0 to 1.0) of the camera width
     * @param heightPercent Height as a percentage (0.0 to 1.0) of the camera height
     *
     * @return Rect in normalized coordinates (0-1 range)
     *
     * Example:
     * - createCenteredRect(0.8f, 0.8f) creates a rectangle that is
     *   80% of width and 80% of height, centered with 10% margins
     */
    fun createCenteredRect(widthPercent: Float, heightPercent: Float): Rect {
        val left = (1f - widthPercent) / 2f
        val top = (1f - heightPercent) / 2f
        val right = left + widthPercent
        val bottom = top + heightPercent
        return Rect(left, top, right, bottom)
    }

    /**
     * Generates normalized coordinates for grid lines (rule of thirds).
     *
     * @return Pair of lists: (vertical line X positions, horizontal line Y positions)
     *         All values are in normalized coordinates (0-1 range)
     */
    fun getRuleOfThirdsLines(): Pair<List<Float>, List<Float>> {
        val verticalLines = listOf(1f / 3f, 2f / 3f)
        val horizontalLines = listOf(1f / 3f, 2f / 3f)
        return Pair(verticalLines, horizontalLines)
    }
}
