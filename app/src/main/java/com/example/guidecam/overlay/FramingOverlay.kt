package com.example.guidecam.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * FramingOverlay is a Composable that draws semi-transparent overlay guides
 * on top of the camera preview.
 *
 * DESIGN DECISIONS:
 *
 * 1. Uses Compose Canvas for drawing:
 *    - Native Compose approach (no Android Canvas/View interop for overlay)
 *    - Efficient redraws when parameters change
 *    - Clean integration with Compose layout system
 *
 * 2. Normalized coordinates for overlay definition:
 *    - Overlays are defined in 0-1 range
 *    - Mapped to actual pixels using FramingMath
 *    - Resolution-independent and device-agnostic
 *
 * 3. Semi-transparent colors:
 *    - Guides are visible but don't completely obscure the preview
 *    - White color with alpha for visibility on various backgrounds
 *
 * WHY THIS OVERLAY ALIGNS WITH CAPTURED IMAGES:
 *
 * The overlay uses the same coordinate system as the camera:
 * - Both Preview and ImageCapture share a ViewPort in CameraController
 * - FramingMath calculates exact pixel coordinates based on the camera bounds
 * - FIT_CENTER scale type means no cropping occurs
 *
 * Result: A subject at normalized coordinate (0.5, 0.5) in the preview
 * will be at the exact same relative position in the captured image.
 *
 * @param modifier Modifier for the Canvas
 * @param cameraAspectRatio The camera's aspect ratio (width/height). Default 0.75 for 4:3 portrait.
 * @param showGrid Whether to show rule-of-thirds grid lines
 * @param showCenterRect Whether to show the center framing rectangle
 * @param centerRectNormalized The normalized rectangle for the center guide (0-1 coordinates)
 * @param overlayColor Color for the overlay elements
 * @param strokeWidth Width of the stroke in pixels
 */
@Composable
fun FramingOverlay(
    modifier: Modifier = Modifier,
    cameraAspectRatio: Float = 3f / 4f, // 4:3 in portrait mode
    showGrid: Boolean = true,
    showCenterRect: Boolean = true,
    centerRectNormalized: Rect = FramingMath.createCenteredRect(0.8f, 0.6f),
    overlayColor: Color = Color.White.copy(alpha = 0.7f),
    strokeWidth: Float = 2f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Calculate where the actual camera image is displayed within this view
        // This is crucial for proper coordinate mapping
        val cameraBounds = FramingMath.calculateCameraBounds(
            viewWidth = size.width,
            viewHeight = size.height,
            cameraAspectRatio = cameraAspectRatio
        )

        // Draw the center framing rectangle
        if (showCenterRect) {
            drawCenterRect(
                normalizedRect = centerRectNormalized,
                cameraBounds = cameraBounds,
                color = overlayColor,
                strokeWidth = strokeWidth
            )
        }

        // Draw rule-of-thirds grid lines
        if (showGrid) {
            drawGridLines(
                cameraBounds = cameraBounds,
                color = overlayColor.copy(alpha = 0.4f), // Lighter than the main rect
                strokeWidth = strokeWidth * 0.5f // Thinner than the main rect
            )
        }

        // Draw corner brackets for the center rectangle
        if (showCenterRect) {
            drawCornerBrackets(
                normalizedRect = centerRectNormalized,
                cameraBounds = cameraBounds,
                color = overlayColor,
                strokeWidth = strokeWidth * 1.5f, // Slightly thicker for emphasis
                bracketLength = 0.05f // 5% of camera dimension
            )
        }
    }
}

/**
 * Draws the center framing rectangle.
 *
 * The rectangle is drawn as a stroke (outline only) to not obscure the preview.
 */
