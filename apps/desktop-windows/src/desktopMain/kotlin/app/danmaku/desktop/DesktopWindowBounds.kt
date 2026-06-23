package app.danmaku.desktop

import java.awt.GraphicsConfiguration
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window as AwtWindow
import kotlin.math.floor

internal data class DesktopWindowRestoreBounds(
    val bounds: Rectangle,
    val scaleX: Double,
    val scaleY: Double,
)

internal fun AwtWindow.captureFloatingWindowRestoreBounds(): DesktopWindowRestoreBounds {
    val configuration = graphicsConfiguration
    val transform = configuration?.defaultTransform
    return DesktopWindowRestoreBounds(
        bounds = Rectangle(bounds),
        scaleX = transform?.scaleX.validScaleOrDefault(),
        scaleY = transform?.scaleY.validScaleOrDefault(),
    )
}

internal fun AwtWindow.floatingRestoreBounds(restoreBounds: DesktopWindowRestoreBounds): Rectangle {
    val configuration = graphicsConfiguration ?: return Rectangle(restoreBounds.bounds)
    val transform = configuration.defaultTransform
    val scaleX = transform.scaleX.validScaleOrDefault()
    val scaleY = transform.scaleY.validScaleOrDefault()
    return restoreBounds.bounds
        .scaledForRestoreCoordinateSpace(scaleX = scaleX, scaleY = scaleY)
        .clampedToScreenAvailableBounds(
            screenAvailableBounds(configuration).scaledForRestoreCoordinateSpace(scaleX = scaleX, scaleY = scaleY),
        )
}

internal fun AwtWindow.windowScaleDescription(): String {
    val transform = graphicsConfiguration?.defaultTransform
    val toolkitScale = runCatching {
        Toolkit.getDefaultToolkit().screenResolution / DEFAULT_SCREEN_DPI
    }.getOrDefault(1.0)
    return "awtScale=${transform?.scaleX.validScaleOrDefault()}x${transform?.scaleY.validScaleOrDefault()} toolkitScale=$toolkitScale"
}

internal fun DesktopWindowRestoreBounds.describe(): String =
    "bounds=${bounds.x},${bounds.y} ${bounds.width}x${bounds.height} scale=${scaleX}x$scaleY"

internal fun Rectangle.clampedToScreenAvailableBounds(availableBounds: Rectangle?): Rectangle {
    val restoreWidth = width.coerceAtLeast(1)
    val restoreHeight = height.coerceAtLeast(1)
    if (availableBounds == null || availableBounds.width <= 0 || availableBounds.height <= 0) {
        return Rectangle(x, y, restoreWidth, restoreHeight)
    }
    val clampedWidth = restoreWidth.coerceAtMost(availableBounds.width)
    val clampedHeight = restoreHeight.coerceAtMost(availableBounds.height)
    val maxX = availableBounds.x + availableBounds.width - clampedWidth
    val maxY = availableBounds.y + availableBounds.height - clampedHeight
    return Rectangle(
        x.coerceIn(availableBounds.x, maxX),
        y.coerceIn(availableBounds.y, maxY),
        clampedWidth,
        clampedHeight,
    )
}

internal fun Rectangle.scaledForRestoreCoordinateSpace(scaleX: Double, scaleY: Double): Rectangle =
    Rectangle(
        floor(x * scaleX).toInt(),
        floor(y * scaleY).toInt(),
        floor(width * scaleX).toInt().coerceAtLeast(1),
        floor(height * scaleY).toInt().coerceAtLeast(1),
    )

private fun AwtWindow.screenAvailableBounds(configuration: GraphicsConfiguration): Rectangle {
    val screenBounds = Rectangle(configuration.bounds)
    val insets = runCatching {
        Toolkit.getDefaultToolkit().getScreenInsets(configuration)
    }.getOrNull() ?: return screenBounds
    return Rectangle(
        screenBounds.x + insets.left,
        screenBounds.y + insets.top,
        (screenBounds.width - insets.left - insets.right).coerceAtLeast(1),
        (screenBounds.height - insets.top - insets.bottom).coerceAtLeast(1),
    )
}

private fun Double?.validScaleOrDefault(): Double =
    this?.takeIf { it > 0.0 } ?: 1.0

private const val DEFAULT_SCREEN_DPI = 96.0