private fun DrawScope.drawCenterRect(
    normalizedRect: Rect,
    cameraBounds: FramingMath.CameraBounds,
    color: Color,
    strokeWidth: Float
) {
    val pixelRect = FramingMath.normalizedRectToPixel(normalizedRect, cameraBounds)

    drawRect(
        color = color,
        topLeft = Offset(pixelRect.left, pixelRect.top),
        size = androidx.compose.ui.geometry.Size(pixelRect.width, pixelRect.height),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * Draws rule-of-thirds grid lines.
 *
 * Rule of thirds divides the frame into 9 equal parts with 2 vertical and 2 horizontal lines.
 * This is a common composition guide in photography.
 */
private fun DrawScope.drawGridLines(
    cameraBounds: FramingMath.CameraBounds,
    color: Color,
    strokeWidth: Float
) {
    val (verticalLines, horizontalLines) = FramingMath.getRuleOfThirdsLines()

    // Draw vertical lines
    verticalLines.forEach { normalizedX ->
        val startPoint = FramingMath.normalizedToPixel(normalizedX, 0f, cameraBounds)
        val endPoint = FramingMath.normalizedToPixel(normalizedX, 1f, cameraBounds)

        drawLine(
            color = color,
            start = startPoint,
            end = endPoint,
            strokeWidth = strokeWidth
        )
    }

    // Draw horizontal lines
    horizontalLines.forEach { normalizedY ->
        val startPoint = FramingMath.normalizedToPixel(0f, normalizedY, cameraBounds)
        val endPoint = FramingMath.normalizedToPixel(1f, normalizedY, cameraBounds)

        drawLine(
            color = color,
            start = startPoint,
            end = endPoint,
            strokeWidth = strokeWidth
        )
    }
}

/**
 * Draws corner brackets at the corners of the center rectangle.
 *
 * Corner brackets make it easier to align subjects with the framing guide
 * while keeping the center area unobstructed.
 *
 * @param bracketLength Length of each bracket arm as a fraction of camera dimension
 */
private fun DrawScope.drawCornerBrackets(
    normalizedRect: Rect,
    cameraBounds: FramingMath.CameraBounds,
    color: Color,
    strokeWidth: Float,
    bracketLength: Float
) {
    val pixelRect = FramingMath.normalizedRectToPixel(normalizedRect, cameraBounds)
    val bracketSize = FramingMath.normalizedSizeToPixel(bracketLength, bracketLength, cameraBounds)
    val bracketLengthPx = minOf(bracketSize.width, bracketSize.height)

    // Top-left corner
    drawCornerBracket(
        cornerX = pixelRect.left,
        cornerY = pixelRect.top,
        bracketLength = bracketLengthPx,
        horizontalDirection = 1f,  // Extends right
        verticalDirection = 1f,    // Extends down
        color = color,
        strokeWidth = strokeWidth
    )

    // Top-right corner
    drawCornerBracket(
        cornerX = pixelRect.right,
        cornerY = pixelRect.top,
        bracketLength = bracketLengthPx,
        horizontalDirection = -1f, // Extends left
        verticalDirection = 1f,    // Extends down
        color = color,
        strokeWidth = strokeWidth
    )

    // Bottom-left corner
    drawCornerBracket(
        cornerX = pixelRect.left,
        cornerY = pixelRect.bottom,
        bracketLength = bracketLengthPx,
        horizontalDirection = 1f,  // Extends right
        verticalDirection = -1f,   // Extends up
        color = color,
        strokeWidth = strokeWidth
    )

    // Bottom-right corner
    drawCornerBracket(
        cornerX = pixelRect.right,
        cornerY = pixelRect.bottom,
        bracketLength = bracketLengthPx,
        horizontalDirection = -1f, // Extends left
        verticalDirection = -1f,   // Extends up
        color = color,
        strokeWidth = strokeWidth
    )
}

/**
 * Draws a single corner bracket (L-shaped).
 */
private fun DrawScope.drawCornerBracket(
    cornerX: Float,
    cornerY: Float,
    bracketLength: Float,
    horizontalDirection: Float,
    verticalDirection: Float,
    color: Color,
    strokeWidth: Float
) {
    val corner = Offset(cornerX, cornerY)

    // Horizontal arm
    drawLine(
        color = color,
        start = corner,
        end = Offset(cornerX + bracketLength * horizontalDirection, cornerY),
        strokeWidth = strokeWidth
    )

    // Vertical arm
    drawLine(
        color = color,
        start = corner,
        end = Offset(cornerX, cornerY + bracketLength * verticalDirection),
        strokeWidth = strokeWidth
    )
}
